package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.TemperatureZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ThermalMonitorState(
  val isRegistered: Boolean = false,
  val temperatureCelsius: Float = Float.NaN,
  val voltageMv: Int = -1,
  val zone: TemperatureZone = TemperatureZone.UNAVAILABLE,
  val activeTier: AdaptivePowerTier = AdaptivePowerTier.NORMAL,
  val isThermalThrottled: Boolean = false,
  val lastNotifiedZone: TemperatureZone? = null,
  val lastNotificationTitle: String? = null,
  val lastNotificationTimeMillis: Long = 0L,
  val alertCount: Int = 0,
)

/**
 * Event-driven thermal monitoring component that registers a BroadcastReceiver for
 * [Intent.ACTION_BATTERY_CHANGED] to detect high-temperature thresholds and trigger local
 * warning notifications within Netra's established [AdaptivePowerTier] rules.
 *
 * Zero polling, zero wake locks, and hysteresis-guarded notifications prevent alert storms
 * and keep background drain minimal.
 */
@Singleton
class ThermalMonitor @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val temperatureEngine: TemperatureControlEngine,
  private val adaptivePowerTierEngine: AdaptivePowerTierEngine,
  private val notificationHelper: NotificationHelper,
) {

  private val _state = MutableStateFlow(ThermalMonitorState())
  val state: StateFlow<ThermalMonitorState> = _state.asStateFlow()

  private var sensitivity: CoolingSensitivity = CoolingSensitivity.BALANCED
  private val activeOwners = mutableSetOf<String>()
  private var receiverRegistered = false
  private var lastAlertTimestampMillis = 0L
  private var lastAlertZone: TemperatureZone? = null

  private val batteryChangedReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
          processBatteryIntent(intent)
        }
      }
    }

  @Synchronized
  fun register(owner: String = DEFAULT_OWNER, coolingSensitivity: CoolingSensitivity = sensitivity) {
    sensitivity = coolingSensitivity
    activeOwners.add(owner)
    if (receiverRegistered) return

    notificationHelper.createChannels()
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val sticky =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(batteryChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context.registerReceiver(batteryChangedReceiver, filter)
      }
    receiverRegistered = true
    _state.update { it.copy(isRegistered = true) }

    if (sticky != null) {
      processBatteryIntent(sticky)
    }
  }

  @Synchronized
  fun unregister(owner: String = DEFAULT_OWNER) {
    activeOwners.remove(owner)
    if (activeOwners.isEmpty() && receiverRegistered) {
      runCatching { context.unregisterReceiver(batteryChangedReceiver) }
      receiverRegistered = false
      _state.update { it.copy(isRegistered = false) }
    }
  }

  fun updateSensitivity(newSensitivity: CoolingSensitivity) {
    sensitivity = newSensitivity
  }

  /**
   * Evaluates an incoming [Intent.ACTION_BATTERY_CHANGED] intent using [AdaptivePowerTierEngine]
   * and [TemperatureControlEngine], triggering local warning notifications when temperature
   * crosses high-heat thresholds permitted under the active power tier.
   */
  @Synchronized
  fun processBatteryIntent(intent: Intent) {
    val isAppForeground = activeOwners.contains(OWNER_FOREGROUND_UI)
    val telemetry = telemetryCollector.collect(
      intentOverride = intent,
      isAppInForeground = isAppForeground,
    )
    val now = telemetry.timestampMillis
    temperatureEngine.recordTemperature(now, telemetry.temperatureCelsius)

    val powerBudget = adaptivePowerTierEngine.evaluate(
      telemetry = telemetry,
      wakeupsThisHour = 0,
      snapshotsToday = 0,
    )
    val zone = temperatureEngine.classifyObserved(telemetry)

    // Hysteresis reset once battery cools comfortably below 38.0°C
    if (zone == TemperatureZone.UNAVAILABLE) {
      lastAlertZone = null
    }

    val tierAllowsAdvisoryAlerts =
      powerBudget.activeTier == AdaptivePowerTier.NORMAL ||
        powerBudget.activeTier == AdaptivePowerTier.SAVER

    // In CRITICAL (<15%) or SURVIVAL (<5%), only Tier-0 safety thresholds (>= VERY_HOT / CRITICAL / EMERGENCY) fire
    val meetsTierThreshold =
      when (powerBudget.activeTier) {
        AdaptivePowerTier.NORMAL, AdaptivePowerTier.SAVER -> zone.ordinal >= TemperatureZone.HOT.ordinal
        AdaptivePowerTier.CRITICAL -> zone.ordinal >= TemperatureZone.VERY_HOT.ordinal
        AdaptivePowerTier.SURVIVAL -> zone.ordinal >= TemperatureZone.CRITICAL.ordinal
      }

    val cooldownMillis = if (tierAllowsAdvisoryAlerts) 10 * 60_000L else 20 * 60_000L
    val escalatedZone = lastAlertZone == null || zone.ordinal > lastAlertZone!!.ordinal
    val cooldownElapsed = now - lastAlertTimestampMillis >= cooldownMillis

    var notifiedTitle = _state.value.lastNotificationTitle
    var notifiedTime = _state.value.lastNotificationTimeMillis
    var alerts = _state.value.alertCount

    if (meetsTierThreshold && (escalatedZone || cooldownElapsed)) {
      val formattedTemp = com.example.domain.model.telemetryText(telemetry.temperatureCelsius, "°C")
      when (zone) {
        TemperatureZone.EMERGENCY, TemperatureZone.CRITICAL -> {
          val title = "Critical Battery Heat ($formattedTemp)"
          val body = if (telemetry.isPlugged) {
            "Battery reached $formattedTemp (${powerBudget.activeTier.name} tier). Unplug charger and let device cool."
          } else {
            "Battery reached $formattedTemp (${powerBudget.activeTier.name} tier). Pause heavy apps and let device cool."
          }
          notificationHelper.postCriticalAlert(NotificationHelper.ID_THERMAL_ALERT, title, body)
          notifiedTitle = title
          notifiedTime = now
          alerts++
          lastAlertZone = zone
          lastAlertTimestampMillis = now
        }
        TemperatureZone.VERY_HOT, TemperatureZone.HOT -> {
          val title = "High Battery Temperature ($formattedTemp)"
          val body = "Thermal warning in ${powerBudget.activeTier.name} tier: battery at $formattedTemp. Android reported thermal stress. Netra can reduce its own work, not control CPU throttling."
          notificationHelper.postAdvisory(NotificationHelper.ID_THERMAL_ALERT, title, body)
          notifiedTitle = title
          notifiedTime = now
          alerts++
          lastAlertZone = zone
          lastAlertTimestampMillis = now
        }
        else -> Unit
      }
    }

    _state.update {
      it.copy(
        isRegistered = receiverRegistered,
        temperatureCelsius = telemetry.temperatureCelsius,
        voltageMv = telemetry.voltageMv,
        zone = zone,
        activeTier = powerBudget.activeTier,
        isThermalThrottled = powerBudget.isThermalThrottled,
        lastNotifiedZone = lastAlertZone,
        lastNotificationTitle = notifiedTitle,
        lastNotificationTimeMillis = notifiedTime,
        alertCount = alerts,
      )
    }
  }

  companion object {
    const val DEFAULT_OWNER = "default"
    const val OWNER_FOREGROUND_UI = "foreground_ui"
    const val OWNER_MONITORING_SERVICE = "monitoring_service"
  }
}
