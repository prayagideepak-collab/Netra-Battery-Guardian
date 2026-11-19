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
  private val energyPolicy = EnergyCounterSpeedPolicy()
  private var energyInput: Triple<Long?, Long?, Boolean>? = null
  private var energyReading = EnergySpeedReading()

  private fun observeEnergy(t: BatteryTelemetry) {
    val charging = t.isPlugged && t.isCharging && t.batteryStatus != 5 && t.levelPercent != 100
    val input = Triple(t.energyCounterNwh, t.collectionStartedElapsedMillis, charging)
    if (input == energyInput) return
    energyInput = input
    energyReading = energyPolicy.observe(charging, t.energyCounterNwh, t.collectionStartedElapsedMillis)
  }

  @Synchronized fun recordSample(telemetry: BatteryTelemetry) {
    observeEnergy(telemetry)
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

  @Synchronized fun clearSamples() { samples.clear(); energyPolicy.reset(); energyInput = null; energyReading = EnergySpeedReading() }

  @Synchronized fun diagnose(telemetry: BatteryTelemetry): ChargingDiagnosis {
    observeEnergy(telemetry)
    // Independent signed battery-side V x I telemetry is preserved, never a speed input.
    val powerWindow = samples.filter { it.hasPower && telemetry.timestampMillis - it.timestampMillis in 0..60_000L }
      .ifEmpty { if (telemetry.hasPower) listOf(telemetry) else emptyList() }
    val avgCurrent = if (telemetry.hasPower && powerWindow.isNotEmpty()) powerWindow.map { it.currentMa }.average().toFloat() else Float.NaN
    val power = if (telemetry.hasPower && powerWindow.isNotEmpty()) powerWindow.map { it.instantaneousPowerWatts }.average().toFloat() else Float.NaN
    val speed = when {
      !telemetry.isPlugged -> ChargingSpeedClass.NOT_CHARGING
      !telemetry.isCharging || telemetry.batteryStatus == 5 || telemetry.levelPercent == 100 -> ChargingSpeedClass.UNAVAILABLE
      else -> when (energyReading.speed) {
        EnergySpeed.SLOW -> ChargingSpeedClass.SLOW
        EnergySpeed.NORMAL -> ChargingSpeedClass.STANDARD
        EnergySpeed.FAST -> ChargingSpeedClass.FAST
        EnergySpeed.ULTRA_FAST -> ChargingSpeedClass.ULTRA_FAST
        EnergySpeed.HYPER_FAST -> ChargingSpeedClass.HYPER_FAST
        EnergySpeed.EXTREME_FAST -> ChargingSpeedClass.EXTREME_FAST
        null -> ChargingSpeedClass.UNAVAILABLE
      }
    }
    val minutes = observedMinutes(telemetry)
    val explanation = if (!telemetry.isPlugged) "Running on battery power." else
      "Estimated net stored-energy rate from Android ENERGY_COUNTER over observed time, not charger output or directly measured charging-input watts. Unsupported/sparse data stays Unavailable; category needs 10 seconds of observed stability."
    val explanationHi = if (!telemetry.isPlugged) "फ़ोन बैटरी पर है।" else
      "Android ऊर्जा काउंटर से दर्ज समय में संचित नेट ऊर्जा की अनुमानित दर। यह चार्जर आउटपुट नहीं है। डेटा या स्थिरता कम होने पर Unavailable।"
    return ChargingDiagnosis(speed, power, avgCurrent, minutes, true, ChargingLimitingFactor.NONE,
      explanation, explanationHi,
      "Approximate time uses this session's observed battery-level change only (at least 2 minutes and 2 percentage points). It changes with use and phone-side taper. Unavailable until enough real observations; no fixed capacity, current floor or taper factor.",
      "अनुमान इस सत्र में दर्ज बैटरी स्तर के बदलाव से है (कम से कम 2 मिनट और 2 प्रतिशत अंक)। उपयोग और फ़ोन की चार्जिंग गति बदलने पर समय बदलेगा। पर्याप्त वास्तविक डेटा तक Unavailable।",
      estimatedStoredEnergyRateWatts = energyReading.watts?.toFloat() ?: Float.NaN)
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
