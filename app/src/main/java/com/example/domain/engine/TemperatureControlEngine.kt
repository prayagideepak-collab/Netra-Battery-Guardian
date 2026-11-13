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
    val offset =
      when (sensitivity) {
        CoolingSensitivity.RELAXED -> 2.0f
        CoolingSensitivity.BALANCED -> 0.0f
        CoolingSensitivity.AGGRESSIVE -> -2.0f
      }
    return when {
      tempC < 0f -> TemperatureZone.TOO_COLD
      tempC < 16f -> TemperatureZone.COLD
      tempC >= 50f -> TemperatureZone.EMERGENCY
      tempC >= 46f -> TemperatureZone.CRITICAL
      tempC >= (43f + offset) -> TemperatureZone.VERY_HOT
      tempC >= (40f + offset) -> TemperatureZone.HOT
      tempC >= (36f + offset) -> TemperatureZone.WARM
      else -> TemperatureZone.NORMAL
    }
  }

  fun computeTrend(
    currentTempC: Float,
    currentTimeMillis: Long,
    sensitivity: CoolingSensitivity = CoolingSensitivity.BALANCED,
  ): TemperatureTrendResult {
    val targetTime = currentTimeMillis - 5 * 60_000L
    val baseline =
      synchronized(this) {
        recentSamples.minByOrNull { kotlin.math.abs(it.timestampMillis - targetTime) }
      }
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
          messageEn = "High heat risk detected.",
          messageHi = "तेज़ी से तापमान बढ़ने का गंभीर ख़तरा है।",
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
