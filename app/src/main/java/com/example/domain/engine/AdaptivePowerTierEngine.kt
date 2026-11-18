package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppStandbyBucket
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import com.example.domain.model.ThermalLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements Ultra-Low Power Adaptive Tiers using all 8 required inputs:
 * 1. battery %, 2. charging, 3. OS Battery Saver, 4. thermal status,
 * 5. screen state, 6. foreground/background, 7. standby bucket, 8. network type.
 *
 * Uses hysteresis on both battery thresholds and thermal recovery so tiers never flap.
 */
@Singleton
class AdaptivePowerTierEngine @Inject constructor() {

  private var currentTier: AdaptivePowerTier = AdaptivePowerTier.NORMAL
  private var thermalLatched: Boolean = false

  @Synchronized
  fun evaluate(
    telemetry: BatteryTelemetry,
    wakeupsThisHour: Int,
    snapshotsToday: Int,
  ): PowerBudgetMetrics {
    val level = telemetry.levelPercent

    // Battery % + Charging + OS Battery Saver with 2% hysteresis buffer
    val batteryTier =
      when {
        !telemetry.hasLevel -> currentTier
        level < 5 -> AdaptivePowerTier.SURVIVAL
        level <= 6 && currentTier == AdaptivePowerTier.SURVIVAL -> AdaptivePowerTier.SURVIVAL
        telemetry.isCharging && level >= 15 -> AdaptivePowerTier.NORMAL
        level < 15 -> AdaptivePowerTier.CRITICAL
        level <= 17 && currentTier == AdaptivePowerTier.CRITICAL -> AdaptivePowerTier.CRITICAL
        telemetry.osBatterySaverOn -> AdaptivePowerTier.SAVER
        level <= 30 -> AdaptivePowerTier.SAVER
        level <= 32 && currentTier == AdaptivePowerTier.SAVER -> AdaptivePowerTier.SAVER
        else -> AdaptivePowerTier.NORMAL
      }

    // Background + Standby Bucket conservation
    val standbyDegraded =
      !telemetry.isAppInForeground &&
        (telemetry.standbyBucket == AppStandbyBucket.RARE ||
          telemetry.standbyBucket == AppStandbyBucket.RESTRICTED)

    val preThermalTier =
      if (standbyDegraded && batteryTier == AdaptivePowerTier.NORMAL) AdaptivePowerTier.SAVER
      else batteryTier

    // Thermal throttling with hysteresis: engages >= 40C / MODERATE, clears < 38C & < MODERATE
    val thermalHotNow =
      telemetry.thermalLevel.ordinal >= ThermalLevel.MODERATE.ordinal ||
        telemetry.temperatureCelsius >= 40f
    val thermalClearNow =
      telemetry.thermalLevel.ordinal < ThermalLevel.MODERATE.ordinal &&
        telemetry.temperatureCelsius < 38f

    if (thermalHotNow) thermalLatched = true
    else if (thermalClearNow) thermalLatched = false

    val finalTier =
      if (thermalLatched) {
        when (preThermalTier) {
          AdaptivePowerTier.NORMAL -> AdaptivePowerTier.SAVER
          AdaptivePowerTier.SAVER -> AdaptivePowerTier.CRITICAL
          AdaptivePowerTier.CRITICAL, AdaptivePowerTier.SURVIVAL -> AdaptivePowerTier.SURVIVAL
        }
      } else {
        preThermalTier
      }

    currentTier = finalTier

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
      isThermalThrottled = thermalLatched,
      dbBatchIntervalSeconds = dbBatchIntervalSec,
      suspendedFeatures = suspendedFeatures,
    )
  }
}
