package com.example.domain.engine

import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ThermalLevel

/** Process-local notification-attempt ownership. Not a delivery receipt or durable safety state. */
class ThermalNotificationEpisode {
  private var highestClaimed = 0
  private var clearSince: Long? = null
  private var latestElapsed: Long? = null

  @Synchronized
  fun observeAndClaim(telemetry: BatteryTelemetry, elapsedMillis: Long, canPost: Boolean): Boolean {
    val severity = maxOf(
      when (telemetry.thermalLevel) {
        ThermalLevel.MODERATE -> 1
        ThermalLevel.SEVERE -> 2
        ThermalLevel.CRITICAL -> 3
        ThermalLevel.EMERGENCY -> 4
        ThermalLevel.SHUTDOWN -> 5
        else -> 0
      },
      if (telemetry.healthState == BatteryHealthState.OVERHEAT) 3 else 0,
    )
    val older = latestElapsed?.let { elapsedMillis < it } ?: false
    if (!older) latestElapsed = elapsedMillis
    if (severity > 0) {
      // An older urgent observation may escalate, but must never clear a newer restriction.
      clearSince = null
      if (!canPost || severity <= highestClaimed) return false
      highestClaimed = severity
      return true
    }
    if (older) return false
    val clear = telemetry.thermalStatusAvailable && telemetry.thermalLevel == ThermalLevel.NONE &&
      telemetry.healthState != BatteryHealthState.OVERHEAT
    if (!clear) {
      clearSince = null
      return false
    }
    if (highestClaimed == 0) return false
    val since = clearSince
    if (since == null) clearSince = elapsedMillis
    else if (elapsedMillis - since >= AdaptivePowerTierEngine.THERMAL_RECOVERY_DWELL_MILLIS) {
      highestClaimed = 0
      clearSince = null
    }
    return false
  }
}
