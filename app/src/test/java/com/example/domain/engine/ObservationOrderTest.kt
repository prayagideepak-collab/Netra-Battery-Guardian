package com.example.domain.engine

import com.example.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class ObservationOrderTest {
  @Test fun olderCollectionCannotClearNewerThermalRestrictionDespiteLaterEvaluation() {
    val engine = AdaptivePowerTierEngine()
    val oldClear = BatteryTelemetry(levelPercent = 80, thermalStatusAvailable = true, collectionStartedElapsedMillis = 100)
    val newerHot = oldClear.copy(thermalLevel = ThermalLevel.CRITICAL, collectionStartedElapsedMillis = 200)
    engine.evaluate(newerHot, 0, 0)
    val accepted = engine.state.value
    val result = engine.evaluate(oldClear, 9, 9)
    assertEquals(AdaptivePowerTier.SURVIVAL, result.activeTier)
    assertEquals(accepted, engine.state.value)
    assertTrue(accepted.hasCollectionProvenance)
    assertEquals(200L, accepted.observedAtElapsedMillis)
  }

  @Test fun olderCollectionCannotResetBatteryHysteresis() {
    val engine = AdaptivePowerTierEngine()
    engine.evaluate(BatteryTelemetry(levelPercent = 4, collectionStartedElapsedMillis = 200), 0, 0)
    engine.evaluate(BatteryTelemetry(levelPercent = 80, collectionStartedElapsedMillis = 100), 0, 0)
    assertEquals(AdaptivePowerTier.SURVIVAL, engine.evaluate(BatteryTelemetry(levelPercent = 7, collectionStartedElapsedMillis = 300), 0, 0).activeTier)
  }

  @Test fun missingCollectionProvenanceIsLabelledRatherThanInvented() {
    val engine = AdaptivePowerTierEngine()
    engine.evaluate(BatteryTelemetry(levelPercent = 80), 0, 0, 100)
    assertFalse(engine.state.value.hasCollectionProvenance)
    assertEquals(100L, engine.state.value.observedAtElapsedMillis)
  }

  @Test fun observerSeesOtherConsumersWithoutPollingAndCanCancelSubscription() = runBlocking {
    val engine = AdaptivePowerTierEngine()
    val seen = mutableListOf<AdaptivePowerTier>()
    val job = launch(Dispatchers.Unconfined) { engine.state.collect { seen.add(it.activeTier) } }
    engine.evaluate(BatteryTelemetry(levelPercent = 80, collectionStartedElapsedMillis = 100), 0, 0)
    engine.evaluate(BatteryTelemetry(levelPercent = 4, collectionStartedElapsedMillis = 200), 0, 0)
    assertEquals(listOf(AdaptivePowerTier.CRITICAL, AdaptivePowerTier.NORMAL, AdaptivePowerTier.SURVIVAL), seen)
    job.cancelAndJoin()
    engine.evaluate(BatteryTelemetry(levelPercent = 80, collectionStartedElapsedMillis = 300), 0, 0)
    assertEquals(3, seen.size)
  }
}
