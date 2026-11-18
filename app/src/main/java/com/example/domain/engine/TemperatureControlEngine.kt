package com.example.domain.engine

import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.TemperatureTrendResult
import com.example.domain.model.TemperatureZone
import com.example.domain.model.TrendStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemperatureControlEngine @Inject constructor() {

  private data class TempSample(val timestampMillis: Long, val tempC: Float)

  private val recentSamples = ArrayDeque<TempSample>()

  @Synchronized
  fun recordTemperature(timestampMillis: Long, tempC: Float) {
    if (!tempC.isFinite()) return
    recentSamples.addLast(TempSample(timestampMillis, tempC))
    // Keep last 10 minutes
    while (recentSamples.isNotEmpty() && timestampMillis - recentSamples.first().timestampMillis > 600_000L) {
      recentSamples.removeFirst()
    }
  }

  fun classifyZone(
    tempC: Float,
    sensitivity: CoolingSensitivity = CoolingSensitivity.BALANCED,
  ): TemperatureZone {
    // A numeric sensor alone does not reveal this phone's thermal limits.
    return TemperatureZone.UNAVAILABLE
  }

  fun classifyObserved(t: com.example.domain.model.BatteryTelemetry): TemperatureZone = when {
    t.healthState == com.example.domain.model.BatteryHealthState.OVERHEAT -> TemperatureZone.CRITICAL
    t.healthState == com.example.domain.model.BatteryHealthState.COLD -> TemperatureZone.COLD
    t.thermalLevel == com.example.domain.model.ThermalLevel.SHUTDOWN ||
      t.thermalLevel == com.example.domain.model.ThermalLevel.EMERGENCY -> TemperatureZone.EMERGENCY
    t.thermalLevel == com.example.domain.model.ThermalLevel.CRITICAL -> TemperatureZone.CRITICAL
    t.thermalLevel == com.example.domain.model.ThermalLevel.SEVERE -> TemperatureZone.VERY_HOT
    t.thermalLevel == com.example.domain.model.ThermalLevel.MODERATE -> TemperatureZone.HOT
    t.thermalLevel == com.example.domain.model.ThermalLevel.LIGHT -> TemperatureZone.WARM
    else -> TemperatureZone.UNAVAILABLE // NONE is not a battery-specific safety certificate.
  }

  fun computeTrend(
    currentTempC: Float,
    currentTimeMillis: Long,
    sensitivity: CoolingSensitivity = CoolingSensitivity.BALANCED,
  ): TemperatureTrendResult {
    if (!currentTempC.isFinite()) return TemperatureTrendResult(Float.NaN, TrendStatus.UNAVAILABLE, "Unavailable: temperature was not reported.", "Unavailable: तापमान उपलब्ध नहीं है।")
    val targetTime = currentTimeMillis - 5 * 60_000L
    val baseline =
      synchronized(this) {
        recentSamples.minByOrNull { kotlin.math.abs(it.timestampMillis - targetTime) }
      }
    if (baseline == null || currentTimeMillis - baseline.timestampMillis < 45_000L) return TemperatureTrendResult(Float.NaN, TrendStatus.UNAVAILABLE, "Unavailable: collecting temperature observations.", "Unavailable: तापमान डेटा एकत्र हो रहा है।")
    val deltaPerMinute =
      if (baseline == null || currentTimeMillis - baseline.timestampMillis < 45_000L) {
        0f
      } else {
        val elapsedMinutes = ((currentTimeMillis - baseline.timestampMillis) / 60_000f).coerceAtLeast(1f)
        (currentTempC - baseline.tempC) / elapsedMinutes
      }

    val zone = classifyZone(currentTempC, sensitivity)
    return when {
      deltaPerMinute > 1.0f ->
        TemperatureTrendResult(
          deltaPerMinute = deltaPerMinute,
          status = TrendStatus.HIGH_HEAT_RISK,
          messageEn = "Observed temperature rising faster than 1°C per minute.",
          messageHi = "दर्ज तापमान प्रति मिनट 1°C से अधिक बढ़ रहा है।",
        )
      deltaPerMinute > 0.5f ->
        TemperatureTrendResult(
          deltaPerMinute = deltaPerMinute,
          status = TrendStatus.RISING_FAST,
          messageEn = "Temperature rising fast.",
          messageHi = "तापमान तेज़ी से बढ़ रहा है।",
        )
      deltaPerMinute < -0.25f ->
        TemperatureTrendResult(
          deltaPerMinute = deltaPerMinute,
          status = TrendStatus.COOLING_DOWN,
          messageEn = "Device is cooling down steadily.",
          messageHi = "फ़ोन धीरे-धीरे ठंडा हो रहा है।",
        )
      zone == TemperatureZone.WARM && kotlin.math.abs(deltaPerMinute) <= 0.25f ->
        TemperatureTrendResult(
          deltaPerMinute = deltaPerMinute,
          status = TrendStatus.WARM_STABLE,
          messageEn = "Warm but stable.",
          messageHi = "फ़ोन हल्का गर्म है, पर तापमान स्थिर है।",
        )
      else ->
        TemperatureTrendResult(
          deltaPerMinute = deltaPerMinute,
          status = TrendStatus.STABLE,
          messageEn = "Temperature is stable.",
          messageHi = "तापमान सामान्य और स्थिर है।",
        )
    }
  }
}
