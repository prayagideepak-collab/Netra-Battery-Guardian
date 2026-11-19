package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryTelemetry

/** Pure battery restriction. No IO, clock, Android service, or hidden mutable state. */
internal object BatteryModePolicy {
  fun reduce(
    previousBatteryTier: AdaptivePowerTier,
    telemetry: BatteryTelemetry,
  ): AdaptivePowerTier {
    if (!telemetry.hasLevel) return previousBatteryTier
    val level = telemetry.levelPercent
    return when {
      level < 5 -> AdaptivePowerTier.SURVIVAL
      // The approved exact-5 rule wins over recovery hysteresis in both directions.
      level == 5 -> AdaptivePowerTier.CRITICAL
      previousBatteryTier == AdaptivePowerTier.SURVIVAL && level < 8 -> AdaptivePowerTier.SURVIVAL
      level < 15 -> AdaptivePowerTier.CRITICAL
      previousBatteryTier.ordinal >= AdaptivePowerTier.CRITICAL.ordinal && level < 18 -> AdaptivePowerTier.CRITICAL
      level <= 30 -> AdaptivePowerTier.SAVER
      previousBatteryTier.ordinal >= AdaptivePowerTier.SAVER.ordinal && level < 33 -> AdaptivePowerTier.SAVER
      else -> AdaptivePowerTier.NORMAL
    }
  }

  /** Independent restrictions cannot be cancelled by charging or by another input. */
  fun systemFloor(batteryTier: AdaptivePowerTier, telemetry: BatteryTelemetry): AdaptivePowerTier =
    if (telemetry.osBatterySaverOn && batteryTier == AdaptivePowerTier.NORMAL) {
      AdaptivePowerTier.SAVER
    } else {
      batteryTier
    }
}
