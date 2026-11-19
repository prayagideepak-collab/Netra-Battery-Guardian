package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppStandbyBucket
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import com.example.domain.model.PowerPolicyState
import com.example.domain.model.PowerRestriction
import com.example.domain.model.PowerFeature
import com.example.domain.model.FeatureImportance
import com.example.domain.model.FeatureDecision
import com.example.domain.model.FeatureDecisionReason
import com.example.domain.model.FeatureDisposition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val _state = MutableStateFlow(PowerPolicyState())
  val state: StateFlow<PowerPolicyState> = _state.asStateFlow()

  @Synchronized
  fun evaluate(
    telemetry: BatteryTelemetry,
    wakeupsThisHour: Int,
    snapshotsToday: Int,
    elapsedRealtimeMillis: Long = telemetry.collectionStartedElapsedMillis ?: (System.nanoTime() / 1_000_000L),
  ): PowerBudgetMetrics {
    val previous = _state.value
    if (previous.observedAtElapsedMillis != null && elapsedRealtimeMillis < previous.observedAtElapsedMillis) {
      // Reject out-of-order evaluations before either reducer can relax or ratchet state.
      previous.metrics?.let { return it }
    }
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

    val metrics = PowerBudgetMetrics(
      activeTier = finalTier,
      wakeupsThisHour = wakeupsThisHour,
      networkCallsThisHour = 0, // Strictly 100% offline
      snapshotsRecordedToday = snapshotsToday,
      monitoringIntervalSeconds = uiIntervalSec,
      isThermalThrottled = thermalState.restriction != ThermalRestriction.NONE,
      dbBatchIntervalSeconds = dbBatchIntervalSec,
      suspendedFeatures = suspendedFeatures,
    )
    val restrictions = buildSet {
      if (!telemetry.hasLevel) add(PowerRestriction.BATTERY_UNAVAILABLE)
      if (batteryTier != AdaptivePowerTier.NORMAL) add(PowerRestriction.BATTERY_LOW)
      if (telemetry.osBatterySaverOn) add(PowerRestriction.BATTERY_SAVER)
      if (standbyDegraded) add(PowerRestriction.STANDBY)
      if (thermalState.restriction != ThermalRestriction.NONE) add(PowerRestriction.THERMAL)
    }
    _state.value = PowerPolicyState(
      sequence = previous.sequence + 1,
      observedAtElapsedMillis = elapsedRealtimeMillis,
      activeTier = finalTier,
      batteryLevelAvailable = telemetry.hasLevel,
      thermalStatusAvailable = telemetry.thermalStatusAvailable,
      isAppInForeground = telemetry.isAppInForeground,
      screenOn = telemetry.screenOn,
      hasCollectionProvenance = telemetry.collectionStartedElapsedMillis != null,
      restrictions = restrictions,
      metrics = metrics,
    )
    return metrics
  }

  /** Recheck after suspending work or before another batch. This does not schedule work. */
  @Synchronized
  fun decision(feature: PowerFeature, expectedSequence: Long, inDoze: Boolean?): FeatureDecision {
    val current = _state.value
    val mode = FeatureModePolicy.evaluate(
      feature, current, System.nanoTime() / 1_000_000L, inDoze,
      stateFresh = current.sequence == expectedSequence,
    )
    if (!mode.allowed || MeasuredPowerBudget.permits(feature)) return mode
    // Finite per-feature limits remain unmeasured. Never turn missing evidence into unlimited work.
    return FeatureDecision(
      FeatureDisposition.DEFER, FeatureDecisionReason.BUDGET_NOT_VERIFIED,
      current.sequence, "Next permitted opportunity after finite measured budgets are approved.",
    )
  }

  companion object {
    // Q9-approved experiment parameter, not a measured physical safe-temperature threshold.
    const val THERMAL_RECOVERY_DWELL_MILLIS = 30_000L
  }
}
