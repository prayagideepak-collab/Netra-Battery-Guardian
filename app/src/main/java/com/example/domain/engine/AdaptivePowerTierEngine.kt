package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import com.example.domain.model.ThermalLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements Ultra-Low Power Adaptive Tiers with hysteresis and thermal drop-one-tier rule.
 * Tier-0 essential safety & battery transition functions continue even in SURVIVAL.
 */
@Singleton
class AdaptivePowerTierEngine @Inject constructor() {

  private var currentTier: AdaptivePowerTier = AdaptivePowerTier.NORMAL

  @Synchronized
  fun evaluate(
    telemetry: BatteryTelemetry,
    wakeupsThisHour: Int,
    snapshotsToday: Int,
  ): PowerBudgetMetrics {
    val level = telemetry.levelPercent
    // Hysteresis: 2% buffer prevents rapid tier flapping around 30%, 15%, 5%
    val baseTier =
      when {
        level < 5 -> AdaptivePowerTier.SURVIVAL
        level <= 5 && currentTier == AdaptivePowerTier.SURVIVAL -> AdaptivePowerTier.SURVIVAL
        telemetry.isCharging && level >= 15 -> AdaptivePowerTier.NORMAL
        level < 15 -> AdaptivePowerTier.CRITICAL
        level <= 17 && currentTier == AdaptivePowerTier.CRITICAL -> AdaptivePowerTier.CRITICAL
        telemetry.osBatterySaverOn -> AdaptivePowerTier.SAVER
        level <= 30 -> AdaptivePowerTier.SAVER
        level <= 32 && currentTier == AdaptivePowerTier.SAVER -> AdaptivePowerTier.SAVER
        else -> AdaptivePowerTier.NORMAL
      }

    val isThermalThrottled =
      telemetry.thermalLevel.ordinal >= ThermalLevel.MODERATE.ordinal ||
        telemetry.temperatureCelsius >= 40f

    val finalTier =
      if (isThermalThrottled) {
        when (baseTier) {
          AdaptivePowerTier.NORMAL -> AdaptivePowerTier.SAVER
          AdaptivePowerTier.SAVER -> AdaptivePowerTier.CRITICAL
          AdaptivePowerTier.CRITICAL, AdaptivePowerTier.SURVIVAL -> AdaptivePowerTier.SURVIVAL
        }
      } else {
        baseTier
      }

    currentTier = finalTier
    val intervalSec =
      if (telemetry.screenOn) finalTier.samplingIntervalSeconds
      else finalTier.samplingIntervalSeconds * 4

    return PowerBudgetMetrics(
      activeTier = finalTier,
      wakeupsThisHour = wakeupsThisHour,
      networkCallsThisHour = 0, // Strictly 100% offline
      snapshotsRecordedToday = snapshotsToday,
      monitoringIntervalSeconds = intervalSec,
      isThermalThrottled = isThermalThrottled,
    )
  }
}
