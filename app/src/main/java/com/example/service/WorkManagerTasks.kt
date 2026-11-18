package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.DiskUsageHelper
import com.example.data.local.system.StorageInspector
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.engine.BatteryLifecycleEstimator
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.engine.SelfHealingSupervisor
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class BackgroundDrainCheckWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val preferencesRepository: PreferencesRepository,
  private val historyRepository: BatteryHistoryRepository,
  private val notificationHelper: NotificationHelper,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (dozeModeMonitor.shouldSkipNonEssentialWork()) return Result.success()
    val prefs = preferencesRepository.preferences.first()
    val telemetry = telemetryCollector.collect(
      observedCapacityFallbackMah = prefs.observedCapacityMah,
      isAppInForeground = false,
    )

    // If screen turned on or charging started, no abnormal screen-off alert needed
    if (telemetry.screenOn || telemetry.isPlugged) return Result.success()

    val baselineLevel = prefs.lastScreenOffLevel
    val baselineTime = prefs.lastScreenOffTimestamp
    if (baselineLevel < 0 || baselineTime <= 0L) return Result.success()

    val elapsedMinutes = ((telemetry.timestampMillis - baselineTime) / 60_000.0).coerceAtLeast(1.0)
    val dropPercent = (baselineLevel - telemetry.levelPercent).coerceAtLeast(0)
    val drainPerHour = (dropPercent / elapsedMinutes) * 60.0

    // Abnormal screen-off drain threshold: >= 3.5% per hour when idle
    if (dropPercent >= 2 && drainPerHour >= 3.5) {
      historyRepository.startOrUpdateDischargeSession(
        telemetry = telemetry,
        screenOffDeltaMin = elapsedMinutes.toInt(),
        abnormalDrain = true,
      )
      historyRepository.logActivity(
        category = LogCategory.BATTERY,
        severity = LogSeverity.WARNING,
        titleEn = "Abnormal Screen-Off Drain Detected",
        titleHi = "स्क्रीन बंद होने पर असामान्य बैटरी खपत",
        detailEn = "Dropped $dropPercent% in ${elapsedMinutes.toInt()} min (${String.format("%.1f", drainPerHour)}%/hr). Consider restricting background sync or location.",
        detailHi = "${elapsedMinutes.toInt()} मिनट में $dropPercent% बैटरी घटी (${String.format("%.1f", drainPerHour)}%/घंटा)। बैकग्राउंड सिंक और लोकेशन सीमित करें।",
        whyEn = "Screen-off drain above 3.5%/hr typically indicates wake-locks, poor network signal, or background GPS.",
        whyHi = "स्क्रीन बंद होने पर 3.5%/घंटा से अधिक खपत अक्सर कमज़ोर नेटवर्क या बैकग्राउंड ऐप वेक-लॉक से होती है।",
      )
      notificationHelper.postAdvisory(
        id = NotificationHelper.ID_DRAIN_ALERT,
        title = "High Screen-Off Drain (${String.format("%.1f", drainPerHour)}%/hr)",
        body = "Battery dropped $dropPercent% while idle. Open Netra to inspect background apps and location settings.",
      )
    }
    return Result.success()
  }
}

@HiltWorker
class StorageAuditWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val preferencesRepository: PreferencesRepository,
  private val storageInspector: StorageInspector,
  private val diskUsageHelper: DiskUsageHelper,
  private val historyRepository: BatteryHistoryRepository,
  private val notificationHelper: NotificationHelper,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (dozeModeMonitor.shouldSkipNonEssentialWork()) return Result.success()
    val prefs = preferencesRepository.preferences.first()
    val telemetry = telemetryCollector.collect(
      observedCapacityFallbackMah = prefs.observedCapacityMah,
      isAppInForeground = false,
    )

    // Skip heavy storage work if hot >=40°C or battery <15%
    if (telemetry.temperatureCelsius >= 40f || telemetry.levelPercent < 15) {
      return Result.success()
    }

    // Run StorageStatsManager app-specific disk scan strictly in this idle maintenance window
    val diskReport = diskUsageHelper.scanAppStorageIfIdle(
      fromIdleWorker = true,
      temperatureCelsius = telemetry.temperatureCelsius,
      batteryPercent = telemetry.levelPercent,
    )
    if (diskReport.executedDuringIdleWindow && diskReport.heavyAppFiles.isNotEmpty()) {
      val topHeavy = diskReport.heavyAppFiles.first()
      historyRepository.logActivity(
        category = LogCategory.STORAGE,
        severity = LogSeverity.INFO,
        titleEn = "Idle Disk Scan: ${diskReport.heavyAppFiles.size} Heavy File(s)",
        titleHi = "आइडल डिस्क स्कैन: ${diskReport.heavyAppFiles.size} भारी फ़ाइलें",
        detailEn = "Largest app file: ${topHeavy.name} (${StorageInspector.formatBytes(topHeavy.sizeBytes)}).",
        detailHi = "सबसे बड़ी ऐप फ़ाइल: ${topHeavy.name} (${StorageInspector.formatBytes(topHeavy.sizeBytes)})।",
        whyEn = "StorageStatsManager scan runs exclusively during idle maintenance windows.",
        whyHi = "StorageStatsManager स्कैन केवल आइडल मेंटेनेंस विंडो में चलता है।",
      )
    }

    val insight = storageInspector.inspect(telemetry.temperatureCelsius, telemetry.levelPercent)
    if (insight.appOwnCacheBytes > 100L * 1024L * 1024L) {
      val freed = storageInspector.cleanOwnCache()
      if (freed > 0L) {
        historyRepository.logActivity(
          category = LogCategory.STORAGE,
          severity = LogSeverity.INFO,
          titleEn = "Scheduled Self-Cache Cleanup",
          titleHi = "नियमित ऐप कैश सफाई",
          detailEn = "Auto-cleaned ${StorageInspector.formatBytes(freed)} of Netra's own cache.",
          detailHi = "नेत्रा के अपने कैश से ${StorageInspector.formatBytes(freed)} हटाया गया।",
          whyEn = "Runs only when battery is healthy and cool.",
          whyHi = "यह केवल तभी चलता है जब बैटरी पर्याप्त और ठंडी हो।",
        )
      }
    }

    if (prefs.storageNotificationsEnabled) {
      if (insight.freePercent < 5f) {
        notificationHelper.postCriticalAlert(
          id = NotificationHelper.ID_STORAGE_ALERT,
          title = "Urgent: Storage Below 5% Free",
          body = "Only ${StorageInspector.formatBytes(insight.freeBytes)} left. Open Storage settings to review unused apps or downloads.",
        )
      } else if (insight.freePercent < 10f) {
        notificationHelper.postAdvisory(
          id = NotificationHelper.ID_STORAGE_ALERT,
          title = "Low Storage: Below 10% Free",
          body = "${StorageInspector.formatBytes(insight.freeBytes)} available. Tap to open Android Storage settings.",
          actionIntent = storageInspector.buildSettingsIntentForTarget(
            com.example.domain.model.StorageActionTarget.ANDROID_STORAGE_SETTINGS,
            null,
          ),
          actionLabel = "Storage Settings",
        )
      }
    }
    return Result.success()
  }
}

@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val preferencesRepository: PreferencesRepository,
  private val historyRepository: BatteryHistoryRepository,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (dozeModeMonitor.shouldSkipNonEssentialWork()) return Result.success()
    val prefs = preferencesRepository.preferences.first()
    historyRepository.pruneOldData(prefs.dataRetentionDays)
    return Result.success()
  }
}

@HiltWorker
class GuardianHealthCheckWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val selfHealingSupervisor: SelfHealingSupervisor,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (dozeModeMonitor.shouldSkipNonEssentialWork()) return Result.success()
    selfHealingSupervisor.runNaturalHealthCheck(triggerSource = "workmanager_window")
    return Result.success()
  }
}

/**
 * Runs once per day with charging constraint to estimate and log long-term battery
 * cycle counts and capacity degradation metrics without idle battery drain.
 */
@HiltWorker
class DailyBatteryHealthWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val batteryLifecycleEstimator: BatteryLifecycleEstimator,
  private val dozeModeMonitor: DozeModeMonitor,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (dozeModeMonitor.shouldSkipNonEssentialWork()) return Result.success()
    batteryLifecycleEstimator.evaluateAndLogDailyWhileCharging()
    return Result.success()
  }
}

/**
 * Low-power periodic WorkManager task that persists a BatterySnapshot to Room
 * strictly when the device is idle (Constraints.setRequiresDeviceIdle = true).
 */
@HiltWorker
class IdleBatteryPersistenceWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val telemetryCollector: BatteryTelemetryCollector,
  private val preferencesRepository: PreferencesRepository,
  private val chargingClassifier: ChargingSpeedClassifier,
  private val appDatabase: AppDatabase,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val prefs = preferencesRepository.preferences.first()
    val stickyBatteryIntent = applicationContext.registerReceiver(
      null,
      IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    )
    val telemetry = telemetryCollector.collect(
      intentOverride = stickyBatteryIntent,
      observedCapacityFallbackMah = prefs.observedCapacityMah,
      isAppInForeground = false,
    )
    val diagnosis = chargingClassifier.diagnose(telemetry)
    val statusCode =
      stickyBatteryIntent?.getIntExtra(
        BatteryManager.EXTRA_STATUS,
        BatteryManager.BATTERY_STATUS_UNKNOWN,
      ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val statusLabel =
      when (statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
        BatteryManager.BATTERY_STATUS_FULL -> "FULL"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
        else -> if (telemetry.isCharging) "CHARGING" else "DISCHARGING"
      }

    appDatabase.batteryDao().insertSnapshot(
      BatterySnapshot(
        timestampMillis = telemetry.timestampMillis,
        levelPercent = telemetry.levelPercent,
        voltageMv = telemetry.voltageMv,
        temperatureCelsius = telemetry.temperatureCelsius,
        currentMa = telemetry.currentMa,
        powerWatts = diagnosis.netPowerWatts,
        isCharging = telemetry.isCharging,
        chargingStatus = statusLabel,
        isPlugged = telemetry.isPlugged,
        plugType = telemetry.plugType.name,
        screenOn = telemetry.screenOn,
        thermalLevel = telemetry.thermalLevel.name,
        activeProfile = "IDLE_PERIODIC",
        isHourlyCheckpoint = true,
      )
    )
    return Result.success()
  }
}

object NetraWorkScheduler {
  const val WORK_STORAGE_AUDIT = "netra_storage_audit"
  const val WORK_DATA_RETENTION = "netra_data_retention"
  const val WORK_SCREEN_OFF_DRAIN = "netra_screen_off_drain"
  const val WORK_HEALTH_CHECK = "netra_health_check"
  const val WORK_IDLE_BATTERY_PERSISTENCE = "netra_idle_battery_persistence"
  const val WORK_DAILY_BATTERY_HEALTH = "netra_daily_battery_health"

  fun buildDailyBatteryHealthRequest(): PeriodicWorkRequest {
    val chargingConstraints =
      Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresBatteryNotLow(true)
        .build()

    return PeriodicWorkRequestBuilder<DailyBatteryHealthWorker>(24, TimeUnit.HOURS)
      .setConstraints(chargingConstraints)
      .build()
  }

  fun buildIdlePersistenceRequest(): PeriodicWorkRequest {
    val idleConstraints =
      Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .build()

    return PeriodicWorkRequestBuilder<IdleBatteryPersistenceWorker>(1, TimeUnit.HOURS)
      .setConstraints(idleConstraints)
      .build()
  }

  fun buildStorageAuditRequest(): PeriodicWorkRequest {
    val idleStorageConstraints =
      Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .build()

    return PeriodicWorkRequestBuilder<StorageAuditWorker>(12, TimeUnit.HOURS)
      .setConstraints(idleStorageConstraints)
      .build()
  }

  fun schedulePeriodicMaintenance(context: Context) {
    val wm = WorkManager.getInstance(context)

    wm.enqueueUniquePeriodicWork(
      WORK_IDLE_BATTERY_PERSISTENCE,
      ExistingPeriodicWorkPolicy.KEEP,
      buildIdlePersistenceRequest(),
    )

    wm.enqueueUniquePeriodicWork(
      WORK_STORAGE_AUDIT,
      ExistingPeriodicWorkPolicy.KEEP,
      buildStorageAuditRequest(),
    )

    val healthConstraints =
      Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    val retentionConstraints =
      Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(true)
        .build()

    val retentionRequest =
      PeriodicWorkRequestBuilder<DataRetentionWorker>(24, TimeUnit.HOURS)
        .setConstraints(retentionConstraints)
        .build()

    wm.enqueueUniquePeriodicWork(
      WORK_DATA_RETENTION,
      ExistingPeriodicWorkPolicy.KEEP,
      retentionRequest,
    )

    val healthRequest =
      PeriodicWorkRequestBuilder<GuardianHealthCheckWorker>(6, TimeUnit.HOURS)
        .setConstraints(healthConstraints)
        .build()

    wm.enqueueUniquePeriodicWork(
      WORK_HEALTH_CHECK,
      ExistingPeriodicWorkPolicy.KEEP,
      healthRequest,
    )

    wm.enqueueUniquePeriodicWork(
      WORK_DAILY_BATTERY_HEALTH,
      ExistingPeriodicWorkPolicy.KEEP,
      buildDailyBatteryHealthRequest(),
    )
  }

  fun scheduleScreenOffDrainCheck(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<BackgroundDrainCheckWorker>()
        .setInitialDelay(12, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      WORK_SCREEN_OFF_DRAIN,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun cancelScreenOffDrainCheck(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_SCREEN_OFF_DRAIN)
  }
}
