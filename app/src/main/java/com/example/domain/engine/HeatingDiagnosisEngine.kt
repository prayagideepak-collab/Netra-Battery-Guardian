package com.example.domain.engine

import com.example.domain.model.AppPowerInsight
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.HeatCauseItem
import com.example.domain.model.HeatDiagnosisReport
import com.example.domain.model.TemperatureZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeatingDiagnosisEngine @Inject constructor(
  private val temperatureEngine: TemperatureControlEngine,
) {

  fun diagnose(
    telemetry: BatteryTelemetry,
    chargingDiagnosis: ChargingDiagnosis,
    topApps: List<AppPowerInsight>,
    sensitivity: CoolingSensitivity,
    coolingWorkflowActive: Boolean,
    coolingStepsAppliedEn: List<String>,
    coolingStepsAppliedHi: List<String>,
  ): HeatDiagnosisReport {
    val zone = temperatureEngine.classifyZone(telemetry.temperatureCelsius, sensitivity)
    val trend = temperatureEngine.computeTrend(telemetry.temperatureCelsius, telemetry.timestampMillis, sensitivity)

    val causes = mutableListOf<HeatCauseItem>()
    val actionsEn = mutableListOf<String>()
    val actionsHi = mutableListOf<String>()

    if (telemetry.isPlugged && telemetry.screenOn) {
      causes +=
        HeatCauseItem(
          titleEn = "Charging while using the phone",
          titleHi = "चार्जिंग के दौरान फ़ोन का उपयोग",
          detailEn = "Simultaneous charging conversion heat and screen/CPU load compound battery temperature.",
          detailHi = "चार्जिंग की गर्मी और स्क्रीन/प्रोसेसर का लोड एक साथ बैटरी का तापमान बढ़ाते हैं।",
        )
      actionsEn += "Avoid heavy use while charging."
      actionsHi += "चार्जिंग के समय भारी उपयोग या गेमिंग से बचें।"
    }

    if (telemetry.isCharging &&
      chargingDiagnosis.speedClass in
      listOf(
        ChargingSpeedClass.FAST,
        ChargingSpeedClass.SUPER_FAST,
        ChargingSpeedClass.ULTRA_FAST,
        ChargingSpeedClass.EXTREME_FAST,
      )
    ) {
      causes +=
        HeatCauseItem(
          titleEn = "High-wattage fast charging",
          titleHi = "तेज़ (हाई-वाट) चार्जिंग",
          detailEn = "Charging at ${String.format("%.1f", chargingDiagnosis.netPowerWatts)} W naturally raises cell temperature.",
          detailHi = "${String.format("%.1f", chargingDiagnosis.netPowerWatts)} W की तेज़ चार्जिंग से बैटरी हल्की गर्म होती है।",
        )
    }

    if (telemetry.screenOn && telemetry.screenBrightnessPercent >= 70) {
      causes +=
        HeatCauseItem(
          titleEn = "High screen brightness (${telemetry.screenBrightnessPercent}%)",
          titleHi = "अधिक स्क्रीन ब्राइटनेस (${telemetry.screenBrightnessPercent}%)",
          detailEn = "Display backlight and GPU rendering draw high current and radiate heat.",
          detailHi = "तेज़ ब्राइटनेस ज़्यादा बैटरी खींचती है और स्क्रीन को गर्म करती है।",
        )
      actionsEn += "Lower screen brightness or enable Adaptive Brightness."
      actionsHi += "स्क्रीन की ब्राइटनेस कम करें या ऑटो-ब्राइटनेस चालू करें।"
    }

    if (telemetry.hotspotLikelyActive) {
      causes +=
        HeatCauseItem(
          titleEn = "Mobile hotspot active",
          titleHi = "मोबाइल हॉटस्पॉट चालू है",
          detailEn = "Modem + Wi-Fi tethering continuously consumes high radio power.",
          detailHi = "हॉटस्पॉट चालू रहने से मॉडेम और वाई-फ़ाई चिप लगातार गर्म होते हैं।",
        )
      actionsEn += "Disable hotspot when not actively sharing data."
      actionsHi += "ज़्यादा गर्मी होने पर हॉटस्पॉट बंद करें।"
    }

    if (telemetry.poorCellularSignal) {
      causes +=
        HeatCauseItem(
          titleEn = "Weak cellular signal",
          titleHi = "कमज़ोर मोबाइल नेटवर्क सिग्नल",
          detailEn = "Modem boosts transmission power in low-signal areas, generating extra heat.",
          detailHi = "कमज़ोर सिग्नल में फ़ोन नेटवर्क पकड़ने के लिए ज़्यादा पावर खर्च करता है।",
        )
      actionsEn += "Switch to Wi-Fi or Airplane Mode briefly if signal is very weak."
      actionsHi += "संभव हो तो वाई-फ़ाई उपयोग करें या कम सिग्नल में डेटा बंद रखें।"
    }

    val activeGame = topApps.firstOrNull { it.isLikelyGame && it.foregroundMinutesToday >= 10 }
    if (activeGame != null) {
      causes +=
        HeatCauseItem(
          titleEn = "Heavy gaming / GPU load (${activeGame.appName})",
          titleHi = "भारी गेमिंग / प्रोसेसर लोड (${activeGame.appName})",
          detailEn = "Sustained 3D rendering raises SoC and battery surface heat.",
          detailHi = "लगातार गेम खेलने से प्रोसेसर और बैटरी दोनों गर्म होते हैं।",
        )
      actionsEn += "Pause heavy gaming for 10 minutes to let heat dissipate."
      actionsHi += "तापमान सामान्य करने के लिए 10 मिनट के लिए गेम रोकें।"
    }

    val locationHeavyApp = topApps.firstOrNull { it.hasLocationPermission && it.foregroundMinutesToday >= 5 }
    if (locationHeavyApp != null) {
      causes +=
        HeatCauseItem(
          titleEn = "Active location / navigation (${locationHeavyApp.appName})",
          titleHi = "लोकेशन / जीपीएस उपयोग (${locationHeavyApp.appName})",
          detailEn = "Continuous GPS sampling increases modem and sensor thermal output.",
          detailHi = "लगातार जीपीएस उपयोग से सेंसर और मॉडेम गर्म होते हैं।",
        )
      actionsEn += "Pause background location sampling if not navigating."
      actionsHi += "नेविगेशन न होने पर बैकग्राउंड लोकेशन सैंपलिंग रोकें।"
    }

    if (telemetry.healthState == BatteryHealthState.OVERHEAT || telemetry.healthState == BatteryHealthState.DEAD) {
      causes +=
        HeatCauseItem(
          titleEn = "Battery ageing or thermal fault signal",
          titleHi = "बैटरी की उम्र या हार्डवेयर गर्मी संकेत",
          detailEn = "Android BatteryManager reports elevated thermal health flag.",
          detailHi = "सिस्टम ने बैटरी हेल्थ में गर्मी का संकेत दिया है।",
        )
    }

    if (zone.ordinal >= TemperatureZone.VERY_HOT.ordinal && telemetry.isPlugged) {
      actionsEn.add(0, "Disconnect charger immediately until battery cools below 36°C.")
      actionsHi.add(0, "बैटरी 36°C से नीचे आने तक तुरंत चार्जर हटा दें।")
    }
    if (zone == TemperatureZone.EMERGENCY) {
      actionsEn.add(0, "Power off the device if temperature stays above 50°C and inspect charger/cable.")
      actionsHi.add(0, "यदि तापमान 50°C से ऊपर रहे तो फ़ोन बंद करें और चार्जर/बैटरी की जाँच कराएँ।")
    }
    if (zone == TemperatureZone.TOO_COLD) {
      causes +=
        HeatCauseItem(
          titleEn = "Sub-zero ambient temperature",
          titleHi = "शून्य से नीचे का ठंडा वातावरण",
          detailEn = "Charging below 0°C can damage lithium cells or run very slowly.",
          detailHi = "0°C से नीचे चार्जिंग धीमी या बैटरी के लिए असुरक्षित हो सकती है।",
        )
      actionsEn += "Let phone warm naturally to room temperature before fast charging."
      actionsHi += "चार्ज करने से पहले फ़ोन को सामान्य कमरे के तापमान पर आने दें।"
    }

    if (actionsEn.isEmpty() && zone.ordinal >= TemperatureZone.WARM.ordinal) {
      actionsEn += "Reduce background sync and screen brightness, and keep phone out of direct sunlight."
      actionsHi += "बैकग्राउंड सिंक और ब्राइटनेस कम करें और फ़ोन को सीधी धूप से दूर रखें।"
    }

    val confidence =
      when {
        causes.size >= 2 || (zone.ordinal >= TemperatureZone.HOT.ordinal && causes.isNotEmpty()) ->
          ConfidenceLevel.HIGH
        causes.size == 1 -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
      }

    return HeatDiagnosisReport(
      zone = zone,
      trend = trend,
      confidence = confidence,
      causes = causes,
      recommendedActionsEn = actionsEn.distinct(),
      recommendedActionsHi = actionsHi.distinct(),
      coolingWorkflowActive = coolingWorkflowActive,
      coolingStepsAppliedEn = coolingStepsAppliedEn,
      coolingStepsAppliedHi = coolingStepsAppliedHi,
    )
  }
}
