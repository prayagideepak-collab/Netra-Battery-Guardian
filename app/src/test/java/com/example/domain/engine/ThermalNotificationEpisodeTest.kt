package com.example.domain.engine

import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ThermalNotificationEpisodeTest {
  private val clear = BatteryTelemetry(thermalStatusAvailable = true)
  private fun hot(level: ThermalLevel = ThermalLevel.MODERATE) = clear.copy(thermalLevel = level)
  @Test fun sameAndLowerSeverityNeverRepeatEvenAfterOldCooldowns() {
    val p = ThermalNotificationEpisode()
    assertTrue(p.observeAndClaim(hot(ThermalLevel.SEVERE), 0, true))
    assertFalse(p.observeAndClaim(hot(ThermalLevel.SEVERE), 3_600_000, true))
    assertFalse(p.observeAndClaim(hot(), 7_200_000, true))
  }
  @Test fun everyHigherOsSeverityEscalatesImmediately() {
    val p = ThermalNotificationEpisode()
    for ((i, l) in listOf(ThermalLevel.MODERATE, ThermalLevel.SEVERE, ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY, ThermalLevel.SHUTDOWN).withIndex()) {
      assertTrue(p.observeAndClaim(hot(l), i.toLong(), true))
      assertFalse(p.observeAndClaim(hot(l), i.toLong(), true))
    }
  }
  @Test fun thirtySecondsOfSupportedClearRearmsTheEpisode() {
    val p = ThermalNotificationEpisode()
    assertTrue(p.observeAndClaim(hot(), 0, true))
    p.observeAndClaim(clear, 1_000, false)
    p.observeAndClaim(clear, 30_999, false)
    p.observeAndClaim(clear, 31_000, false)
    assertTrue(p.observeAndClaim(hot(), 31_001, true))
  }
  @Test fun unknownAndLightBreakTheClearDwell() {
    for (t in listOf(clear.copy(thermalStatusAvailable = false), hot(ThermalLevel.LIGHT))) {
      val p = ThermalNotificationEpisode()
      p.observeAndClaim(hot(), 0, true)
      p.observeAndClaim(clear, 1_000, false)
      p.observeAndClaim(t, 30_000, false)
      p.observeAndClaim(clear, 31_000, false)
      assertFalse(p.observeAndClaim(hot(), 31_001, true))
    }
  }
  @Test fun overheatIsCriticalButCannotMaskShutdownEscalation() {
    val p = ThermalNotificationEpisode()
    assertTrue(p.observeAndClaim(clear.copy(healthState = BatteryHealthState.OVERHEAT), 0, true))
    assertFalse(p.observeAndClaim(hot(ThermalLevel.SEVERE), 1, true))
    assertTrue(p.observeAndClaim(hot(ThermalLevel.SHUTDOWN).copy(healthState = BatteryHealthState.OVERHEAT), 2, true))
  }
  @Test fun deniedNotificationDoesNotConsumeTheWarning() {
    val p = ThermalNotificationEpisode()
    assertFalse(p.observeAndClaim(hot(), 0, false))
    assertTrue(p.observeAndClaim(hot(), 1, true))
  }
  @Test fun staleClearCannotResetButStaleUrgentEventCanEscalate() {
    val p = ThermalNotificationEpisode()
    assertTrue(p.observeAndClaim(hot(), 40_000, true))
    p.observeAndClaim(clear, 0, false)
    p.observeAndClaim(clear, 30_000, false)
    assertFalse(p.observeAndClaim(hot(), 40_001, true))
    assertTrue(p.observeAndClaim(hot(ThermalLevel.CRITICAL), 10, true))
  }
  @Test fun concurrentClaimsHaveExactlyOneOwner() {
    val p = ThermalNotificationEpisode()
    val count = java.util.concurrent.atomic.AtomicInteger()
    val threads = List(16) { Thread { if (p.observeAndClaim(hot(), 1, true)) count.incrementAndGet() } }
    threads.forEach { it.start() }; threads.forEach { it.join() }
    assertEquals(1, count.get())
  }
}
