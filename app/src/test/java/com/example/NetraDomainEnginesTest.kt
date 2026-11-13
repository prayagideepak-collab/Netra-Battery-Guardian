package com.example

import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.TemperatureZone
import com.example.domain.model.ThermalLevel
import com.example.domain.model.TrendStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NetraDomainEnginesTest {

  @Test
  fun `charging classifier calculates net battery wattage bands accurately`() {
    val classifier = ChargingSpeedClassifier()

    // 4.0 V * 2000 mA / 1000 = 8.0 W -> Standard
    val std = BatteryTelemetry(levelPercent = 40, isCharging = true, isPlugged = true, voltageMv = 4000, currentMa = 2000f)
    classifier.recordSample(std)
    val stdDiag = classifier.diagnose(std)
    assertEquals(ChargingSpeedClass.STANDARD, stdDiag.speedClass)

    classifier.clearSamples()
    // 4.2 V * 5000 mA / 1000 = 21.0 W -> Super Fast
    val superFast = BatteryTelemetry(levelPercent = 30, isCharging = true, isPlugged = true, voltageMv = 4200, currentMa = 5000f)
    classifier.recordSample(superFast)
    val sfDiag = classifier.diagnose(superFast)
    assertEquals(ChargingSpeedClass.SUPER_FAST, sfDiag.speedClass)

    classifier.clearSamples()
    // Discharging while plugged
    val drainWhilePlugged = BatteryTelemetry(levelPercent = 50, isCharging = false, isPlugged = true, voltageMv = 3900, currentMa = -650f)
    classifier.recordSample(drainWhilePlugged)
    val drainDiag = classifier.diagnose(drainWhilePlugged)
    assertEquals(ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED, drainDiag.speedClass)
  }

  @Test
  fun `temperature engine classifies 8 zones and 5 minute trend`() {
    val engine = TemperatureControlEngine()
    assertEquals(TemperatureZone.TOO_COLD, engine.classifyZone(-2f))
    assertEquals(TemperatureZone.COLD, engine.classifyZone(10f))
    assertEquals(TemperatureZone.NORMAL, engine.classifyZone(28f))
    assertEquals(TemperatureZone.WARM, engine.classifyZone(37f))
    assertEquals(TemperatureZone.HOT, engine.classifyZone(41f))
    assertEquals(TemperatureZone.VERY_HOT, engine.classifyZone(44f))
    assertEquals(TemperatureZone.CRITICAL, engine.classifyZone(47f))
    assertEquals(TemperatureZone.EMERGENCY, engine.classifyZone(51f))

    val t0 = 1_000_000L
    engine.recordTemperature(t0, 34.0f)
    engine.recordTemperature(t0 + 5 * 60_000L, 40.0f) // +1.2 C/min -> HIGH_HEAT_RISK
    val trend = engine.computeTrend(40.0f, t0 + 5 * 60_000L)
    assertEquals(TrendStatus.HIGH_HEAT_RISK, trend.status)
  }

  @Test
  fun `adaptive power tier drops one tier on thermal throttling`() {
    val tierEngine = AdaptivePowerTierEngine()
    val normalTelemetry = BatteryTelemetry(levelPercent = 80, isCharging = false, thermalLevel = ThermalLevel.NONE, temperatureCelsius = 30f)
    assertEquals(AdaptivePowerTier.NORMAL, tierEngine.evaluate(normalTelemetry, 1, 5).activeTier)

    val hotTelemetry = BatteryTelemetry(levelPercent = 80, isCharging = false, thermalLevel = ThermalLevel.MODERATE, temperatureCelsius = 41f)
    val hotBudget = tierEngine.evaluate(hotTelemetry, 1, 5)
    assertEquals(AdaptivePowerTier.SAVER, hotBudget.activeTier)
    assertEquals(0, hotBudget.networkCallsThisHour)
  }
}
