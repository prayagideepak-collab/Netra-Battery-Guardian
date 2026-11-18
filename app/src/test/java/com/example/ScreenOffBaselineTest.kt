package com.example

import com.example.domain.engine.shouldSeedScreenOffBaseline
import com.example.domain.model.BatteryTelemetry
import org.junit.Assert.*
import org.junit.Test

class ScreenOffBaselineTest {
  @Test fun missingSampleCanBeReseededWhileScreenRemainsOff() {
    val unknown = BatteryTelemetry(screenOn = false)
    assertFalse(shouldSeedScreenOffBaseline(unknown, -1, true))
    assertTrue(shouldSeedScreenOffBaseline(unknown.copy(levelPercent = 60), -1, false))
    assertFalse(shouldSeedScreenOffBaseline(unknown.copy(levelPercent = 59), 60, false))
    assertFalse(shouldSeedScreenOffBaseline(unknown.copy(levelPercent = 60, isPlugged = true), -1, false))
    assertFalse(shouldSeedScreenOffBaseline(unknown.copy(levelPercent = 60, screenOn = true), -1, true))
  }
}
