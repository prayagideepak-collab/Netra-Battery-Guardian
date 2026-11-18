package com.example.domain.engine

import com.example.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/** One observed session owns the estimate. No nominal battery, current floor or global taper. */
@Singleton
class ChargingSpeedClassifier @Inject constructor() {
  private val samples = ArrayDeque<BatteryTelemetry>()

  @Synchronized fun recordSample(telemetry: BatteryTelemetry) {
    val previous = samples.lastOrNull()
    if (previous != null && (previous.isPlugged != telemetry.isPlugged ||
        previous.isCharging != telemetry.isCharging || telemetry.timestampMillis <= previous.timestampMillis ||
        telemetry.timestampMillis - previous.timestampMillis > 5 * 60_000L ||
        (telemetry.isCharging && telemetry.levelPercent < previous.levelPercent) ||
        (!telemetry.isPlugged && telemetry.levelPercent > previous.levelPercent))) samples.clear()
    if (!telemetry.hasLevel) { samples.clear(); return }
    samples.addLast(telemetry)
    while (samples.isNotEmpty() && telemetry.timestampMillis - samples.first().timestampMillis > 30 * 60_000L) samples.removeFirst()
  }

  @Synchronized fun clearSamples() { samples.clear() }

  @Synchronized fun diagnose(telemetry: BatteryTelemetry): ChargingDiagnosis {
    val powerWindow = samples.filter { it.hasPower && telemetry.timestampMillis - it.timestampMillis in 0..60_000L }
      .ifEmpty { if (telemetry.hasPower) listOf(telemetry) else emptyList() }
    val avgCurrent = if (telemetry.hasPower && powerWindow.isNotEmpty()) powerWindow.map { it.currentMa }.average().toFloat() else Float.NaN
    val power = if (telemetry.hasPower && powerWindow.isNotEmpty()) powerWindow.map { it.instantaneousPowerWatts }.average().toFloat() else Float.NaN
    val speed = when {
      !telemetry.hasLevel -> ChargingSpeedClass.UNAVAILABLE
      !telemetry.isPlugged -> ChargingSpeedClass.NOT_CHARGING
      !power.isFinite() -> ChargingSpeedClass.UNAVAILABLE
      power < 0 -> ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED
      !telemetry.isCharging -> ChargingSpeedClass.UNAVAILABLE
      power < 5 -> ChargingSpeedClass.SLOW
      power < 10 -> ChargingSpeedClass.STANDARD
      power < 18 -> ChargingSpeedClass.FAST
      power < 30 -> ChargingSpeedClass.SUPER_FAST
      power < 60 -> ChargingSpeedClass.ULTRA_FAST
      else -> ChargingSpeedClass.EXTREME_FAST
    }
    val minutes = observedMinutes(telemetry)
    val explanation = when {
      !telemetry.hasLevel -> "Unavailable: battery level was not reported."
      telemetry.isPlugged && !telemetry.isCharging -> "Plugged in, not actively charging. Cause unavailable."
      !power.isFinite() -> "Power unavailable: current or voltage was not reported."
      power < 0 && telemetry.isPlugged -> "Observed net battery discharge while plugged in. Cause unavailable."
      telemetry.isCharging -> "Observed battery-side power. Phone-side taper may change the rate; charger or cable fault is not established."
      else -> "Running on battery power."
    }
    val explanationHi = when {
      !telemetry.hasLevel -> "Unavailable: बैटरी स्तर उपलब्ध नहीं है।"
      telemetry.isPlugged && !telemetry.isCharging -> "चार्जर जुड़ा है, लेकिन चार्जिंग सक्रिय नहीं है। कारण उपलब्ध नहीं है।"
      !power.isFinite() -> "Unavailable: करंट या वोल्टेज उपलब्ध नहीं है।"
      power < 0 && telemetry.isPlugged -> "चार्जर जुड़ा होने पर भी बैटरी में कमी दर्ज हुई। कारण उपलब्ध नहीं है।"
      telemetry.isCharging -> "बैटरी की वास्तविक नेट पावर। फ़ोन की चार्जिंग गति बदल सकती है; केबल या चार्जर की खराबी साबित नहीं है।"
      else -> "फ़ोन अभी बैटरी पावर पर चल रहा है।"
    }
    return ChargingDiagnosis(speed, power, avgCurrent, minutes, true, ChargingLimitingFactor.NONE,
      explanation, explanationHi,
      "Approximate time uses this session's observed battery-level change only (at least 2 minutes and 2 percentage points). It changes with use and phone-side taper. Unavailable until enough real observations; no fixed capacity, current floor or taper factor.",
      "अनुमान इस सत्र में दर्ज बैटरी स्तर के बदलाव से है (कम से कम 2 मिनट और 2 प्रतिशत अंक)। उपयोग और फ़ोन की चार्जिंग गति बदलने पर समय बदलेगा। पर्याप्त वास्तविक डेटा तक Unavailable।")
  }

  private fun observedMinutes(t: BatteryTelemetry): Int? {
    if (!t.hasLevel) return null
    if (t.isPlugged && t.isCharging && t.levelPercent == 100) return 0
    if (t.isPlugged && !t.isCharging) return null
    val window = samples.filter { t.timestampMillis - it.timestampMillis in 0..10 * 60_000L }
    if (window.size < 3 || window.last().timestampMillis != t.timestampMillis) return null
    val first = window.first()
    val elapsed = t.timestampMillis - first.timestampMillis
    val delta = t.levelPercent - first.levelPercent
    if (elapsed < 120_000L || abs(delta) < 2 || (t.isCharging && delta <= 0) || (!t.isPlugged && delta >= 0)) return null
    val remaining = if (t.isCharging) 100 - t.levelPercent else t.levelPercent
    val estimate = remaining.toDouble() / abs(delta) * elapsed / 60_000.0
    return estimate.takeIf { it.isFinite() && it in 0.0..4320.0 }?.roundToInt()
  }
}
