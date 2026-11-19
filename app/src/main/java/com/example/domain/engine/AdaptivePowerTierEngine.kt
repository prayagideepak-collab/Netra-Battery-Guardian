package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppStandbyBucket
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines battery, OS Battery Saver, thermal and standby restrictions.
 * Screen/foreground state adjusts existing refresh intervals. Charging never
 * overrides a restriction; offline operation does not require a network monitor.
 *
 * Battery hysteresis and thermal recovery use independent pure reducers.
 * Physical-device power and thermal behavior still require measurement.
 */
@Singleton
class AdaptivePowerTierEngine @Inject constructor() {

  private var currentBatteryTier: AdaptivePowerTier = AdaptivePowerTier.NORMAL
  private var thermalState = ThermalPolicyState()

  @Synchronized
  fun evaluate(
    telemetry: BatteryTelemetry,
    wakeupsThisHour: Int,
    snapshotsToday: Int,
    elapsedRealtimeMillis: Long = System.nanoTime() / 1_000_000L,
  ): PowerBudgetMetrics {
    // Preserve battery-only hysteresis separately from Saver/thermal/system floors.
    // Charging cannot upgrade a restriction selected by another input.
    val batteryTier = BatteryModePolicy.reduce(currentBatteryTier, telemetry)
    val availableBatteryTier =
      if (!telemetry.hasLevel && batteryTier.ordinal < AdaptivePowerTier.CRITICAL.ordinal) {
        AdaptivePowerTier.CRITICAL
      } else {
        batteryTier
      }
    val systemRestrictedTier = BatteryModePolicy.systemFloor(availableBatteryTier, telemetry)

    // Background + Standby Bucket conservation
    val standbyDegraded =
      !telemetry.isAppInForeground &&
        (telemetry.standbyBucket == AppStandbyBucket.RARE ||
          telemetry.standbyBucket == AppStandbyBucket.RESTRICTED)

    val preThermalTier =
      if (standbyDegraded && systemRestrictedTier == AdaptivePowerTier.NORMAL) AdaptivePowerTier.SAVER
      else systemRestrictedTier

    thermalState = ThermalModePolicy.reduce(
      thermalState, telemetry, elapsedRealtimeMillis, THERMAL_RECOVERY_DWELL_MILLIS,
    )
    val finalTier = ThermalModePolicy.applyFloor(preThermalTier, thermalState.restriction)

    currentBatteryTier = batteryTier

    val uiIntervalSec =
      when {
        !telemetry.isAppInForeground || !telemetry.screenOn -> finalTier.samplingIntervalSeconds * 4
        else -> finalTier.samplingIntervalSeconds
      }

    val dbBatchIntervalSec =
      when (finalTier) {
        AdaptivePowerTier.NORMAL -> 60
        AdaptivePowerTier.SAVER -> 300
        AdaptivePowerTier.CRITICAL -> 900
        AdaptivePowerTier.SURVIVAL -> 3600
      }

    val suspendedFeatures =
      when (finalTier) {
        AdaptivePowerTier.NORMAL -> emptyList()
        AdaptivePowerTier.SAVER ->
          listOf("Frequent UI refresh", "Non-essential background sync")
        AdaptivePowerTier.CRITICAL ->
          listOf("Storage directory scans", "UsageStats app polling", "Routine voice milestones")
        AdaptivePowerTier.SURVIVAL ->
          listOf("Animations", "Sensors & GPS sampling", "Storage scans", "App polling", "Routine TTS")
      }

    return PowerBudgetMetrics(
      activeTier = finalTier,
      wakeupsThisHour = wakeupsThisHour,
      networkCallsThisHour = 0, // Strictly 100% offline
      snapshotsRecordedToday = snapshotsToday,
      monitoringIntervalSeconds = uiIntervalSec,
      isThermalThrottled = thermalState.restriction != ThermalRestriction.NONE,
      dbBatchIntervalSeconds = dbBatchIntervalSec,
      suspendedFeatures = suspendedFeatures,
    )
  }

  companion object {
    // Q9-approved experiment parameter, not a measured physical safe-temperature threshold.
    const val THERMAL_RECOVERY_DWELL_MILLIS = 30_000L
  }
}
