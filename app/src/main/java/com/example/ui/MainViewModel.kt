package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.BatteryHealthLogEntity
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.db.ChargingSessionEntity
import com.example.data.local.db.DischargeSessionEntity
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.prefs.UserPreferences
import com.example.data.local.system.AppPowerInspector
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.DiskUsageHelper
import com.example.data.local.system.DiskUsageReport
import com.example.data.local.system.LocalReportExporter
import com.example.data.local.system.StorageInspector
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.AutomationRulesEngine
import com.example.domain.engine.BatteryLifecycleEstimator
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.engine.HeatingDiagnosisEngine
import com.example.domain.engine.SelfHealingSupervisor
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppPowerInsight
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.GuardianProfile
import com.example.domain.model.HeatDiagnosisReport
import com.example.domain.model.LocationSaverMode
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.domain.model.PowerBudgetMetrics
import com.example.domain.model.SelfHealingStatus
import com.example.domain.model.StorageActionTarget
import com.example.domain.model.StorageInsight
import com.example.domain.model.ThemeMode
import com.example.service.BatteryGuardianService
import com.example.service.BatteryMonitoringService
import com.example.service.DozeModeMonitor
import com.example.service.DozeMonitorState
import com.example.service.ThermalMonitor
import com.example.service.ThermalMonitorState
import com.example.voice.ChargingVoiceAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class NetraUiState(
  val telemetry: BatteryTelemetry = BatteryTelemetry(),
  val chargingDiagnosis: ChargingDiagnosis? = null,
  val heatReport: HeatDiagnosisReport? = null,
  val activeProfile: GuardianProfile = GuardianProfile.NORMAL,
  val powerBudget: PowerBudgetMetrics =
    PowerBudgetMetrics(AdaptivePowerTier.NORMAL, 0, 0, 0, 15, false),
  val storageInsight: StorageInsight = StorageInsight(1L, 0L, 1L, 50f, 0L, 0L),
  val topApps: List<AppPowerInsight> = emptyList(),
  val hasUsageStatsPermission: Boolean = false,
  val snapshots24h: List<BatterySnapshot> = emptyList(),
  val snapshots7d: List<BatterySnapshot> = emptyList(),
  val hourlySnapshots: List<BatterySnapshot> = emptyList(),
  val chargingSessions: List<ChargingSessionEntity> = emptyList(),
  val dischargeSessions: List<DischargeSessionEntity> = emptyList(),
  val activityLogs: List<ActivityLogEntity> = emptyList(),
  val prefs: UserPreferences = UserPreferences(),
  val selfHealingStatus: SelfHealingStatus = SelfHealingStatus(),
  val thermalState: ThermalMonitorState = ThermalMonitorState(),
  val dozeState: DozeMonitorState = DozeMonitorState(),
  val batteryHealthLogs: List<BatteryHealthLogEntity> = emptyList(),
  val diskUsageReport: DiskUsageReport? = null,
  val statusMessage: String? = null,
  val isNightSilentActive: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
  private val app: Application,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val appPowerInspector: AppPowerInspector,
  private val storageInspector: StorageInspector,
  private val diskUsageHelper: DiskUsageHelper,
  private val chargingClassifier: ChargingSpeedClassifier,
  private val heatingDiagnosisEngine: HeatingDiagnosisEngine,
  private val adaptivePowerTierEngine: AdaptivePowerTierEngine,
  private val selfHealingSupervisor: SelfHealingSupervisor,
  private val rulesEngine: AutomationRulesEngine,
  private val preferencesRepository: PreferencesRepository,
  private val historyRepository: BatteryHistoryRepository,
  private val voiceAssistant: ChargingVoiceAssistant,
  private val reportExporter: LocalReportExporter,
  private val thermalMonitor: ThermalMonitor,
  private val dozeModeMonitor: DozeModeMonitor,
  private val batteryLifecycleEstimator: BatteryLifecycleEstimator,
) : AndroidViewModel(app) {

  private val _uiState = MutableStateFlow(NetraUiState())
  val uiState: StateFlow<NetraUiState> = _uiState.asStateFlow()

  private val _isUiVisible = MutableStateFlow(false)
  val isUiVisible: StateFlow<Boolean> = _isUiVisible.asStateFlow()

  private var wakeupsThisHour = 0
  private var wakeupHourBucket = currentHourBucket()
  private var isForegroundActive = false
  private var receiverRegistered = false
  private var foregroundLoopJob: Job? = null

  private val liveBatteryReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (isForegroundActive) {
          refreshNow(intentOverride = intent, forceSnapshot = false)
        }
      }
    }

  init {
    observeDatabaseAndPreferences()
    viewModelScope.launch {
      val health = selfHealingSupervisor.runNaturalHealthCheck(triggerSource = "app_launch")
      _uiState.update { it.copy(selfHealingStatus = health) }
    }
    refreshNow(intentOverride = null, forceSnapshot = true)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeDatabaseAndPreferences() {
    val startOfDay = startOfDayMillis()
    viewModelScope.launch {
      combine(
        preferencesRepository.preferences,
        thermalMonitor.state,
        diskUsageHelper.latestReport,
        dozeModeMonitor.state,
      ) { prefs, thermal, diskReport, doze -> Tuple4(prefs, thermal, diskReport, doze) }
        .collect { (prefs, thermal, diskReport, doze) ->
          thermalMonitor.updateSensitivity(prefs.coolingSensitivity)
          _uiState.update { state ->
            state.copy(
              prefs = prefs,
              thermalState = thermal,
              dozeState = doze,
              diskUsageReport = diskReport,
              isNightSilentActive = voiceAssistant.isNightSilentActive(prefs),
            )
          }
        }
    }

    // Refresh Room chart & session history ONLY while UI is visible (low-power constraint)
    viewModelScope.launch {
      _isUiVisible
        .flatMapLatest { visible ->
          if (!visible) {
            emptyFlow()
          } else {
            combine(
              historyRepository.observe24HourSnapshots(),
              historyRepository.observe7DaySnapshots(),
              historyRepository.observeRecentChargingSessions(),
              historyRepository.observeRecentDischargeSessions(),
              historyRepository.observeActivityLogs(),
            ) { s24, s7d, chargeSess, dischargeSess, logs ->
              Tuple5(s24, s7d, chargeSess, dischargeSess, logs)
            }.combine(
              combine(
                historyRepository.observeSnapshotsTodayCount(startOfDay),
                historyRepository.observeHourlySnapshots(),
                historyRepository.observeBatteryHealthLogs(),
              ) { countToday, hourly, healthLogs -> Triple(countToday, hourly, healthLogs) }
            ) { tuple, metricsTriple ->
              Pair(tuple, metricsTriple)
            }
          }
        }
        .collect { (tuple, metricsTriple) ->
          val (s24, s7d, chargeSess, dischargeSess, logs) = tuple
          val (countToday, hourly, healthLogs) = metricsTriple
          _uiState.update { state ->
            state.copy(
              snapshots24h = s24,
              snapshots7d = s7d,
              hourlySnapshots = hourly,
              batteryHealthLogs = healthLogs,
              chargingSessions = chargeSess,
              dischargeSessions = dischargeSess,
              activityLogs = logs,
              powerBudget = state.powerBudget.copy(snapshotsRecordedToday = countToday),
            )
          }
        }
    }
  }

  private fun startAdaptiveForegroundLoop() {
    foregroundLoopJob?.cancel()
    foregroundLoopJob = viewModelScope.launch {
      while (isActive && isForegroundActive) {
        val intervalSec = _uiState.value.powerBudget.monitoringIntervalSeconds.coerceIn(15, 180)
        delay(intervalSec * 1000L)
        if (isForegroundActive && !dozeModeMonitor.shouldSkipNonEssentialWork()) {
          refreshNow(intentOverride = null, forceSnapshot = false)
        }
      }
    }
  }

  fun onForegroundStarted() {
    isForegroundActive = true
    _isUiVisible.value = true
    thermalMonitor.register(
      owner = ThermalMonitor.OWNER_FOREGROUND_UI,
      coolingSensitivity = _uiState.value.prefs.coolingSensitivity,
    )
    registerBatteryReceiver()
    startAdaptiveForegroundLoop()
    refreshNow(intentOverride = null, forceSnapshot = false)
  }

  fun onForegroundStopped() {
    isForegroundActive = false
    _isUiVisible.value = false
    thermalMonitor.unregister(ThermalMonitor.OWNER_FOREGROUND_UI)
    foregroundLoopJob?.cancel()
    foregroundLoopJob = null
    unregisterBatteryReceiver()
  }

  private fun registerBatteryReceiver() {
    if (receiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      app.registerReceiver(liveBatteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      app.registerReceiver(liveBatteryReceiver, filter)
    }
    receiverRegistered = true
  }

  private fun unregisterBatteryReceiver() {
    if (!receiverRegistered) return
    runCatching { app.unregisterReceiver(liveBatteryReceiver) }
    receiverRegistered = false
  }

  fun refreshNow(intentOverride: Intent? = null, forceSnapshot: Boolean = false) {
    viewModelScope.launch {
      val prefs = preferencesRepository.preferences.first()
      val bucket = currentHourBucket()
      if (bucket != wakeupHourBucket) {
        wakeupHourBucket = bucket
        wakeupsThisHour = 0
      }
      wakeupsThisHour++

      val telemetry =
        withContext(Dispatchers.Default) {
          telemetryCollector.collect(
            intentOverride = intentOverride,
            observedCapacityFallbackMah = prefs.observedCapacityMah,
            isAppInForeground = isForegroundActive,
          )
        }
      val snapshotsToday = _uiState.value.powerBudget.snapshotsRecordedToday
      val powerBudget = adaptivePowerTierEngine.evaluate(telemetry, wakeupsThisHour, snapshotsToday)
      val inDoze = dozeModeMonitor.shouldSkipNonEssentialWork()
      val hasUsage = appPowerInspector.hasUsageStatsPermission()
      val topApps =
        withContext(Dispatchers.Default) {
          if (!inDoze && hasUsage && powerBudget.activeTier != AdaptivePowerTier.SURVIVAL) {
            appPowerInspector.collectTopApps()
          } else {
            emptyList()
          }
        }
      val eval = rulesEngine.evaluate(
        telemetry = telemetry,
        prefs = prefs,
        topApps = topApps,
        forceSnapshot = forceSnapshot,
        dbBatchIntervalSeconds = powerBudget.dbBatchIntervalSeconds,
      )
      if (!inDoze && telemetry.isCharging) {
        batteryLifecycleEstimator.evaluateAndLogDailyWhileCharging(
          intentOverride = intentOverride,
          nowMillis = telemetry.timestampMillis,
        )
      }
      val chargingDiag = chargingClassifier.diagnose(telemetry)
      val heatReport =
        heatingDiagnosisEngine.diagnose(
          telemetry = telemetry,
          chargingDiagnosis = chargingDiag,
          topApps = topApps,
          sensitivity = prefs.coolingSensitivity,
          coolingWorkflowActive = eval.coolingWorkflowActive,
          coolingStepsAppliedEn = eval.coolingStepsAppliedEn,
          coolingStepsAppliedHi = eval.coolingStepsAppliedHi,
        )
      val storageInsight =
        if (!inDoze) {
          withContext(Dispatchers.IO) {
            storageInspector.inspect(telemetry.temperatureCelsius, telemetry.levelPercent)
          }
        } else {
          _uiState.value.storageInsight
        }

      _uiState.update {
        it.copy(
          telemetry = telemetry,
          chargingDiagnosis = chargingDiag,
          heatReport = heatReport,
          activeProfile = eval.activeProfile,
          powerBudget = powerBudget,
          storageInsight = storageInsight,
          topApps = topApps,
          hasUsageStatsPermission = hasUsage,
          selfHealingStatus = selfHealingSupervisor.currentStatus(),
          isNightSilentActive = voiceAssistant.isNightSilentActive(prefs),
        )
      }
    }
  }

  // Quick actions & profile controls
  fun toggleBatterySaverQuick() {
    viewModelScope.launch {
      val current = _uiState.value.prefs.manualProfileOverride
      val next = if (current == GuardianProfile.BATTERY_SAVER) null else GuardianProfile.BATTERY_SAVER
      preferencesRepository.setManualProfileOverride(next)
      refreshNow()
    }
  }

  fun toggleCoolingModeQuick() {
    viewModelScope.launch {
      val current = _uiState.value.prefs.manualProfileOverride
      val next = if (current == GuardianProfile.COOLING) null else GuardianProfile.COOLING
      preferencesRepository.setManualProfileOverride(next)
      if (next == GuardianProfile.COOLING) {
        val lowered = telemetryCollector.tryLowerBrightnessForCooling()
        val msg =
          if (lowered) "Cooling workflow activated & brightness lowered."
          else "Cooling Mode active. Lower brightness or pause heavy apps."
        _uiState.update { it.copy(statusMessage = msg) }
      }
      refreshNow()
    }
  }

  fun toggleLocationSaverQuick() {
    viewModelScope.launch {
      val current = _uiState.value.prefs.locationSaverMode
      val next =
        if (current == LocationSaverMode.BATTERY_SAVER) LocationSaverMode.NORMAL
        else LocationSaverMode.BATTERY_SAVER
      preferencesRepository.updateLocationSaverMode(next)
      historyRepository.logActivity(
        category = LogCategory.LOCATION,
        severity = LogSeverity.INFO,
        titleEn = "Location Saver -> ${next.name}",
        titleHi = "लोकेशन सेवर -> ${next.name}",
        detailEn = "Switched Location Power Saver mode locally.",
        detailHi = "लोकेशन पावर सेवर मोड स्थानीय रूप से बदला गया।",
        whyEn = "Reducing high-accuracy GPS sampling cuts modem & sensor battery drain.",
        whyHi = "हाई-एक्यूरेसी जीपीएस कम करने से सेंसर और बैटरी की बचत होती है।",
      )
    }
  }

  fun toggleNightSilentQuick() {
    viewModelScope.launch {
      val p = _uiState.value.prefs
      preferencesRepository.updateNightSilent(
        enabled = !p.nightSilentEnabled,
        startMin = p.nightSilentStartMinutes,
        endMin = p.nightSilentEndMinutes,
      )
    }
  }

  fun cleanAppOwnCache() {
    viewModelScope.launch {
      val freed = withContext(Dispatchers.IO) { storageInspector.cleanOwnCache() }
      historyRepository.logActivity(
        category = LogCategory.STORAGE,
        severity = LogSeverity.INFO,
        titleEn = "Cleaned Netra App Cache",
        titleHi = "नेत्रा ऐप कैश साफ़ हुआ",
        detailEn = "Safely cleared ${StorageInspector.formatBytes(freed)} from Netra's own cache.",
        detailHi = "नेत्रा के अपने कैश से ${StorageInspector.formatBytes(freed)} सुरक्षित हटाया गया।",
        whyEn = "Netra never deletes photos, videos, downloads, or other apps' files.",
        whyHi = "नेत्रा कभी भी फ़ोटो, वीडियो या अन्य ऐप्स की फ़ाइलें नहीं हटाता।",
      )
      _uiState.update {
        it.copy(statusMessage = "Cleaned ${StorageInspector.formatBytes(freed)} from Netra cache.")
      }
      refreshNow()
    }
  }

  fun openStorageTarget(target: StorageActionTarget, packageHint: String?) {
    if (target == StorageActionTarget.APP_OWN_CACHE) {
      cleanAppOwnCache()
    } else {
      val intent = storageInspector.buildSettingsIntentForTarget(target, packageHint)
      runCatching { app.startActivity(intent) }
    }
  }

  // Deep links
  fun openUsageAccessSettings() = runCatching { app.startActivity(appPowerInspector.buildUsageAccessIntent()) }
  fun openAppInfo(pkg: String) = runCatching { app.startActivity(appPowerInspector.buildAppInfoIntent(pkg)) }
  fun openAppNotifications(pkg: String) = runCatching { app.startActivity(appPowerInspector.buildAppNotificationSettingsIntent(pkg)) }
  fun openBatteryOptimizationSettings() = runCatching { app.startActivity(appPowerInspector.buildBatteryOptimizationSettingsIntent()) }
  fun openBatterySaverSettings() = runCatching { app.startActivity(appPowerInspector.buildBatterySaverSettingsIntent()) }
  fun openLocationSettings() = runCatching { app.startActivity(appPowerInspector.buildLocationSettingsIntent()) }
  fun openDataSaverSettings() = runCatching { app.startActivity(appPowerInspector.buildDataSaverSettingsIntent()) }
  fun openWriteSettings() = runCatching { app.startActivity(appPowerInspector.buildWriteSettingsIntent()) }

  // Settings updates
  fun setAppLanguage(lang: AppLanguage) {
    viewModelScope.launch { preferencesRepository.updateAppLanguage(lang) }
  }

  fun setVoiceLanguage(lang: AppLanguage) {
    viewModelScope.launch { preferencesRepository.updateVoiceLanguage(lang) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
  }

  fun setVoiceEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateVoiceEnabled(enabled) }
  }

  fun setVoiceVolume(volume: Float) {
    viewModelScope.launch { preferencesRepository.updateVoiceVolume(volume) }
  }

  fun setAnnounceCharging(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateAnnounceCharging(enabled) }
  }

  fun setAnnounceDischarging(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateAnnounceDischarging(enabled) }
  }

  fun setAnnounceScreenOff(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateAnnounceScreenOff(enabled) }
  }

  fun setAnnouncementStep(step: Int) {
    viewModelScope.launch { preferencesRepository.updateAnnouncementStep(step) }
  }

  fun setNightSilent(enabled: Boolean, startMin: Int, endMin: Int) {
    viewModelScope.launch { preferencesRepository.updateNightSilent(enabled, startMin, endMin) }
  }

  fun setCoolingSensitivity(sensitivity: CoolingSensitivity) {
    viewModelScope.launch { preferencesRepository.updateCoolingSensitivity(sensitivity) }
  }

  fun setLocationSaverMode(mode: LocationSaverMode, samplingMin: Int? = null) {
    viewModelScope.launch {
      preferencesRepository.updateLocationSaverMode(mode, samplingMin)
      if (mode != LocationSaverMode.TRIP && _uiState.value.prefs.isTripActive) {
        preferencesRepository.setTripActive(false)
      }
    }
  }

  fun setEmergencyLocationException(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setEmergencyLocationException(enabled) }
  }

  fun toggleTripMode(start: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setTripActive(start)
      if (start) {
        BatteryGuardianService.start(app)
      } else if (!_uiState.value.prefs.liveGuardianServiceEnabled) {
        BatteryGuardianService.stop(app)
      }
    }
  }

  fun setStorageNotifications(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateStorageNotifications(enabled) }
  }

  fun setBatterySaverThreshold(threshold: Int) {
    viewModelScope.launch { preferencesRepository.updateBatterySaverThreshold(threshold) }
  }

  fun setLowBatteryAlerts(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateLowBatteryAlerts(enabled) }
  }

  fun setDataRetentionDays(days: Int) {
    viewModelScope.launch { preferencesRepository.updateDataRetentionDays(days) }
  }

  fun setLiveGuardianService(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.updateLiveGuardianService(enabled)
      if (enabled) BatteryMonitoringService.start(app)
      else BatteryMonitoringService.stop(app)
    }
  }

  fun testVoiceAnnouncement() {
    voiceAssistant.testVoice(_uiState.value.prefs)
  }

  fun exportCsvReport() {
    viewModelScope.launch {
      val file = reportExporter.exportCsv()
      _uiState.update { it.copy(statusMessage = "CSV saved locally: ${file.name}") }
    }
  }

  fun exportPdfReport() {
    viewModelScope.launch {
      val file = reportExporter.exportPdf()
      _uiState.update { it.copy(statusMessage = "PDF saved locally: ${file.name}") }
    }
  }

  fun clearAllLocalData() {
    viewModelScope.launch {
      historyRepository.clearAllLocalData()
      preferencesRepository.resetAllPreferences()
      _uiState.update { it.copy(statusMessage = "All local Netra data cleared.") }
      refreshNow(forceSnapshot = true)
    }
  }

  fun consumeStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  override fun onCleared() {
    thermalMonitor.unregister(ThermalMonitor.OWNER_FOREGROUND_UI)
    unregisterBatteryReceiver()
    voiceAssistant.shutdown()
    super.onCleared()
  }

  private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
  private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
  private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

  private fun startOfDayMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  private fun currentHourBucket(): Long = System.currentTimeMillis() / 3_600_000L
}
