package com.example.data.local.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PlugType
import com.example.domain.model.ThermalLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BatteryTelemetryCollector @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val batteryManager: BatteryManager? =
    context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
  private val powerManager: PowerManager? =
    context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  private val connectivityManager: ConnectivityManager? =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  fun collect(
    intentOverride: Intent? = null,
    observedCapacityFallbackMah: Int = 4500,
  ): BatteryTelemetry {
    val intent =
      intentOverride
        ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
    val levelPercent =
      if (levelRaw >= 0) ((levelRaw * 100f) / scale).toInt().coerceIn(0, 100)
      else batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 50

    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
      status == BatteryManager.BATTERY_STATUS_FULL

    val pluggedRaw = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val isPlugged = pluggedRaw != 0
    val plugType =
      when (pluggedRaw) {
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
        else -> PlugType.NONE
      }

    val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
    val temperatureCelsius = tempTenths / 10f

    val voltageMv = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850)
      .let { if (it in 1..20) it * 1000 else it } // Guard devices reporting V instead of mV

    val rawCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    val rawAvgCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE
    val chosenRaw = if (rawCurrent != Int.MIN_VALUE && rawCurrent != 0) rawCurrent else rawAvgCurrent

    var currentMa =
      if (chosenRaw == Int.MIN_VALUE) {
        if (isCharging) 1100f else -320f
      } else {
        // Android spec is microamps; some OEMs return milliamps directly
        if (abs(chosenRaw) > 25_000) chosenRaw / 1000f else chosenRaw.toFloat()
      }

    // Normalize sign: positive when actively charging, negative when discharging
    if (isCharging && currentMa < -50f) {
      currentMa = abs(currentMa)
    } else if (!isPlugged && currentMa > 50f) {
      currentMa = -abs(currentMa)
    }

    val chargeCounterRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1L
    val chargeCounterUah = if (chargeCounterRaw > 0L) chargeCounterRaw else -1L
    val derivedCapacityMah =
      if (chargeCounterUah > 0L && levelPercent >= 15) {
        ((chargeCounterUah / 1000.0) / (levelPercent / 100.0)).toInt().coerceIn(1500, 12000)
      } else {
        observedCapacityFallbackMah
      }

    val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
      ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
    val healthState =
      when (healthInt) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealthState.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealthState.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealthState.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealthState.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealthState.UNSPECIFIED_FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealthState.COLD
        else -> BatteryHealthState.UNKNOWN
      }

    val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"

    val cycleCount =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) cycles else null
      } else {
        null
      }

    val thermalLevel =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
        when (powerManager.currentThermalStatus) {
          PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
          PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
          PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
          PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
          PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
          PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
          else -> ThermalLevel.NONE
        }
      } else {
        ThermalLevel.NONE
      }

    val screenOn = powerManager?.isInteractive ?: true
    val osBatterySaverOn = powerManager?.isPowerSaveMode ?: false
    val canWriteSettings = Settings.System.canWrite(context)
    val brightnessRaw =
      runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
      }.getOrDefault(128)
    val brightnessPercent = ((brightnessRaw / 255f) * 100f).toInt().coerceIn(0, 100)

    val hotspotLikelyActive = detectHotspotLikelyActive()
    val isMetered = connectivityManager?.isActiveNetworkMetered ?: false
    val poorCellularSignal = detectWeakCellularSignal()

    return BatteryTelemetry(
      timestampMillis = System.currentTimeMillis(),
      levelPercent = levelPercent,
      isCharging = isCharging,
      isPlugged = isPlugged,
      plugType = plugType,
      temperatureCelsius = temperatureCelsius,
      voltageMv = voltageMv,
      currentMa = currentMa,
      chargeCounterUah = chargeCounterUah,
      estimatedCapacityMah = derivedCapacityMah,
      healthState = healthState,
      technology = technology,
      thermalLevel = thermalLevel,
      osBatterySaverOn = osBatterySaverOn,
      screenOn = screenOn,
      screenBrightnessPercent = brightnessPercent,
      canWriteSettings = canWriteSettings,
      hotspotLikelyActive = hotspotLikelyActive,
      poorCellularSignal = poorCellularSignal,
      isNetworkMetered = isMetered,
      chargeCycleCount = cycleCount,
    )
  }

  private fun detectHotspotLikelyActive(): Boolean {
    return runCatching {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return false
      val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
      method.isAccessible = true
      method.invoke(wifiManager) as? Boolean ?: false
    }.getOrDefault(false)
  }

  private fun detectWeakCellularSignal(): Boolean {
    return runCatching {
      val cm = connectivityManager ?: return false
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val dbm = caps.signalStrength
        dbm != Int.MIN_VALUE && dbm < -108
      } else {
        false
      }
    }.getOrDefault(false)
  }

  fun tryLowerBrightnessForCooling(targetBrightness255: Int = 90): Boolean {
    if (!Settings.System.canWrite(context)) return false
    return runCatching {
      val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
      if (current > targetBrightness255) {
        Settings.System.putInt(
          context.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS_MODE,
          Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
          context.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS,
          targetBrightness255.coerceIn(30, 255),
        )
        true
      } else {
        false
      }
    }.getOrDefault(false)
  }
}
