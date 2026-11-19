package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ThermalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalModePolicyTest {
  private val clear = BatteryTelemetry(levelPercent = 80, thermalStatusAvailable = true)
  private fun step(state: ThermalPolicyState, sample: BatteryTelemetry, time: Long) =
    ThermalModePolicy.reduce(state, sample, time, 30_000L)
  private fun hot(level: ThermalLevel = ThermalLevel.MODERATE) =
    clear.copy(thermalLevel = level)

  @Test fun severityFloorsAndAtLeastOneLevelAreEnforced() {
    for (baseline in AdaptivePowerTier.entries) {
      assertEquals(baseline, ThermalModePolicy.applyFloor(baseline, ThermalRestriction.NONE))
      val elevated = ThermalModePolicy.applyFloor(baseline, ThermalRestriction.ELEVATED)
      assertEquals(minOf(baseline.ordinal + 1, 3), elevated.ordinal)
      val severe = ThermalModePolicy.applyFloor(baseline, ThermalRestriction.SEVERE)
      assertTrue(severe.ordinal >= elevated.ordinal && severe.ordinal >= AdaptivePowerTier.CRITICAL.ordinal)
      assertEquals(AdaptivePowerTier.SURVIVAL, ThermalModePolicy.applyFloor(baseline, ThermalRestriction.MINIMUM))
    }
  }

  @Test fun eachOsSeverityHasAnExplicitMapping() {
    val expected = mapOf(
      ThermalLevel.NONE to ThermalRestriction.NONE, ThermalLevel.LIGHT to ThermalRestriction.NONE,
      ThermalLevel.MODERATE to ThermalRestriction.ELEVATED, ThermalLevel.SEVERE to ThermalRestriction.SEVERE,
      ThermalLevel.CRITICAL to ThermalRestriction.MINIMUM, ThermalLevel.EMERGENCY to ThermalRestriction.MINIMUM,
      ThermalLevel.SHUTDOWN to ThermalRestriction.MINIMUM,
    )
    for ((level, restriction) in expected) assertEquals(restriction, step(ThermalPolicyState(), hot(level), 0).restriction)
  }

  @Test fun thirtySecondRecoveryRequiresTwoSupportedClearObservations() {
    var state = step(ThermalPolicyState(), hot(), 0)
    state = step(state, clear, 1_000)
    assertEquals(ThermalRestriction.ELEVATED, step(state, clear, 30_999).restriction)
    assertEquals(ThermalRestriction.NONE, step(state, clear, 31_000).restriction)
  }

  @Test fun unknownLightAndOverheatBreakRecoveryDwell() {
    for (interrupted in listOf(clear.copy(thermalStatusAvailable = false), hot(ThermalLevel.LIGHT), clear.copy(healthState = BatteryHealthState.OVERHEAT))) {
      var state = step(ThermalPolicyState(), hot(ThermalLevel.SEVERE), 0)
      state = step(state, clear, 100)
      state = step(state, interrupted, 30_000)
      assertEquals(ThermalRestriction.SEVERE, state.restriction)
      assertEquals(null, state.clearSinceElapsedMillis)
      state = step(state, clear, 40_000)
      assertEquals(ThermalRestriction.SEVERE, step(state, clear, 69_999).restriction)
      assertEquals(ThermalRestriction.NONE, step(state, clear, 70_000).restriction)
    }
  }

  @Test fun escalationIsImmediateAndLesserHotReportsDoNotRestoreWork() {
    var state = step(ThermalPolicyState(), hot(), 0)
    state = step(state, clear, 1)
    state = step(state, hot(ThermalLevel.CRITICAL), 2)
    assertEquals(ThermalRestriction.MINIMUM, state.restriction)
    assertEquals(null, state.clearSinceElapsedMillis)
    assertEquals(ThermalRestriction.MINIMUM, step(state, hot(), 100_000).restriction)
  }

  @Test fun elapsedClockRollbackRestartsDwellAndWallClockIsNotAnInput() {
    var state = step(ThermalPolicyState(), hot(), 100_000)
    state = step(state, clear, 100_001)
    state = step(state, clear, 3)
    assertEquals(3L, state.clearSinceElapsedMillis)
    assertEquals(ThermalRestriction.ELEVATED, step(state, clear, 30_002).restriction)
    assertEquals(ThermalRestriction.NONE, step(state, clear, 30_003).restriction)
  }

  @Test fun engineKeepsThermalAndBatteryHysteresisSeparate() {
    val engine = AdaptivePowerTierEngine()
    val sample = hot().copy(levelPercent = 31)
    repeat(40) { assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(sample, 0, 0, it.toLong()).activeTier) }
    assertTrue(engine.evaluate(clear.copy(levelPercent = 31), 0, 0, 1_000).isThermalThrottled)
    val restored = engine.evaluate(clear.copy(levelPercent = 31), 0, 0, 31_000)
    assertFalse(restored.isThermalThrottled)
    assertEquals(AdaptivePowerTier.NORMAL, restored.activeTier)
  }

  @Test fun severeAndCriticalThermalCannotRemainInSaver() {
    val severe = AdaptivePowerTierEngine().evaluate(hot(ThermalLevel.SEVERE), 0, 0, 0)
    assertEquals(AdaptivePowerTier.CRITICAL, severe.activeTier)
    for (level in listOf(ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN)) {
      assertEquals(AdaptivePowerTier.SURVIVAL, AdaptivePowerTierEngine().evaluate(hot(level), 0, 0, 0).activeTier)
    }
  }
}
