package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryModePolicyTest {
  private fun reduce(previous: AdaptivePowerTier, level: Int, charging: Boolean = false) =
    BatteryModePolicy.reduce(previous, BatteryTelemetry(levelPercent = level, isCharging = charging))

  @Test fun initialBoundariesMatchApprovedMatrix() {
    val expected = mapOf(
      0 to AdaptivePowerTier.SURVIVAL, 4 to AdaptivePowerTier.SURVIVAL,
      5 to AdaptivePowerTier.CRITICAL, 6 to AdaptivePowerTier.CRITICAL,
      8 to AdaptivePowerTier.CRITICAL, 14 to AdaptivePowerTier.CRITICAL,
      15 to AdaptivePowerTier.SAVER, 18 to AdaptivePowerTier.SAVER,
      30 to AdaptivePowerTier.SAVER, 31 to AdaptivePowerTier.NORMAL,
      33 to AdaptivePowerTier.NORMAL, 100 to AdaptivePowerTier.NORMAL,
    )
    for ((level, tier) in expected) assertEquals("level=$level", tier, reduce(AdaptivePowerTier.NORMAL, level))
  }

  @Test fun recoveryThresholdsAndExactFiveAreExplicit() {
    assertEquals(AdaptivePowerTier.SURVIVAL, reduce(AdaptivePowerTier.SURVIVAL, 7))
    assertEquals(AdaptivePowerTier.CRITICAL, reduce(AdaptivePowerTier.SURVIVAL, 8))
    assertEquals(AdaptivePowerTier.CRITICAL, reduce(AdaptivePowerTier.SURVIVAL, 5))
    assertEquals(AdaptivePowerTier.CRITICAL, reduce(AdaptivePowerTier.CRITICAL, 5))
    assertEquals(AdaptivePowerTier.CRITICAL, reduce(AdaptivePowerTier.CRITICAL, 17))
    assertEquals(AdaptivePowerTier.SAVER, reduce(AdaptivePowerTier.CRITICAL, 18))
    assertEquals(AdaptivePowerTier.SAVER, reduce(AdaptivePowerTier.SAVER, 32))
    assertEquals(AdaptivePowerTier.NORMAL, reduce(AdaptivePowerTier.SAVER, 33))
    assertEquals(AdaptivePowerTier.CRITICAL, reduce(AdaptivePowerTier.SURVIVAL, 16))
    assertEquals(AdaptivePowerTier.SAVER, reduce(AdaptivePowerTier.CRITICAL, 31))
  }

  @Test fun chargingNeverRelaxesBatteryRestriction() {
    for (previous in AdaptivePowerTier.entries) for (level in 0..100) {
      assertEquals(reduce(previous, level, false), reduce(previous, level, true))
    }
  }

  @Test fun batterySaverCannotBeBypassedAtAnyChargeLevel() {
    for (previous in AdaptivePowerTier.entries) for (level in 0..100) for (charging in listOf(false, true)) {
      val sample = BatteryTelemetry(levelPercent = level, isCharging = charging, osBatterySaverOn = true)
      val battery = BatteryModePolicy.reduce(previous, sample)
      val result = BatteryModePolicy.systemFloor(battery, sample)
      assertTrue("level=$level charging=$charging", result.ordinal >= AdaptivePowerTier.SAVER.ordinal)
      assertTrue(result.ordinal >= battery.ordinal)
    }
  }

  @Test fun unknownBatteryRetainsRestrictionAndStillHonorsSaver() {
    for (previous in AdaptivePowerTier.entries) for (level in listOf(-1, -999, 101, Int.MAX_VALUE)) {
      val sample = BatteryTelemetry(levelPercent = level, osBatterySaverOn = true)
      assertEquals(previous, BatteryModePolicy.reduce(previous, sample))
      assertTrue(BatteryModePolicy.systemFloor(previous, sample).ordinal >= previous.ordinal)
    }
  }

  @Test fun repeatedSamplesAreStableWithoutHysteresisRatchet() {
    for (start in AdaptivePowerTier.entries) for (level in 0..100) {
      val once = reduce(start, level)
      assertEquals("start=$start level=$level", once, reduce(once, level))
    }
  }

  @Test fun fallingBatteryNeverPromotesMode() {
    for (start in AdaptivePowerTier.entries) {
      var current = start
      for (level in 100 downTo 0) {
        val next = reduce(current, level)
        if (level < 100) assertTrue("level=$level", next.ordinal >= current.ordinal)
        current = next
      }
    }
  }

  @Test fun saverRemovalDoesNotPolluteBatteryHysteresis() {
    val sample = BatteryTelemetry(levelPercent = 80, osBatterySaverOn = true)
    val battery = BatteryModePolicy.reduce(AdaptivePowerTier.NORMAL, sample)
    assertEquals(AdaptivePowerTier.NORMAL, battery)
    assertEquals(AdaptivePowerTier.SAVER, BatteryModePolicy.systemFloor(battery, sample))
    assertEquals(AdaptivePowerTier.NORMAL, BatteryModePolicy.systemFloor(battery, sample.copy(osBatterySaverOn = false)))
  }
}
