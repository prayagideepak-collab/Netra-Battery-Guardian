package com.example.domain.engine

import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.prefs.UserPreferences
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.StorageInspector
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.model.AppPowerInsight
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.GuardianProfile
import com.example.domain.model.LocationSaverMode
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.domain.model.TemperatureZone
import com.example.service.DozeModeMonitor
import com.example.service.NotificationHelper
import com.example.voice.ChargingVoiceAssistant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class RuleEvaluationOutput(
  val activeProfile: GuardianProfile,
  val coolingWorkflowActive: Boolean,
  val coolingStepsAppliedEn: List<String>,
  val coolingStepsAppliedHi: List<String>,
  val temperatureZone: TemperatureZone,
)

@Singleton
class AutomationRulesEngine @Inject constructor(
  private val temperatureEngine: TemperatureControlEngine,
  private val chargingClassifier: ChargingSpeedClassifier,
  private val historyRepository: BatteryHistoryRepository,
  private val preferencesRepository: PreferencesRepository,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val storageInspector: StorageInspector,
  private val voiceAssistant: ChargingVoiceAssistant,
  private val notificationHelper: NotificationHelper,
  private val dozeModeMonitor: DozeModeMonitor,
) {
  private var lastPluggedState: Boolean? = null
  private var lastAnnouncedChargeLevel: Int = -1
  private var lastAnnouncedDischargeLevel: Int = -1
  private var lastScreenOnState: Boolean? = null
  private var lastProfile: GuardianProfile? = null
  private var lastZone: TemperatureZone? = null
  private var lastLowBatteryAlertLevel: Int = -1
  private var lastSnapshotSavedMillis: Long = 0L
  private val evalMutex = Mutex()

  suspend fun evaluate(
    telemetry: BatteryTelemetry,
    prefs: UserPreferences,
    topApps: List<AppPowerInsight>,
    forceSnapshot: Boolean = false,
    dbBatchIntervalSeconds: Int = 60,
  ): RuleEvaluationOutput = evalMutex.withLock {
    chargingClassifier.recordSample(telemetry)
    temperatureEngine.recordTemperature(telemetry.timestampMillis, telemetry.temperatureCelsius)

    val chargingDiagnosis: ChargingDiagnosis = chargingClassifier.diagnose(telemetry)
    val zone = temperatureEngine.classifyZone(telemetry.temperatureCelsius, prefs.coolingSensitivity)

    // 1. Charger connect / disconnect rules
    val prevPlugged = lastPluggedState
    if (prevPlugged != null && prevPlugged != telemetry.isPlugged) {
      if (telemetry.isPlugged) {
        chargingClassifier.clearSamples()
        chargingClassifier.recordSample(telemetry)
        lastAnnouncedDischargeLevel = -1
        voiceAssistant.announceChargerConnected(
          speedClass = chargingDiagnosis.speedClass,
          prefs = prefs,
          screenOn = telemetry.screenOn,
        )
        historyRepository.startOrUpdateChargingSession(
          telemetry = telemetry,
          speedClass = chargingDiagnosis.speedClass,
          powerWatts = chargingDiagnosis.netPowerWatts,
        )
        historyRepository.logActivity(
          category = LogCategory.BATTERY,
          severity = LogSeverity.INFO,
          titleEn = "Charger Connected (${chargingDiagnosis.speedClass.name})",
          titleHi = "चार्जर जुड़ा (${chargingDiagnosis.speedClass.name})",
          detailEn = chargingDiagnosis.explanationEn,
          detailHi = chargingDiagnosis.explanationHi,
          whyEn = "Detected via Android battery plug event; monitoring net battery wattage.",
          whyHi = "Android बैटरी प्लग इवेंट से पता चला; वास्तविक नेट वाट की निगरानी शुरू की गई।",
        )
      } else {
        voiceAssistant.announceChargerDisconnected(prefs = prefs, screenOn = telemetry.screenOn)
        lastAnnouncedChargeLevel = -1
        historyRepository.closeOpenChargingSession(telemetry)
        historyRepository.startOrUpdateDischargeSession(telemetry = telemetry)
        historyRepository.logActivity(
          category = LogCategory.BATTERY,
          severity = LogSeverity.INFO,
          titleEn = "Charger Disconnected",
          titleHi = "चार्जर हटाया गया",
          detailEn = "Switched to battery mode at ${batteryLevelText(telemetry.levelPercent)}.",
          detailHi = "${batteryLevelText(telemetry.levelPercent)} पर बैटरी मोड सक्रिय हुआ।",
          whyEn = "Battery mode tracks screen-on/off drain and protects against background spikes.",
          whyHi = "बैटरी मोड स्क्रीन ऑन/ऑफ़ खपत की निगरानी करता है और बैकग्राउंड ड्रेन रोकता है।",
        )
      }
    } else if (telemetry.isPlugged) {
      historyRepository.startOrUpdateChargingSession(
        telemetry = telemetry,
        speedClass = chargingDiagnosis.speedClass,
        powerWatts = chargingDiagnosis.netPowerWatts,
      )
    } else {
      historyRepository.startOrUpdateDischargeSession(telemetry = telemetry)
    }
    lastPluggedState = telemetry.isPlugged

    // 2. Charging C5..C100 (only while charging and screen is on) and Discharging D100..D1 milestones
    val step = prefs.announcementStepPercent.coerceIn(1, 25)
    if (telemetry.hasLevel && telemetry.isCharging && telemetry.isPlugged) {
      val hitBoundary = telemetry.levelPercent == 100 || (telemetry.levelPercent > 0 && telemetry.levelPercent % step == 0)
      if (hitBoundary && telemetry.levelPercent != lastAnnouncedChargeLevel && telemetry.screenOn) {
        lastAnnouncedChargeLevel = telemetry.levelPercent
        voiceAssistant.announceChargingMilestone(
          level = telemetry.levelPercent,
          prefs = prefs,
          screenOn = telemetry.screenOn,
          isCharging = true,
        )
      }
    } else if (telemetry.hasLevel && !telemetry.isPlugged) {
      val hitBoundary =
        telemetry.levelPercent == 1 ||
          telemetry.levelPercent == 100 ||
          (telemetry.levelPercent > 0 && telemetry.levelPercent % step == 0)
      if (hitBoundary && telemetry.levelPercent != lastAnnouncedDischargeLevel) {
        lastAnnouncedDischargeLevel = telemetry.levelPercent
        voiceAssistant.announceDischargingMilestone(telemetry.levelPercent, prefs, telemetry.screenOn)
      }
    }

    // 3. Low battery alerts at 20%, 10%, 5%, 1%
    if (telemetry.hasLevel && !telemetry.isCharging && prefs.lowBatteryAlertsEnabled) {
      val thresholdHit =
        when (telemetry.levelPercent) {
          1, 5, 10, 20 -> telemetry.levelPercent
          else -> -1
        }
      if (thresholdHit != -1 && thresholdHit != lastLowBatteryAlertLevel) {
        lastLowBatteryAlertLevel = thresholdHit
        val isCritical = thresholdHit <= 5
        val title =
          if (isCritical) "Critical Battery: $thresholdHit%" else "Low Battery Advisory: $thresholdHit%"
        val body =
          if (isCritical) "Battery at $thresholdHit%. Enable Ultra Saver and connect charger soon."
          else "Battery reached $thresholdHit%. Battery Saver profile activated."
        if (isCritical) {
          notificationHelper.postCriticalAlert(NotificationHelper.ID_CHARGING_ADVISORY, title, body)
        } else {
          notificationHelper.postAdvisory(NotificationHelper.ID_CHARGING_ADVISORY, title, body)
        }
      }
    } else if (telemetry.isCharging) {
      lastLowBatteryAlertLevel = -1
    }

    // 4. Thermal control & 5-min cool-down recovery below 35°C
    val now = telemetry.timestampMillis
    if (telemetry.temperatureCelsius < 35.0f) {
      if (prefs.below35SinceMillis == 0L) {
        preferencesRepository.setBelow35Since(now)
      } else if (now - prefs.below35SinceMillis >= 5 * 60_000L && lastZone != null && lastZone!!.ordinal >= TemperatureZone.WARM.ordinal) {
        voiceAssistant.announceThermalSafety(
          key = "normal_temp_restored",
          messageEn = "Normal temperature restored.",
          messageHi = "सामान्य तापमान वापस आ गया है।",
          messageHinglish = "Normal temperature wapas aa gaya hai.",
          prefs = prefs,
          urgent = false,
          screenOn = telemetry.screenOn,
        )
        historyRepository.logActivity(
          category = LogCategory.HEAT,
          severity = LogSeverity.INFO,
          titleEn = "Normal Temperature Restored",
          titleHi = "सामान्य तापमान बहाल",
          detailEn = "Battery stayed below 35°C for 5 minutes; Normal profile restored.",
          detailHi = "बैटरी 5 मिनट तक 35°C से नीचे रही; सामान्य प्रोफ़ाइल बहाल की गई।",
          whyEn = "Hysteresis ensures Cooling Mode does not toggle prematurely.",
          whyHi = "5 मिनट की पुष्टि से कूलिंग मोड बार-बार चालू-बंद नहीं होता।",
        )
      }
    } else if (prefs.below35SinceMillis != 0L) {
      preferencesRepository.setBelow35Since(0L)
    }

    val thermalCooling = zone != TemperatureZone.UNAVAILABLE && zone.ordinal >= TemperatureZone.HOT.ordinal
    val manualCooling = prefs.manualProfileOverride == GuardianProfile.COOLING
    val coolingActive = thermalCooling || manualCooling
    val knownCoolingState: Boolean? = when {
      coolingActive -> true
      zone == TemperatureZone.UNAVAILABLE -> null
      else -> false
    }
    // This is the sole cooling decision. The collector delegates to one persistent settings writer.
    val brightnessApplied = telemetryCollector.reconcileCoolingBrightness(knownCoolingState, if (manualCooling) 90 else 95)
    val coolingStepsEn = mutableListOf<String>()
    val coolingStepsHi = mutableListOf<String>()

    if (coolingActive) {
      if (brightnessApplied) {
        coolingStepsEn += "Cooling brightness applied with saved original settings."
        coolingStepsHi += "मूल सेटिंग सहेजकर कूलिंग ब्राइटनेस लागू की गई।"
      } else {
        coolingStepsEn += "Brightness not changed: consent, permission or settings ownership may be unavailable."
        coolingStepsHi += "ब्राइटनेस नहीं बदली: सहमति, अनुमति या सेटिंग नियंत्रण उपलब्ध नहीं है।"
      }

      if (prefs.locationSaverMode != LocationSaverMode.NORMAL && !prefs.emergencyLocationException) {
        coolingStepsEn += "Location sampling paused under Location Saver."
        coolingStepsHi += "लोकेशन सेवर के अंतर्गत लोकेशन सैंपलिंग रोकी गई।"
      }

      coolingStepsEn += "Thermal monitoring heightened; advised pausing background sync & heavy apps."
      coolingStepsHi += "थर्मल निगरानी बढ़ाई गई; बैकग्राउंड सिंक और भारी ऐप्स रोकने की सलाह।"
    }

    if (lastZone != zone) {
      when (zone) {
        TemperatureZone.EMERGENCY -> {
          val msgEn = "Emergency battery temperature ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}! Disconnect charger immediately and power off if heat persists."
          val msgHi = "आपातकालीन बैटरी तापमान ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}! तुरंत चार्जर हटाएँ और फ़ोन बंद करें।"
          notificationHelper.postCriticalAlert(NotificationHelper.ID_THERMAL_ALERT, "Emergency Heat Alert", msgEn)
          voiceAssistant.announceThermalSafety("temp_emergency", msgEn, msgHi, msgEn, prefs, urgent = true, screenOn = telemetry.screenOn)
        }
        TemperatureZone.CRITICAL -> {
          val msgEn = "Critical battery temperature ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}. Disconnect charger immediately and let phone cool."
          val msgHi = "गंभीर तापमान ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}। तुरंत चार्जर हटाएँ और फ़ोन को ठंडा होने दें।"
          notificationHelper.postCriticalAlert(NotificationHelper.ID_THERMAL_ALERT, "Critical Temperature Alert", msgEn)
          voiceAssistant.announceThermalSafety("temp_critical", msgEn, msgHi, msgEn, prefs, urgent = true, screenOn = telemetry.screenOn)
        }
        TemperatureZone.VERY_HOT -> {
          val msgEn = "Phone is very hot (${telemetryText(telemetry.temperatureCelsius, "°C", 1)}). Strong cooling active; close heavy apps and hotspot."
          val msgHi = "फ़ोन बहुत गर्म है (${telemetryText(telemetry.temperatureCelsius, "°C", 1)})। भारी ऐप्स और हॉटस्पॉट बंद करें।"
          notificationHelper.postAdvisory(NotificationHelper.ID_THERMAL_ALERT, "Very Hot Device", msgEn)
        }
        TemperatureZone.HOT -> {
          val msgEn = "Cooling Mode activated at ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}."
          notificationHelper.postAdvisory(NotificationHelper.ID_THERMAL_ALERT, "Cooling Mode Active", msgEn)
        }
        else -> Unit
      }
      if (zone != TemperatureZone.UNAVAILABLE && zone.ordinal >= TemperatureZone.HOT.ordinal || zone == TemperatureZone.TOO_COLD) {
        historyRepository.logActivity(
          category = LogCategory.HEAT,
          severity = if (zone.ordinal >= TemperatureZone.CRITICAL.ordinal) LogSeverity.CRITICAL else LogSeverity.WARNING,
          titleEn = "Thermal Zone: ${zone.name}",
          titleHi = "तापमान स्थिति: ${zone.name}",
          detailEn = "Battery temperature measured at ${telemetryText(telemetry.temperatureCelsius, "°C", 1)}.",
          detailHi = "बैटरी का तापमान ${telemetryText(telemetry.temperatureCelsius, "°C", 1)} दर्ज किया गया।",
          whyEn = "Automatic thermal protection monitors battery °C to prevent heat wear.",
          whyHi = "स्वचालित थर्मल सुरक्षा बैटरी को अधिक गर्मी से होने वाले नुकसान से बचाती है।",
        )
      }
    }
    lastZone = zone

    // 5. Determine Active Guardian Profile
    val isNight = voiceAssistant.isNightSilentActive(prefs, now)
    val isGaming = topApps.any { it.isLikelyGame && it.foregroundMinutesToday >= 15 } && telemetry.screenOn
    val activeProfile =
      when {
        coolingActive -> GuardianProfile.COOLING
        telemetry.hasLevel && !telemetry.isCharging && telemetry.levelPercent < 10 -> GuardianProfile.ULTRA_BATTERY_SAVER
        telemetry.hasLevel && !telemetry.isCharging && telemetry.levelPercent <= prefs.batterySaverThreshold -> GuardianProfile.BATTERY_SAVER
        telemetry.isPlugged -> GuardianProfile.CHARGING
        prefs.manualProfileOverride != null -> prefs.manualProfileOverride
        isNight -> GuardianProfile.SLEEP
        prefs.isTripActive || prefs.locationSaverMode == LocationSaverMode.SAMPLING -> GuardianProfile.TRAVEL
        isGaming -> GuardianProfile.GAMING
        else -> GuardianProfile.NORMAL
      }

    if (lastProfile != null && lastProfile != activeProfile) {
      historyRepository.logActivity(
        category = LogCategory.PROFILE,
        severity = LogSeverity.INFO,
        titleEn = "Profile Switched -> ${activeProfile.name}",
        titleHi = "प्रोफ़ाइल बदला -> ${activeProfile.name}",
        detailEn = "Active optimization profile changed based on battery, thermal, and schedule state.",
        detailHi = "बैटरी, तापमान और समय के अनुसार प्रोफ़ाइल स्वतः अपडेट हुआ।",
        whyEn = "Automatic profiles adjust sampling and alerts without draining extra battery.",
        whyHi = "स्वचालित प्रोफ़ाइल बिना अतिरिक्त बैटरी खर्च किए सुरक्षा नियम लागू करते हैं।",
      )
    }
    lastProfile = activeProfile

    // 6. Screen-off baseline tracking
    if (lastScreenOnState != null && lastScreenOnState != telemetry.screenOn) {
      if (!telemetry.screenOn) {
        preferencesRepository.setScreenOffBaseline(telemetry.levelPercent, now)
      }
    }
    lastScreenOnState = telemetry.screenOn

    // 7. Auto-clean Netra's own cache if > 100 MB (skip during Doze mode)
    if (!dozeModeMonitor.shouldSkipNonEssentialWork()) {
      val ownCacheMb = storageInspector.inspect(telemetry.temperatureCelsius, telemetry.levelPercent).appOwnCacheBytes / (1024 * 1024)
      if (ownCacheMb > 100L) {
        val freed = storageInspector.cleanOwnCache()
        if (freed > 0L) {
          historyRepository.logActivity(
            category = LogCategory.STORAGE,
            severity = LogSeverity.INFO,
            titleEn = "Auto-Cleaned Netra Cache",
            titleHi = "नेत्रा ऐप कैश स्वतः साफ़ हुआ",
            detailEn = "Freed ${StorageInspector.formatBytes(freed)} from Netra's own cache (>100 MB threshold).",
            detailHi = "नेत्रा के अपने कैश से ${StorageInspector.formatBytes(freed)} खाली किया गया।",
            whyEn = "Netra only cleans its own temporary cache when it exceeds 100 MB; user files are untouched.",
            whyHi = "100 MB से अधिक होने पर केवल ऐप का अपना अस्थायी कैश हटाया जाता है।",
          )
        }
      }
    }

    // 8. Persist snapshot batched according to Adaptive Power Tier interval
    val minIntervalMillis = dbBatchIntervalSeconds.coerceAtLeast(60) * 1000L
    if (forceSnapshot || now - lastSnapshotSavedMillis >= minIntervalMillis) {
      lastSnapshotSavedMillis = now
      historyRepository.recordSnapshot(telemetry, activeProfile)
    }

    RuleEvaluationOutput(
      activeProfile = activeProfile,
      coolingWorkflowActive = coolingActive,
      coolingStepsAppliedEn = coolingStepsEn,
      coolingStepsAppliedHi = coolingStepsHi,
      temperatureZone = zone,
    )
  }
}
