package com.example.data.local.system

import android.app.usage.UsageStatsManager
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
import com.example.domain.model.AppStandbyBucket
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.NetworkType
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
    observedCapacityFallbackMah: Int = -1,
    isAppInForeground: Boolean = true,
  ): BatteryTelemetry {
    val intent =
      intentOverride
        ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val reportedLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val levelPercent = if (scale > 0 && levelRaw in 0..scale) ((levelRaw * 100f) / scale).toInt()
      else reportedLevel?.takeIf { it in 0..100 } ?: -1

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

    val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val temperatureCelsius = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else Float.NaN
    // Android documents millivolts and microamps. Never infer another unit by magnitude.
    val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 } ?: -1
    val rawCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    val rawAvgCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE
    val chosenRaw = if (rawCurrent != Int.MIN_VALUE) rawCurrent else rawAvgCurrent
    val currentMa = if (chosenRaw == Int.MIN_VALUE) Float.NaN else chosenRaw / 1000f

    val chargeCounterRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1L
    val chargeCounterUah = if (chargeCounterRaw > 0L) chargeCounterRaw else -1L
    val derivedCapacityMah =
      if (chargeCounterUah > 0L && levelPercent in 15..100) {
        ((chargeCounterUah / 1000.0) / (levelPercent / 100.0)).toInt()
      } else {
        -1 // Legacy cached capacities lack measurement provenance. Do not reuse them.
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

    val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Unavailable"

    val cycleCount =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) cycles else null
      } else {
        null
      }

    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { powerManager?.currentThermalStatus }.getOrNull() else null
    val thermalLevel =
      if (thermalStatus != null) {
        when (thermalStatus) {
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
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
      }.getOrDefault(-1)
    val brightnessPercent = if (brightnessRaw in 0..255) ((brightnessRaw / 255f) * 100f).toInt() else -1

    val hotspotLikelyActive = detectHotspotLikelyActive()
    val isMetered = connectivityManager?.isActiveNetworkMetered ?: false
    val poorCellularSignal = detectWeakCellularSignal()
    val networkType = detectNetworkType()
    val standbyBucket = detectStandbyBucket()

    return BatteryTelemetry(
      timestampMillis = System.currentTimeMillis(),
      batteryStatus = status,
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
      thermalStatusAvailable = thermalStatus != null,
      osBatterySaverOn = osBatterySaverOn,
      screenOn = screenOn,
      screenBrightnessPercent = brightnessPercent,
      canWriteSettings = canWriteSettings,
      hotspotLikelyActive = hotspotLikelyActive,
      poorCellularSignal = poorCellularSignal,
      isNetworkMetered = isMetered,
      networkType = networkType,
      standbyBucket = standbyBucket,
      isAppInForeground = isAppInForeground,
      chargeCycleCount = cycleCount,
    )
  }

  private fun detectNetworkType(): NetworkType {
    return runCatching {
      val cm = connectivityManager ?: return NetworkType.NONE
      val net = cm.activeNetwork ?: return NetworkType.NONE
      val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.NONE
      when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
          caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkType.UNMETERED_WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.METERED_CELLULAR
        else -> NetworkType.OTHER
      }
    }.getOrDefault(NetworkType.NONE)
  }

  private fun detectStandbyBucket(): AppStandbyBucket {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return AppStandbyBucket.UNKNOWN
    return runCatching {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return AppStandbyBucket.UNKNOWN
      when (usm.appStandbyBucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> AppStandbyBucket.ACTIVE
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> AppStandbyBucket.WORKING_SET
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> AppStandbyBucket.FREQUENT
        UsageStatsManager.STANDBY_BUCKET_RARE -> AppStandbyBucket.RARE
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> AppStandbyBucket.RESTRICTED
        else -> AppStandbyBucket.UNKNOWN
      }
    }.getOrDefault(AppStandbyBucket.UNKNOWN)
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

  private val coolingBrightness = CoolingBrightnessController(context)
  val coolingBrightnessState get() = coolingBrightness.state
  fun setCoolingBrightnessAllowed(allowed: Boolean) = coolingBrightness.setEnabled(allowed)
  fun reconcileCoolingBrightness(active: Boolean?, target: Int = 95): Boolean = coolingBrightness.reconcile(active, target)
  fun stopCoolingBrightness() = coolingBrightness.retryRestore()
  fun retryCoolingBrightnessRestore() = coolingBrightness.retryRestore()
}
