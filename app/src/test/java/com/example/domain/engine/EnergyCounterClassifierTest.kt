package com.example.domain.engine

import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class EnergyCounterClassifierTest {
  private fun point(second: Long, watts: Long = 9) = BatteryTelemetry(
    timestampMillis = second * 1000, collectionStartedElapsedMillis = second * 1000,
    levelPercent = 45, batteryStatus = 2, isPlugged = true, isCharging = true,
    energyCounterNwh = 1_000_000_000L + second * watts * 2_500_000L / 9,
    voltageMv = 4000, currentMa = -200f,
  )
  @Test fun counterOwnsSpeedWhileSignedPowerRemainsIndependent() {
    val c = ChargingSpeedClassifier()
    for(i in 0L..20L) { c.recordSample(point(i)); c.diagnose(point(i)) }
    val d = c.diagnose(point(20))
    assertEquals(ChargingSpeedClass.STANDARD, d.speedClass)
    assertEquals(9f,d.estimatedStoredEnergyRateWatts,0.001f)
    assertEquals(-0.8f,d.netPowerWatts,0.001f)
  }
  @Test fun stableCategoryChangedByNewDataDoesNotRetainOldBand() {
    val c = ChargingSpeedClassifier()
    for(i in 0L..20L) c.recordSample(point(i))
    assertEquals(ChargingSpeedClass.STANDARD,c.diagnose(point(20)).speedClass)
    val changed = point(21).copy(energyCounterNwh = 1_150_000_000L)
    c.recordSample(changed)
    assertEquals(ChargingSpeedClass.UNAVAILABLE,c.diagnose(changed).speedClass)
  }
  @Test fun fullPausedUnpluggedAndRestartCannotSpeakSpeed() {
    for (end in listOf(point(21).copy(batteryStatus = 5),point(21).copy(levelPercent = 100),
      point(21).copy(isCharging = false), point(21).copy(energyCounterNwh = null))) {
      val c = ChargingSpeedClassifier()
      for(i in 0L..20L)c.recordSample(point(i))
      assertEquals(ChargingSpeedClass.UNAVAILABLE,c.diagnose(end).speedClass)
      assertTrue(c.diagnose(end).estimatedStoredEnergyRateWatts.isNaN())
    }
    assertEquals(ChargingSpeedClass.NOT_CHARGING,ChargingSpeedClassifier().diagnose(point(0).copy(isPlugged = false)).speedClass)
    assertEquals(ChargingSpeedClass.UNAVAILABLE,ChargingSpeedClassifier().diagnose(point(20)).speedClass)
  }
  @Test fun noCounterDoesNotUseVoltageTimesCurrentAtAnyPower() {
    for(current in listOf(100f,2000f,5000f,25000f,-1000f)) {
      val t = point(0).copy(currentMa=current, energyCounterNwh=null)
      assertEquals(ChargingSpeedClass.UNAVAILABLE,ChargingSpeedClassifier().diagnose(t).speedClass)
    }
  }
  @Test fun unchangedCounterInvalidatesPreviouslyStableSpeed() {
    val c=ChargingSpeedClassifier()
    for(i in 0L..20L)c.recordSample(point(i))
    val unchanged=point(21).copy(energyCounterNwh=point(20).energyCounterNwh)
    assertEquals(ChargingSpeedClass.UNAVAILABLE,c.diagnose(unchanged).speedClass)
  }
}
