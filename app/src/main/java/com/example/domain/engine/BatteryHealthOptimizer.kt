package com.example.domain.engine

import com.example.data.local.db.BatteryHealthLogEntity
import com.example.data.local.db.DischargeSessionEntity
import com.example.data.local.prefs.UserPreferences
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppPowerInsight
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import com.example.service.DozeMonitorState
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class OptimizationPriority {
  URGENT,
  HIGH,
  MODERATE,
  GOOD,
}

enum class OptimizerActionTarget {
  BATTERY_OPTIMIZATION_SETTINGS,
  BATTERY_SAVER_SETTINGS,
  APP_INFO,
  LOCATION_SETTINGS,
  DATA_SAVER_SETTINGS,
  THERMAL_COOLING_MODE,
}

data class OptimizationRecommendation(
  val id: String,
  val priority: OptimizationPriority,
  val titleEn: String,
  val titleHi: String,
  val adviceEn: String,
  val adviceHi: String,
  val whyEn: String,
  val whyHi: String,
  val actionLabelEn: String,
  val actionLabelHi: String,
  val actionTarget: OptimizerActionTarget,
  val packageHint: String? = null,
)

data class BatteryOptimizationReport(
  val recommendations: List<OptimizationRecommendation> = emptyList(),
  val healthScore: Int = -1,
  val powerTier: AdaptivePowerTier = AdaptivePowerTier.NORMAL,
  val throttledDueToLowPower: Boolean = false,
  val evaluatedAtMillis: Long = 0L,
)

/**
 * Purely in-memory, zero-polling Battery Health Optimizer that generates actionable,
 * Android-compliant optimization advice (Doze mode settings, closing/restricting
 * high-consumption apps via App Info, cooling steps, and background data/location limits)
 * using already-collected telemetry and respecting ultra-low-power tiers and Doze state.
 */
@Singleton
class BatteryHealthOptimizer @Inject constructor() {

  fun analyze(
    telemetry: BatteryTelemetry,
    prefs: UserPreferences,
    powerBudget: PowerBudgetMetrics,
    dozeState: DozeMonitorState,
    topApps: List<AppPowerInsight>,
    latestHealthLog: BatteryHealthLogEntity? = null,
    recentDischarges: List<DischargeSessionEntity> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
  ): BatteryOptimizationReport {
    val isLowPowerThrottled =
      dozeState.isInDozeMode ||
        powerBudget.activeTier == AdaptivePowerTier.CRITICAL ||
        powerBudget.activeTier == AdaptivePowerTier.SURVIVAL

    val items = mutableListOf<OptimizationRecommendation>()

    // 1. High battery temperature advice (Tier-0 safety relevant)
    if (telemetry.healthState == com.example.domain.model.BatteryHealthState.OVERHEAT || telemetry.thermalLevel in setOf(com.example.domain.model.ThermalLevel.SEVERE, com.example.domain.model.ThermalLevel.CRITICAL, com.example.domain.model.ThermalLevel.EMERGENCY, com.example.domain.model.ThermalLevel.SHUTDOWN)) {

      val tempStr = com.example.domain.model.telemetryText(telemetry.temperatureCelsius, "°C")
      items += OptimizationRecommendation(
        id = "opt_thermal_cooling",
        priority = OptimizationPriority.URGENT,
        titleEn = "Reduce Device Temperature ($tempStr)",
        titleHi = "फ़ोन का तापमान कम करें ($tempStr)",
        adviceEn = "Battery is warm while ${if (telemetry.isCharging) "charging" else "active"}. Review Android thermal warnings and avoid heavy apps until the OS warning clears.",
        adviceHi = "बैटरी गर्म है। कूलिंग मोड चालू करें और OS चेतावनी समाप्त होने तक भारी ऐप्स बंद रखें।",
        whyEn = "Android reported thermal stress or battery overheat. This is not a measured OEM safe-temperature threshold.",
        whyHi = "Android ने थर्मल दबाव या बैटरी ओवरहीट की सूचना दी है।",
        actionLabelEn = "Activate Cooling Mode",
        actionLabelHi = "कूलिंग मोड चालू करें",
        actionTarget = OptimizerActionTarget.THERMAL_COOLING_MODE,
      )
    }

    // 2. Skip non-essential deep app/pattern heuristics when in Doze or SURVIVAL tier
    if (!dozeState.isInDozeMode && powerBudget.activeTier != AdaptivePowerTier.SURVIVAL) {
      // High-consumption apps (close / restrict safely via Android App Info)
      val heavyApp = topApps.firstOrNull {
        it.estimatedDrainSharePercent >= 20f || it.foregroundMinutesToday >= 30 || !it.isBatteryOptimized
      }
      if (heavyApp != null) {

        items += OptimizationRecommendation(
          id = "opt_heavy_app_${heavyApp.packageName}",
          priority = OptimizationPriority.HIGH,
          titleEn = "Close or Restrict ${heavyApp.appName}",
          titleHi = "${heavyApp.appName} को बंद या सीमित करें",
          adviceEn = "${heavyApp.appName} used ${heavyApp.foregroundMinutesToday}m today (foreground-use share ~${heavyApp.estimatedDrainSharePercent.toInt()}%, not measured battery drain). Open App Info to close it or set Battery usage to Restricted.",
          adviceHi = "${heavyApp.appName} ने आज ${heavyApp.foregroundMinutesToday} मिनट उपयोग किया (~${heavyApp.estimatedDrainSharePercent.toInt()}%)। ऐप जानकारी में इसे सीमित करें।",
          whyEn = "Manually closing or restricting high-consumption apps in Android settings cuts background wake-ups safely without root or fake task killers.",
          whyHi = "Android सेटिंग से भारी ऐप्स को सीमित करना सुरक्षित रूप से बैटरी बचाता है।",
          actionLabelEn = "Open App Info",
          actionLabelHi = "ऐप जानकारी खोलें",
          actionTarget = OptimizerActionTarget.APP_INFO,
          packageHint = heavyApp.packageName,
        )
      }

      // Doze mode & Battery Optimization posture check
      val hasAbnormalDrain = recentDischarges.any { it.abnormalDrainDetected }
      val hasUnrestrictedApps = topApps.any { !it.isBatteryOptimized }
      if ((!telemetry.osBatterySaverOn && !telemetry.isCharging && telemetry.hasLevel && telemetry.levelPercent <= 50) ||
        hasAbnormalDrain ||
        hasUnrestrictedApps
      ) {

        items += OptimizationRecommendation(
          id = "opt_doze_settings",
          priority = if (hasAbnormalDrain) OptimizationPriority.HIGH else OptimizationPriority.MODERATE,
          titleEn = "Optimize Doze & Background Restrictions",
          titleHi = "डोज़ मोड और बैकग्राउंड नियंत्रण सुधारें",
          adviceEn = if (hasAbnormalDrain) {
            "Recent screen-off drain spike detected. Review Battery Optimization settings so idle apps sleep immediately in Doze mode."
          } else {
            "Enable Battery Optimization for unused apps or toggle OS Battery Saver to maximize Doze idle savings."
          },
          adviceHi = "निष्क्रिय समय में बैटरी बचाने के लिए बैटरी ऑप्टिमाइज़ेशन और डोज़ सेटिंग्स की समीक्षा करें।",
          whyEn = "Allowing Android Doze to defer network and job wake-ups cuts overnight idle battery drain significantly.",
          whyHi = "डोज़ मोड में गैर-ज़रूरी बैकग्राउंड कार्य रुकने से रात भर की बैटरी खपत घटती है।",
          actionLabelEn = "Doze & Battery Settings",
          actionLabelHi = "डोज़ व बैटरी सेटिंग्स",
          actionTarget = if (hasUnrestrictedApps) {
            OptimizerActionTarget.BATTERY_OPTIMIZATION_SETTINGS
          } else {
            OptimizerActionTarget.BATTERY_SAVER_SETTINGS
          },
        )
      }

      // Location / poor cellular / hotspot drain check
      val locationHeavyApp = topApps.firstOrNull { it.hasLocationPermission && it.foregroundMinutesToday >= 15 }
      if (telemetry.hotspotLikelyActive || telemetry.poorCellularSignal || locationHeavyApp != null) {

        items += OptimizationRecommendation(
          id = "opt_radio_location",
          priority = OptimizationPriority.MODERATE,
          titleEn = "Trim Radio & Location Drain",
          titleHi = "नेटवर्क और लोकेशन खपत कम करें",
          adviceEn = when {
            telemetry.hotspotLikelyActive ->
              "Mobile hotspot is active and consumes heavy battery + heat. Disable hotspot when idle."
            telemetry.poorCellularSignal ->
              "Weak cellular signal detected; modem boost drains battery rapidly. Enable Data Saver or use Wi-Fi."
            else ->
              "${locationHeavyApp?.appName ?: "An app"} has location permission and recent foreground use. Background-location use is not verified; review its permission if needed."
          },
          adviceHi = "हॉटस्पॉट, कमज़ोर सिग्नल या बैकग्राउंड लोकेशन से बैटरी खपत बढ़ रही है; डेटा सेवर या लोकेशन सेटिंग सुधारें।",
          whyEn = "Cellular transmission and high-accuracy GPS polling are major off-screen power consumers.",
          whyHi = "कमज़ोर सिग्नल और जीपीएस पोलिंग स्क्रीन बंद होने पर भी अधिक बैटरी खर्च करते हैं।",
          actionLabelEn = if (telemetry.poorCellularSignal) "Data Saver Settings" else "Location Settings",
          actionLabelHi = if (telemetry.poorCellularSignal) "डेटा सेवर सेटिंग्स" else "लोकेशन सेटिंग्स",
          actionTarget = if (telemetry.poorCellularSignal) {
            OptimizerActionTarget.DATA_SAVER_SETTINGS
          } else {
            OptimizerActionTarget.LOCATION_SETTINGS
          },
        )
      }


    } else if (!telemetry.isCharging && telemetry.hasLevel && telemetry.levelPercent < 15) {

      items += OptimizationRecommendation(
        id = "opt_low_power_tier",
        priority = OptimizationPriority.URGENT,
        titleEn = "Low-Power ${powerBudget.activeTier.name} Posture Active",
        titleHi = "लो-पावर ${powerBudget.activeTier.name} स्थिति सक्रिय",
        adviceEn = "Battery is at ${telemetry.levelPercent}%. Non-essential diagnostics are paused; enable OS Battery Saver until you reach a charger.",
        adviceHi = "बैटरी ${telemetry.levelPercent}% पर है। गैर-जरूरी स्कैन रोके गए हैं; चार्जर मिलने तक बैटरी सेवर चालू रखें।",
        whyEn = "Critical & Survival tiers minimize CPU wake-ups and UI work to stretch remaining charge.",
        whyHi = "कम बैटरी स्तर पर गैर-जरूरी कार्य रोककर बैटरी बैकअप बढ़ाया जाता है।",
        actionLabelEn = "Battery Saver Settings",
        actionLabelHi = "बैटरी सेवर सेटिंग्स",
        actionTarget = OptimizerActionTarget.BATTERY_SAVER_SETTINGS,
      )
    }

    if (items.isEmpty()) {
      items += OptimizationRecommendation(
        id = "opt_all_good",
        priority = OptimizationPriority.GOOD,
        titleEn = if (dozeState.isInDozeMode) "Doze Mode Power Savings Active" else "Available Observations Reviewed",
        titleHi = if (dozeState.isInDozeMode) "डोज़ मोड पावर बचत सक्रिय" else "उपलब्ध माप की समीक्षा की गई",
        adviceEn = if (dozeState.isInDozeMode) {
          "Device is in Doze mode; Netra has paused all non-essential background tasks automatically."
        } else {
          "No additional advice from the available observations. Battery health and unobserved app activity remain Unavailable."
        },
        adviceHi = if (dozeState.isInDozeMode) {
          "डिवाइस डोज़ मोड में है; सभी गैर-जरूरी बैकग्राउंड कार्य रुके हुए हैं।"
        } else {
          "उपलब्ध डेटा की समीक्षा की गई। बैटरी स्वास्थ्य और अनदेखी गतिविधि अनुपलब्ध हैं।"
        },
        whyEn = "Adaptive power tiers keep monitoring lightweight and only surface actions when needed.",
        whyHi = "अनुकूली पावर टियर केवल आवश्यकता होने पर ही सुझाव दिखाते हैं।",
        actionLabelEn = "Review Battery Settings",
        actionLabelHi = "बैटरी सेटिंग्स देखें",
        actionTarget = OptimizerActionTarget.BATTERY_OPTIMIZATION_SETTINGS,
      )
    }

    return BatteryOptimizationReport(
      recommendations = items,
      healthScore = -1,
      powerTier = powerBudget.activeTier,
      throttledDueToLowPower = isLowPowerThrottled,
      evaluatedAtMillis = nowMillis,
    )
  }
}
