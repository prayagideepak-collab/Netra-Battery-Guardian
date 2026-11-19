package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ThermalLevel

internal enum class ThermalRestriction { NONE, ELEVATED, SEVERE, MINIMUM }

internal data class ThermalPolicyState(
  val restriction: ThermalRestriction = ThermalRestriction.NONE,
  val clearSinceElapsedMillis: Long? = null,
)

/** Pure observed-state reducer. Dwell is an explicit policy parameter, not a temperature claim. */
internal object ThermalModePolicy {
  fun reduce(
    previous: ThermalPolicyState,
    telemetry: BatteryTelemetry,
    elapsedMillis: Long,
    recoveryDwellMillis: Long,
  ): ThermalPolicyState {
    require(recoveryDwellMillis > 0)
    val observed = when {
      telemetry.thermalLevel.ordinal >= ThermalLevel.CRITICAL.ordinal -> ThermalRestriction.MINIMUM
      telemetry.thermalLevel == ThermalLevel.SEVERE -> ThermalRestriction.SEVERE
      telemetry.thermalLevel == ThermalLevel.MODERATE || telemetry.healthState == BatteryHealthState.OVERHEAT -> ThermalRestriction.ELEVATED
      else -> ThermalRestriction.NONE
    }
    if (observed != ThermalRestriction.NONE) {
      return ThermalPolicyState(maxOf(previous.restriction, observed))
    }
    if (previous.restriction == ThermalRestriction.NONE) return ThermalPolicyState()
    val supportedClear = telemetry.thermalStatusAvailable && telemetry.thermalLevel == ThermalLevel.NONE &&
      telemetry.healthState != BatteryHealthState.OVERHEAT
    if (!supportedClear) return previous.copy(clearSinceElapsedMillis = null)
    val started = previous.clearSinceElapsedMillis
    if (started == null || elapsedMillis < started) {
      return previous.copy(clearSinceElapsedMillis = elapsedMillis)
    }
    return if (elapsedMillis - started >= recoveryDwellMillis) ThermalPolicyState() else previous
  }

  fun applyFloor(baseline: AdaptivePowerTier, restriction: ThermalRestriction): AdaptivePowerTier = when (restriction) {
    ThermalRestriction.NONE -> baseline
    ThermalRestriction.ELEVATED -> AdaptivePowerTier.entries[minOf(baseline.ordinal + 1, AdaptivePowerTier.SURVIVAL.ordinal)]
    ThermalRestriction.SEVERE -> maxOf(applyFloor(baseline, ThermalRestriction.ELEVATED), AdaptivePowerTier.CRITICAL)
    ThermalRestriction.MINIMUM -> AdaptivePowerTier.SURVIVAL
  }
}
