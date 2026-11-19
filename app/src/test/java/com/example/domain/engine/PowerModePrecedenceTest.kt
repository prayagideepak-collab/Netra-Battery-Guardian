package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerModePrecedenceTest {
  @Test fun chargingAtTwentyPercentCannotBypassBatterySaver() {
    val result = AdaptivePowerTierEngine().evaluate(
      BatteryTelemetry(levelPercent = 20, isCharging = true, osBatterySaverOn = true), 0, 0,
    )
    assertEquals(AdaptivePowerTier.SAVER, result.activeTier)
  }

  @Test fun freshUnknownBatteryStartsConservativelyWithoutPretendingItsLevel() {
    val result = AdaptivePowerTierEngine().evaluate(BatteryTelemetry(), 0, 0)
    assertEquals(AdaptivePowerTier.CRITICAL, result.activeTier)
  }

  @Test fun survivalRecoversAtEightExceptForExactFiveOverride() {
    val engine = AdaptivePowerTierEngine()
    assertEquals(AdaptivePowerTier.SURVIVAL, engine.evaluate(BatteryTelemetry(levelPercent = 4), 0, 0).activeTier)
    assertEquals(AdaptivePowerTier.SURVIVAL, engine.evaluate(BatteryTelemetry(levelPercent = 7), 0, 0).activeTier)
    assertEquals(AdaptivePowerTier.CRITICAL, engine.evaluate(BatteryTelemetry(levelPercent = 8), 0, 0).activeTier)
    engine.evaluate(BatteryTelemetry(levelPercent = 4), 0, 0)
    assertEquals(AdaptivePowerTier.CRITICAL, engine.evaluate(BatteryTelemetry(levelPercent = 5), 0, 0).activeTier)
  }
}
