package com.example.service

import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.AppPowerInspector
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.AutomationRulesEngine
import com.example.domain.engine.ChargingSpeedClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Event-driven foreground guardian service used ONLY when the user enables
 * 24/7 Live Guardian or Trip Mode. Uses zero polling loops; responds strictly
 * to OS battery and screen broadcasts.
 */
@AndroidEntryPoint
class BatteryGuardianService : Service() {

  @Inject lateinit var notificationHelper: NotificationHelper
  @Inject lateinit var telemetryCollector: BatteryTelemetryCollector
  @Inject lateinit var appPowerInspector: AppPowerInspector
  @Inject lateinit var rulesEngine: AutomationRulesEngine
  @Inject lateinit var chargingClassifier: ChargingSpeedClassifier
  @Inject lateinit var preferencesRepository: PreferencesRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var receiverRegistered = false

  private val systemEventsReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
          Intent.ACTION_SCREEN_OFF -> {
            NetraWorkScheduler.scheduleScreenOffDrainCheck(this@BatteryGuardianService)
            evaluateNow(intentOverride = null, forceSnapshot = true)
          }
          Intent.ACTION_SCREEN_ON -> {
            NetraWorkScheduler.cancelScreenOffDrainCheck(this@BatteryGuardianService)
            evaluateNow(intentOverride = null, forceSnapshot = false)
          }
          Intent.ACTION_BATTERY_CHANGED,
          Intent.ACTION_POWER_CONNECTED,
          Intent.ACTION_POWER_DISCONNECTED,
          Intent.ACTION_BATTERY_LOW,
          Intent.ACTION_BATTERY_OKAY -> {
            val batteryIntent = if (action == Intent.ACTION_BATTERY_CHANGED) intent else null
            evaluateNow(
              intentOverride = batteryIntent,
              forceSnapshot = action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED,
            )
          }
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    notificationHelper.createChannels()
    startInForegroundCompat("Netra Guardian Active", "Monitoring battery, charging & thermal safety locally.")
    registerSystemReceiver()
    evaluateNow(intentOverride = null, forceSnapshot = true)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    startInForegroundCompat("Netra Guardian Active", "Monitoring battery, charging & thermal safety locally.")
    evaluateNow(intentOverride = null, forceSnapshot = false)
    return START_STICKY
  }

  private fun startInForegroundCompat(title: String, body: String) {
    val notification = notificationHelper.buildGuardianForegroundNotification(title, body)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val hasLocPerm =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED ||
          ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
      val fgsType =
        if (hasLocPerm) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
      startForeground(NotificationHelper.ID_GUARDIAN_SERVICE, notification, fgsType)
    } else {
      startForeground(NotificationHelper.ID_GUARDIAN_SERVICE, notification)
    }
  }

  private fun registerSystemReceiver() {
    if (receiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_BATTERY_LOW)
        addAction(Intent.ACTION_BATTERY_OKAY)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(systemEventsReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(systemEventsReceiver, filter)
    }
    receiverRegistered = true
  }

  private fun evaluateNow(intentOverride: Intent?, forceSnapshot: Boolean) {
    serviceScope.launch {
      val prefs = preferencesRepository.preferences.first()
      val telemetry = telemetryCollector.collect(intentOverride, prefs.observedCapacityMah)
      val topApps = appPowerInspector.collectTopApps(limit = 5)
      val output = rulesEngine.evaluate(telemetry, prefs, topApps, forceSnapshot = forceSnapshot)
      val diag = chargingClassifier.diagnose(telemetry)
      val statusLine =
        if (telemetry.isPlugged) {
          "${batteryLevelText(telemetry.levelPercent)} • ${telemetryText(diag.netPowerWatts, " W")} • ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}"
        } else {
          "${batteryLevelText(telemetry.levelPercent)} • ${output.activeProfile.name} • ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}"
        }
      val notif = notificationHelper.buildGuardianForegroundNotification("Netra Guardian", statusLine)
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
      try {
        nm?.notify(NotificationHelper.ID_GUARDIAN_SERVICE, notif)
      } catch (_: SecurityException) {
        // Notifications may be denied/revoked; keep monitoring state local.
      }
    }
  }

  override fun onDestroy() {
    telemetryCollector.stopCoolingBrightness()
    if (receiverRegistered) {
      runCatching { unregisterReceiver(systemEventsReceiver) }
      receiverRegistered = false
    }
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_STOP = "com.example.action.STOP_GUARDIAN"

    fun start(context: Context) {
      val intent = Intent(context, BatteryGuardianService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, BatteryGuardianService::class.java).apply { action = ACTION_STOP }
      context.startService(intent)
    }
  }
}
