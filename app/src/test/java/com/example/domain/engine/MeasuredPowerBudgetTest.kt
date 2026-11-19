package com.example.domain.engine

import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MeasuredPowerBudgetTest {
  @Test fun everyUnmeasuredNonEssentialFeatureDefersAcrossAllModes() {
    for (tier in AdaptivePowerTier.entries) {
      val engine = AdaptivePowerTierEngine()
      val level = when (tier) {
        AdaptivePowerTier.NORMAL -> 80
        AdaptivePowerTier.SAVER -> 20
        AdaptivePowerTier.CRITICAL -> 10
        AdaptivePowerTier.SURVIVAL -> 4
      }
      engine.evaluate(BatteryTelemetry(levelPercent = level), 0, 0)
      for (feature in PowerFeature.entries.filter { it.importance != FeatureImportance.ESSENTIAL }) {
        assertFalse("$tier $feature", MeasuredPowerBudget.permits(feature))
        assertFalse(engine.decision(feature, engine.state.value.sequence, false).allowed)
      }
    }
  }

  @Test fun essentialFeaturesAreNotDeniedByAnUnmeasuredOptionalBudget() {
    for (feature in PowerFeature.entries.filter { it.importance == FeatureImportance.ESSENTIAL }) {
      assertTrue(MeasuredPowerBudget.permits(feature))
    }
  }

  @Test fun directExportGuardFailsBeforeAnyOutputCanBeOpened() {
    var wrote = false
    try {
      MeasuredPowerBudget.requireOptionalBudget(PowerFeature.REPORT_EXPORT)
      wrote = true
      fail("Missing budget must not allow output")
    } catch (expected: IllegalStateException) {
      assertEquals(MeasuredPowerBudget.DEFERRED_MESSAGE, expected.message)
    }
    assertFalse(wrote)
  }
}
