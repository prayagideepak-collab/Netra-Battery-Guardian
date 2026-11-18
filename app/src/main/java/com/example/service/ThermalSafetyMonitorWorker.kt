package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.domain.model.TemperatureZone
import com.example.domain.model.ThermalLevel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Background WorkManager worker that samples battery temperature via Android's [BatteryManager]
 * API and logs critical thermal events into Netra's offline Room database ([AppDatabase]).
 *
 * Designed for ultra-low power:
 * - Zero polling loops or wake locks; relies on OS-scheduled WorkManager intervals.
 * - Performs ZERO Room database writes when Android reports no overheat or critical thermal state,
 *   persisting snapshots and critical logs exclusively when a genuine critical thermal condition
 *   or [BatteryManager.BATTERY_HEALTH_OVERHEAT] state occurs (Tier-0 device safety).
 */
@HiltWorker
class ThermalSafetyMonitorWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val temperatureEngine: TemperatureControlEngine,
  private val preferencesRepository: PreferencesRepository,
  private val appDatabase: AppDatabase,
  private val notificationHelper: NotificationHelper,
  private val logWriter: BoundedLogFileWriter,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  companion object {
    const val CRITICAL_TITLE_EN = "Critical Thermal Event Detected"
  }

  override suspend fun doWork(): Result {
    val stickyBatteryIntent = applicationContext.registerReceiver(
      null,
      IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    )

    val prefs = preferencesRepository.preferences.first()
    val batteryManager =
      applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    val telemetry = telemetryCollector.collect(
      intentOverride = stickyBatteryIntent,
      observedCapacityFallbackMah = prefs.observedCapacityMah,
      isAppInForeground = false,
    )

    // Cross-check BatteryManager health/level/charging properties directly
    val rawHealth =
      stickyBatteryIntent?.getIntExtra(
        BatteryManager.EXTRA_HEALTH,
        BatteryManager.BATTERY_HEALTH_UNKNOWN,
      ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
    val bmLevel =
      batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
        ?: telemetry.levelPercent
    val isChargingNow = batteryManager?.isCharging ?: telemetry.isCharging

    val tempTenths = stickyBatteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val tempC =
      if (tempTenths != null && tempTenths != Int.MIN_VALUE) tempTenths / 10f
      else telemetry.temperatureCelsius

    val zone = temperatureEngine.classifyObserved(telemetry)
    val isOverheatHealth =
      rawHealth == BatteryManager.BATTERY_HEALTH_OVERHEAT ||
        telemetry.healthState == BatteryHealthState.OVERHEAT
    val isOsCriticalThermal =
      telemetry.thermalLevel in setOf(ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN)
    val isCriticalTemp =
      isOverheatHealth ||
        isOsCriticalThermal

    if (!isCriticalTemp) {
      // Safe temperature: avoid extra Room writes to minimize background flash & battery wear
      if (dozeModeMonitor.shouldSkipNonEssentialWork()) {
        return Result.success()
      }
      return Result.success()
    }

    val nowMillis = System.currentTimeMillis()
    val tempStr = com.example.domain.model.telemetryText(tempC, "°C")
    val voltageVolts = com.example.domain.model.telemetryText(telemetry.voltageVolts, " V", 2)

    // Persist snapshot and critical thermal event log atomically to Room
    appDatabase.batteryDao().insertSnapshot(
      BatterySnapshot(
        timestampMillis = nowMillis,
        levelPercent = bmLevel,
        voltageMv = telemetry.voltageMv,
        temperatureCelsius = tempC.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        currentMa = telemetry.currentMa.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        powerWatts = telemetry.instantaneousPowerWatts.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        isCharging = isChargingNow,
        chargingStatus = if (isChargingNow) "CHARGING" else "DISCHARGING",
        isPlugged = telemetry.isPlugged,
        plugType = telemetry.plugType.name,
        screenOn = telemetry.screenOn,
        thermalLevel = telemetry.thermalLevel.name,
        activeProfile = "THERMAL_WORKER",
        isHourlyCheckpoint = false,
      )
    )

    val detailEn = buildString {
      append("$tempStr (${zone.name}) at ${com.example.domain.model.batteryLevelText(bmLevel)}, $voltageVolts")
      if (isOverheatHealth) append(" • OS BATTERY_HEALTH_OVERHEAT")
      if (isChargingNow) append(" • Charger plugged (unplug to cool)")
    }
    val detailHi =
      "$tempStr (${zone.name}) • स्तर: ${com.example.domain.model.batteryLevelText(bmLevel)}, वोल्टेज: $voltageVolts।" +
        if (isChargingNow) " ठंडा करने के लिए चार्जर हटाएँ।" else " भारी उपयोग रोकें।"

    appDatabase.netraDao().insertActivityLog(
      ActivityLogEntity(
        timestampMillis = nowMillis,
        category = LogCategory.HEAT.name,
        severity = LogSeverity.CRITICAL.name,
        titleEn = "$CRITICAL_TITLE_EN ($tempStr)",
        titleHi = "गंभीर बैटरी तापमान चेतावनी ($tempStr)",
        detailEn = detailEn,
        detailHi = detailHi,
        whyEn = "Background WorkManager thermal guard detected dangerous battery heat via BatteryManager and logged it offline.",
        whyHi = "बैकग्राउंड WorkManager मॉनिटर ने BatteryManager से अत्यधिक गर्मी का पता लगाकर इसे ऑफ़लाइन दर्ज किया।",
      )
    )

    logWriter.append("THERMAL_WORKER", "CRITICAL thermal event logged: $detailEn")
    notificationHelper.postCriticalAlert(
      id = NotificationHelper.ID_THERMAL_ALERT,
      title = "Critical Battery Heat ($tempStr)",
      body = if (isChargingNow) {
        "Background monitor detected $tempStr while charging. Unplug charger to prevent battery wear."
      } else {
        "Background monitor detected $tempStr. Pause heavy tasks and let device cool."
      },
    )

    return Result.success()
  }
}
