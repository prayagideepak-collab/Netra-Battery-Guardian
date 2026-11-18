package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.AppPowerInspector
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.AutomationRulesEngine
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.engine.SelfHealingSupervisor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service that tracks battery state in the background using BatteryManager
 * and IntentFilter(Intent.ACTION_BATTERY_CHANGED), persisting hourly BatterySnapshot
 * metrics (voltage, temperature, charging status) into BatteryDatabase.
 */
@AndroidEntryPoint
class BatteryMonitoringService : Service() {

  @Inject lateinit var notificationHelper: NotificationHelper
  @Inject lateinit var appDatabase: AppDatabase
  @Inject lateinit var telemetryCollector: BatteryTelemetryCollector
  @Inject lateinit var appPowerInspector: AppPowerInspector
  @Inject lateinit var rulesEngine: AutomationRulesEngine
  @Inject lateinit var chargingClassifier: ChargingSpeedClassifier
  @Inject lateinit var adaptivePowerTierEngine: AdaptivePowerTierEngine
  @Inject lateinit var selfHealingSupervisor: SelfHealingSupervisor
  @Inject lateinit var preferencesRepository: PreferencesRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var receiverRegistered = false
  private var batteryManager: BatteryManager? = null

  private val batteryChangedReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
          handleBatteryChanged(intent)
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    notificationHelper.createChannels()
    startForegroundCompat(
      title = "Netra Battery Monitor",
      body = "Tracking voltage, temperature, and charging status locally.",
    )
    registerBatteryChangedReceiver()
    // Capture initial sticky broadcast immediately on startup
    val stickyIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    handleBatteryChanged(stickyIntent)
    serviceScope.launch {
      selfHealingSupervisor.runNaturalHealthCheck(triggerSource = "battery_monitoring_service_start")
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    startForegroundCompat(
      title = "Netra Battery Monitor",
      body = "Tracking voltage, temperature, and charging status locally.",
    )
    val stickyIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    handleBatteryChanged(stickyIntent)
    return START_STICKY
  }

  private fun registerBatteryChangedReceiver() {
    if (receiverRegistered) return
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(batteryChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(batteryChangedReceiver, filter)
    }
    receiverRegistered = true
  }

  private fun handleBatteryChanged(intent: Intent?) {
    serviceScope.launch {
      val prefs = preferencesRepository.preferences.first()
      val telemetry = telemetryCollector.collect(
        intentOverride = intent,
        observedCapacityFallbackMah = prefs.observedCapacityMah,
        isAppInForeground = false,
      )
      val tierMetrics = adaptivePowerTierEngine.evaluate(telemetry, wakeupsThisHour = 0, snapshotsToday = 0)
      val topApps = if (appPowerInspector.hasUsageStatsPermission()) appPowerInspector.collectTopApps(5) else emptyList()
      val eval = rulesEngine.evaluate(
        telemetry = telemetry,
        prefs = prefs,
        topApps = topApps,
        forceSnapshot = false,
        dbBatchIntervalSeconds = tierMetrics.dbBatchIntervalSeconds,
      )
      val diag = chargingClassifier.diagnose(telemetry)

      val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        ?: BatteryManager.BATTERY_STATUS_UNKNOWN
      val chargingStatusLabel =
        when (statusCode) {
          BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
          BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
          BatteryManager.BATTERY_STATUS_FULL -> "FULL"
          BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
          else -> "UNKNOWN"
        }

      val batteryDao = appDatabase.batteryDao()
      val lastHourly = batteryDao.getLatestHourlySnapshot()
      val now = telemetry.timestampMillis
      val oneHourMillis = 3_600_000L

      // Persist an explicit hourly BatterySnapshot checkpoint when >= 1 hour has elapsed
      if (lastHourly == null || now - lastHourly.timestampMillis >= oneHourMillis) {
        val currentPropMa =
          batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { if (kotlin.math.abs(it) > 25_000) it / 1000f else it.toFloat() }
            ?: telemetry.currentMa

        batteryDao.insertSnapshot(
          BatterySnapshot(
            timestampMillis = now,
            levelPercent = telemetry.levelPercent,
            voltageMv = telemetry.voltageMv,
            temperatureCelsius = telemetry.temperatureCelsius,
            currentMa = currentPropMa,
            powerWatts = diag.netPowerWatts,
            isCharging = telemetry.isCharging,
            chargingStatus = chargingStatusLabel,
            isPlugged = telemetry.isPlugged,
            plugType = telemetry.plugType.name,
            screenOn = telemetry.screenOn,
            thermalLevel = telemetry.thermalLevel.name,
            activeProfile = eval.activeProfile.name,
            isHourlyCheckpoint = true,
          )
        )
      }

      val statusSummary =
        "${telemetry.levelPercent}% • ${String.format("%.1f", telemetry.temperatureCelsius)}°C • ${
          String.format("%.2f", telemetry.voltageVolts)
        }V • $chargingStatusLabel"
      val notification = notificationHelper.buildGuardianForegroundNotification("Netra Battery Monitor", statusSummary)
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.notify(NotificationHelper.ID_GUARDIAN_SERVICE, notification)
    }
  }

  private fun startForegroundCompat(title: String, body: String) {
    val notification = notificationHelper.buildGuardianForegroundNotification(title, body)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NotificationHelper.ID_GUARDIAN_SERVICE,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NotificationHelper.ID_GUARDIAN_SERVICE, notification)
    }
  }

  override fun onDestroy() {
    if (receiverRegistered) {
      runCatching { unregisterReceiver(batteryChangedReceiver) }
      receiverRegistered = false
    }
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_STOP = "com.example.action.STOP_BATTERY_MONITORING"

    fun start(context: Context) {
      val intent = Intent(context, BatteryMonitoringService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, BatteryMonitoringService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
