package com.example.domain.engine

import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.ChargingLimitingFactor
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.RollingPowerSample
import com.example.domain.model.ThermalLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Singleton
class ChargingSpeedClassifier @Inject constructor() {

  private val samples = ArrayDeque<RollingPowerSample>()

  @Synchronized
  fun recordSample(telemetry: BatteryTelemetry) {
    val now = telemetry.timestampMillis
    samples.addLast(
      RollingPowerSample(
        timestampMillis = now,
        voltageMv = telemetry.voltageMv,
        currentMa = telemetry.currentMa,
      )
    )
    // Keep 60 seconds rolling window
    while (samples.isNotEmpty() && now - samples.first().timestampMillis > 60_000L) {
      samples.removeFirst()
    }
  }

  @Synchronized
  fun clearSamples() {
    samples.clear()
  }

  fun diagnose(telemetry: BatteryTelemetry): ChargingDiagnosis {
    val window =
      synchronized(this) {
        if (samples.isEmpty()) {
          listOf(
            RollingPowerSample(
              telemetry.timestampMillis,
              telemetry.voltageMv,
              telemetry.currentMa,
            )
          )
        } else {
          samples.toList()
        }
      }

    val avgCurrentMa = window.map { it.currentMa }.average().toFloat()
    val avgVoltageV = window.map { it.voltageMv / 1000f }.average().toFloat()
    // Net battery charging power (W) = voltage (V) * current (mA) / 1000
    val netPowerWatts = (avgVoltageV * avgCurrentMa) / 1000f

    if (!telemetry.isPlugged) {
      val dischargeRateMa = abs(avgCurrentMa).coerceAtLeast(180f)
      val remainingMah = (telemetry.estimatedCapacityMah * (telemetry.levelPercent / 100f))
      val estimatedMin = ((remainingMah / dischargeRateMa) * 60f).roundToInt().coerceIn(5, 4320)
      return ChargingDiagnosis(
        speedClass = ChargingSpeedClass.NOT_CHARGING,
        netPowerWatts = netPowerWatts,
        rollingAvgCurrentMa = avgCurrentMa,
        estimatedMinutesRemaining = estimatedMin,
        isTimeEstimateApproximate = telemetry.chargeCounterUah <= 0L,
        limitingFactor = ChargingLimitingFactor.NONE,
        explanationEn = "Running on battery power.",
        explanationHi = "फ़ोन अभी बैटरी पावर पर चल रहा है।",
        whyEn = "Estimated backup uses your rolling 60-second battery drain average.",
        whyHi = "बचा हुआ समय पिछले 60 सेकंड की औसत बैटरी खपत से निकाला गया है।",
      )
    }

    // Plugged in, check if discharging despite charger connected
    if (avgCurrentMa < -50f || (!telemetry.isCharging && telemetry.levelPercent < 100 && netPowerWatts < 0f)) {
      return ChargingDiagnosis(
        speedClass = ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED,
        netPowerWatts = netPowerWatts,
        rollingAvgCurrentMa = avgCurrentMa,
        estimatedMinutesRemaining = null,
        isTimeEstimateApproximate = true,
        limitingFactor = ChargingLimitingFactor.HIGH_PHONE_USAGE,
        explanationEn = "Battery discharging despite charger connected. High phone usage is reducing effective charging speed.",
        explanationHi = "चार्जर लगा होने के बावजूद बैटरी घट रही है। ज़्यादा उपयोग के कारण चार्जिंग धीमी हो गई है।",
        whyEn = "Net current entering the battery is negative because device power draw exceeds adapter input.",
        whyHi = "फ़ोन की बिजली खपत चार्जर से आने वाली बिजली से ज़्यादा है, इसलिए बैटरी घट रही है।",
      )
    }

    if (!telemetry.isCharging && telemetry.levelPercent < 100) {
      return ChargingDiagnosis(
        speedClass = ChargingSpeedClass.SLOW,
        netPowerWatts = netPowerWatts.coerceAtLeast(0f),
        rollingAvgCurrentMa = avgCurrentMa,
        estimatedMinutesRemaining = null,
        isTimeEstimateApproximate = true,
        limitingFactor = ChargingLimitingFactor.CHARGING_PAUSED,
        explanationEn = "Charging paused or interrupted by thermal or system protection.",
        explanationHi = "गर्मी या सिस्टम सुरक्षा के कारण चार्जिंग रुकी हुई है।",
        whyEn = "Android BatteryManager reports plugged-in state without active charge flow.",
        whyHi = "सिस्टम ने तापमान या वोल्टेज सुरक्षा के लिए चार्जिंग अस्थायी रूप से रोकी है।",
      )
    }

    val positiveWatts = netPowerWatts.coerceAtLeast(0f)
    val speedClass =
      when {
        positiveWatts < 5.0f -> ChargingSpeedClass.SLOW
        positiveWatts < 10.0f -> ChargingSpeedClass.STANDARD
        positiveWatts < 18.0f -> ChargingSpeedClass.FAST
        positiveWatts < 30.0f -> ChargingSpeedClass.SUPER_FAST
        positiveWatts < 60.0f -> ChargingSpeedClass.ULTRA_FAST
        else -> ChargingSpeedClass.EXTREME_FAST
      }

    val limitingFactor =
      when {
        telemetry.temperatureCelsius >= 40f ||
          telemetry.thermalLevel.ordinal >= ThermalLevel.MODERATE.ordinal ->
          ChargingLimitingFactor.THERMAL_THROTTLING
        telemetry.levelPercent >= 85 && positiveWatts < 10.0f ->
          ChargingLimitingFactor.HIGH_BATTERY_LEVEL
        telemetry.screenOn && positiveWatts < 10.0f && telemetry.temperatureCelsius >= 35f ->
          ChargingLimitingFactor.HIGH_PHONE_USAGE
        positiveWatts < 5.0f && telemetry.levelPercent < 85 ->
          ChargingLimitingFactor.WEAK_CHARGER_OR_CABLE
        else -> ChargingLimitingFactor.NONE
      }

    val remainingPercent = (100 - telemetry.levelPercent).coerceAtLeast(0)
    val remainingMah = telemetry.estimatedCapacityMah * (remainingPercent / 100f)
    val effectiveChargeMa = avgCurrentMa.coerceAtLeast(250f)
    // Add modest CV trickle buffer above 80%
    val cvFactor = if (telemetry.levelPercent < 80) 1.12f else 1.35f
    val minutesToFull =
      if (remainingPercent == 0) 0
      else ((remainingMah / effectiveChargeMa) * 60f * cvFactor).roundToInt().coerceIn(1, 600)

    val (expEn, expHi) =
      when (limitingFactor) {
        ChargingLimitingFactor.THERMAL_THROTTLING ->
          "Thermal-limited charging: device is warm (${String.format("%.1f", telemetry.temperatureCelsius)}°C), so charging speed is reduced to protect the battery." to
            "तापमान अधिक (${String.format("%.1f", telemetry.temperatureCelsius)}°C) होने से बैटरी सुरक्षा के लिए चार्जिंग धीमी कर दी गई है।"
        ChargingLimitingFactor.HIGH_BATTERY_LEVEL ->
          "Slow trickle stage above 85% helps preserve long-term battery health." to
            "85% से ऊपर धीमी चार्जिंग बैटरी की उम्र सुरक्षित रखती है।"
        ChargingLimitingFactor.HIGH_PHONE_USAGE ->
          "High phone usage is reducing effective charging speed." to
            "फ़ोन के ज़्यादा उपयोग के कारण नेट चार्जिंग स्पीड कम हो गई है।"
        ChargingLimitingFactor.WEAK_CHARGER_OR_CABLE ->
          "Slow charging detected. Check cable fit, adapter wattage, or USB port." to
            "धीमी चार्जिंग का पता चला। केबल, एडाप्टर या पोर्ट की जाँच करें।"
        ChargingLimitingFactor.CHARGING_PAUSED ->
          "Charging paused temporarily." to "चार्जिंग अस्थायी रूप से रुकी हुई है।"
        ChargingLimitingFactor.NONE ->
          when (speedClass) {
            ChargingSpeedClass.SLOW ->
              "Slow / trickle charging at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर धीमी चार्जिंग चल रही है।"
            ChargingSpeedClass.STANDARD ->
              "Standard charging at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर सामान्य चार्जिंग चल रही है।"
            ChargingSpeedClass.FAST ->
              "Fast charging active at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर तेज़ चार्जिंग सक्रिय है।"
            ChargingSpeedClass.SUPER_FAST ->
              "Super fast charging active at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर सुपर फ़ास्ट चार्जिंग सक्रिय है।"
            ChargingSpeedClass.ULTRA_FAST ->
              "Ultra fast charging active at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर अल्ट्रा फ़ास्ट चार्जिंग सक्रिय है।"
            ChargingSpeedClass.EXTREME_FAST ->
              "Extreme fast charging active at ${String.format("%.1f", positiveWatts)} W." to
                "${String.format("%.1f", positiveWatts)} W पर एक्सट्रीम फ़ास्ट चार्जिंग सक्रिय है।"
            else -> "Charging active." to "चार्जिंग चालू है।"
          }
      }

    return ChargingDiagnosis(
      speedClass = speedClass,
      netPowerWatts = positiveWatts,
      rollingAvgCurrentMa = avgCurrentMa,
      estimatedMinutesRemaining = minutesToFull,
      isTimeEstimateApproximate = telemetry.chargeCounterUah <= 0L,
      limitingFactor = limitingFactor,
      explanationEn = expEn,
      explanationHi = expHi,
      whyEn = "Calculated from battery-side net power (Voltage × Rolling Current / 1000), excluding power consumed by screen and CPU.",
      whyHi = "यह गति बैटरी तक पहुँचने वाली वास्तविक नेट पावर (वोल्टेज × करंट) से निकाली गई है, न कि केवल चार्जर के वाट से।",
    )
  }
}
