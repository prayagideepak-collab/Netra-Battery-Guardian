package com.example.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.StorageInspector
import com.example.data.repository.BatteryHistoryRepository
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
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
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
  private val historyRepository: BatteryHistoryRepository,
  private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val prefs = preferencesRepository.preferences.first()
    val telemetry = telemetryCollector.collect(
      observedCapacityFallbackMah = prefs.observedCapacityMah,
      isAppInForeground = false,
    )

    // Skip heavy storage work if hot >=40°C or battery <15%
    if (telemetry.temperatureCelsius >= 40f || telemetry.levelPercent < 15) {
      return Result.success()
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
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
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
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    selfHealingSupervisor.runNaturalHealthCheck(triggerSource = "workmanager_window")
    return Result.success()
  }
}

object NetraWorkScheduler {
  private const val WORK_STORAGE_AUDIT = "netra_storage_audit"
  private const val WORK_DATA_RETENTION = "netra_data_retention"
  private const val WORK_SCREEN_OFF_DRAIN = "netra_screen_off_drain"
  private const val WORK_HEALTH_CHECK = "netra_health_check"

  fun schedulePeriodicMaintenance(context: Context) {
    val wm = WorkManager.getInstance(context)

    val storageConstraints =
      Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    val storageRequest =
      PeriodicWorkRequestBuilder<StorageAuditWorker>(12, TimeUnit.HOURS)
        .setConstraints(storageConstraints)
        .build()

    wm.enqueueUniquePeriodicWork(
      WORK_STORAGE_AUDIT,
      ExistingPeriodicWorkPolicy.KEEP,
      storageRequest,
    )

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
        .setConstraints(storageConstraints)
        .build()

    wm.enqueueUniquePeriodicWork(
      WORK_HEALTH_CHECK,
      ExistingPeriodicWorkPolicy.KEEP,
      healthRequest,
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
