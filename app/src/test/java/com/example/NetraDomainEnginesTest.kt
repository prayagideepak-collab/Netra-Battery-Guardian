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
  fun `charging speed does not use signed voltage times current`() {
    val classifier = ChargingSpeedClassifier()
    val t = BatteryTelemetry(levelPercent = 40, isCharging = true, isPlugged = true, voltageMv = 4200, currentMa = 5000f)
    classifier.recordSample(t)
    val diagnosis = classifier.diagnose(t)
    assertEquals(ChargingSpeedClass.UNAVAILABLE, diagnosis.speedClass)
    assertEquals(21f, diagnosis.netPowerWatts, 0.001f)
    assertEquals(true, diagnosis.estimatedStoredEnergyRateWatts.isNaN())
    val drain = t.copy(isCharging = false, currentMa = -650f)
    classifier.clearSamples()
    assertEquals(ChargingSpeedClass.UNAVAILABLE, classifier.diagnose(drain).speedClass)
    assertEquals(-2.73f, classifier.diagnose(drain).netPowerWatts, 0.001f)
  }

  @Test
  fun `temperature engine classifies 8 zones and 5 minute trend`() {
    val engine = TemperatureControlEngine()
    listOf(-2f,10f,28f,37f,41f,44f,47f,51f).forEach {
      assertEquals(TemperatureZone.UNAVAILABLE, engine.classifyZone(it))
    }
    assertEquals(TemperatureZone.CRITICAL, engine.classifyObserved(BatteryTelemetry(thermalLevel=ThermalLevel.CRITICAL)))
    assertEquals(TemperatureZone.HOT, engine.classifyObserved(BatteryTelemetry(thermalLevel=ThermalLevel.MODERATE)))

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
