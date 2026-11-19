package com.example.domain.engine

import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SharedPowerStateTest {
  private val normal = BatteryTelemetry(levelPercent = 80, thermalStatusAvailable = true)

  @Test fun initialStateHasNoFictionalObservation() {
    val engine = AdaptivePowerTierEngine()
    assertEquals(0L, engine.state.value.sequence)
    assertNull(engine.state.value.metrics)
    assertFalse(engine.state.value.batteryLevelAvailable)
    assertEquals(setOf(PowerRestriction.NOT_OBSERVED), engine.state.value.restrictions)
  }

  @Test fun publishedDecisionMatchesReturnedMetricsAndRetainsReasons() {
    val engine = AdaptivePowerTierEngine()
    val metrics = engine.evaluate(normal.copy(osBatterySaverOn = true, thermalLevel = ThermalLevel.SEVERE), 7, 9, 10)
    val state = engine.state.value
    assertEquals(metrics, state.metrics)
    assertEquals(metrics.activeTier, state.activeTier)
    assertEquals(1L, state.sequence)
    assertEquals(10L, state.observedAtElapsedMillis)
    assertTrue(state.restrictions.containsAll(setOf(PowerRestriction.BATTERY_SAVER, PowerRestriction.THERMAL)))
    assertEquals(9, state.metrics!!.snapshotsRecordedToday)
  }

  @Test fun olderEvaluationCannotClearOrRatchetCurrentPolicy() {
    val engine = AdaptivePowerTierEngine()
    val restricted = engine.evaluate(normal.copy(thermalLevel = ThermalLevel.CRITICAL), 0, 0, 100)
    val current = engine.state.value
    assertEquals(restricted, engine.evaluate(normal, 99, 99, 99))
    assertEquals(current, engine.state.value)
  }

  @Test fun immutableSnapshotsDoNotChangeWhenASecondCallerPublishes() {
    val engine = AdaptivePowerTierEngine()
    engine.evaluate(normal, 0, 0, 1)
    val first = engine.state.value
    engine.evaluate(normal.copy(levelPercent = 4), 0, 0, 2)
    assertEquals(AdaptivePowerTier.NORMAL, first.activeTier)
    assertEquals(AdaptivePowerTier.SURVIVAL, engine.state.value.activeTier)
    assertEquals(2L, engine.state.value.sequence)
  }

  @Test fun unmeasuredBudgetCannotBecomeAnOptionalWorkGrant() {
    val engine = AdaptivePowerTierEngine()
    engine.evaluate(normal, 0, 0)
    for (feature in PowerFeature.entries.filter { it.importance != FeatureImportance.ESSENTIAL }) {
      val decision = engine.decision(feature, engine.state.value.sequence, false)
      assertFalse(feature.name, decision.allowed)
      assertEquals(FeatureDecisionReason.BUDGET_NOT_VERIFIED, decision.reason)
    }
  }

  @Test fun essentialsRemainPossibleButDecisionDoesNotGrantConsent() {
    val engine = AdaptivePowerTierEngine()
    for (feature in PowerFeature.entries.filter { it.importance == FeatureImportance.ESSENTIAL }) {
      assertTrue(engine.decision(feature, 0, null).allowed)
      assertEquals(FeatureDecisionReason.ESSENTIAL_OPPORTUNITY, engine.decision(feature, 0, true).reason)
    }
  }

  @Test fun changedSequenceInvalidatesAnOptionalWorkSnapshot() {
    val engine = AdaptivePowerTierEngine()
    engine.evaluate(normal, 0, 0)
    val sequence = engine.state.value.sequence
    engine.evaluate(normal.copy(levelPercent = 4), 0, 0)
    assertFalse(engine.decision(PowerFeature.REPORT_EXPORT, sequence, false).allowed)
    assertEquals(FeatureDecisionReason.STALE_STATE, engine.decision(PowerFeature.REPORT_EXPORT, sequence, false).reason)
  }

  @Test fun concurrentPublishersDoNotLoseSequenceOrMismatchMetrics() {
    val engine = AdaptivePowerTierEngine()
    val pool = Executors.newFixedThreadPool(4)
    val ready = CountDownLatch(1)
    val work = (1..100).map { index -> pool.submit {
      ready.await()
      engine.evaluate(normal.copy(levelPercent = if (index % 2 == 0) 4 else 80), index, index, 100)
    } }
    ready.countDown()
    work.forEach { it.get(5, TimeUnit.SECONDS) }
    pool.shutdown()
    assertEquals(100L, engine.state.value.sequence)
    assertEquals(engine.state.value.activeTier, engine.state.value.metrics!!.activeTier)
  }

  @Test fun modeMatrixRestrictsAllOptionalImportanceTiersAndPreservesEssentials() {
    for (tier in AdaptivePowerTier.entries) for (feature in PowerFeature.entries) {
      val state = PowerPolicyState(sequence = 1, observedAtElapsedMillis = 10, activeTier = tier,
        batteryLevelAvailable = true, isAppInForeground = true, screenOn = true)
      val decision = FeatureModePolicy.evaluate(feature, state, 11, false, true)
      val expected = feature.importance == FeatureImportance.ESSENTIAL || tier.ordinal < AdaptivePowerTier.CRITICAL.ordinal
      assertEquals("$tier $feature", expected, decision.allowed)
    }
  }

  @Test fun dozeUnknownBatteryAndVisibilityEachHaveExplicitDeferral() {
    val state = PowerPolicyState(sequence = 1, observedAtElapsedMillis = 10, activeTier = AdaptivePowerTier.NORMAL,
      batteryLevelAvailable = true, isAppInForeground = true, screenOn = true)
    assertEquals(FeatureDecisionReason.DOZE_OR_UNKNOWN, FeatureModePolicy.evaluate(PowerFeature.STORAGE_SCAN, state, 11, null, true).reason)
    assertEquals(FeatureDecisionReason.STATE_UNAVAILABLE, FeatureModePolicy.evaluate(PowerFeature.STORAGE_SCAN, state.copy(batteryLevelAvailable = false), 11, false, true).reason)
    assertEquals(FeatureDecisionReason.NOT_VISIBLE, FeatureModePolicy.evaluate(PowerFeature.ANIMATION, state.copy(screenOn = false), 11, false, true).reason)
    assertEquals(FeatureDecisionReason.STALE_STATE, FeatureModePolicy.evaluate(PowerFeature.STORAGE_SCAN, state, 9, false, true).reason)
  }
}
