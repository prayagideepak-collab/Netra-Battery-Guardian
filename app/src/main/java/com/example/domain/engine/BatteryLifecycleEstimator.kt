package com.example.domain.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatteryHealthLogEntity
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BatteryLifecycleResult(
  val loggedNewEntry: Boolean,
  val alreadyLoggedToday: Boolean,
  val skippedReason: String? = null,
  val latestEntry: BatteryHealthLogEntity? = null,
)

/**
 * Estimates and logs long-term battery cycle counts and capacity degradation metrics
 * strictly once per day while the device is charging, using [BatteryManager] telemetry
 * and atomic DataStore + Room persistence.
 */
@Singleton
class BatteryLifecycleEstimator @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val appDatabase: AppDatabase,
  private val preferencesRepository: PreferencesRepository,
  private val logWriter: BoundedLogFileWriter,
) {
  companion object {
    const val MILLIS_PER_DAY = 86_400_000L
  }

  private val evalMutex = Mutex()
  private val batteryManager: BatteryManager? =
    context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

  private val _latestEntry = MutableStateFlow<BatteryHealthLogEntity?>(null)
  val latestEntry: StateFlow<BatteryHealthLogEntity?> = _latestEntry.asStateFlow()

  suspend fun evaluateAndLogDailyWhileCharging(
    intentOverride: Intent? = null,
    nowMillis: Long = System.currentTimeMillis(),
  ): BatteryLifecycleResult = evalMutex.withLock {
    val prefs = preferencesRepository.preferences.first()
    val intent = intentOverride ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
    val levelPercent =
      if (levelRaw >= 0) ((levelRaw * 100f) / scale).toInt().coerceIn(0, 100)
      else batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 50

    val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val isCharging =
      statusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
        statusCode == BatteryManager.BATTERY_STATUS_FULL ||
        (intent == null && (batteryManager?.isCharging == true))

    val chargeCounterRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1L
    val chargeCounterUah = if (chargeCounterRaw > 0L) chargeCounterRaw else -1L
    val estimatedCapacityMah =
      if (chargeCounterUah > 0L && levelPercent >= 15) {
        ((chargeCounterUah / 1000.0) / (levelPercent / 100.0)).toInt().coerceIn(1500, 12000)
      } else {
        prefs.observedCapacityMah
      }

    val designBaseline =
      if (prefs.designCapacityBaselineMah > 0) {
        prefs.designCapacityBaselineMah
      } else {
        maxOf(prefs.observedCapacityMah, estimatedCapacityMah)
      }

    var cumulativeChargedPct = prefs.cumulativeChargedPercent
    if (isCharging && prefs.lastChargeLevelForCycles >= 0 && levelPercent > prefs.lastChargeLevelForCycles) {
      val deltaPct = (levelPercent - prefs.lastChargeLevelForCycles).coerceIn(0, 100)
      cumulativeChargedPct += deltaPct.toFloat()
    }

    if (levelPercent != prefs.lastChargeLevelForCycles ||
      prefs.designCapacityBaselineMah == 0 ||
      cumulativeChargedPct != prefs.cumulativeChargedPercent
    ) {
      preferencesRepository.updateCycleTracking(
        cumulativeChargedPercent = cumulativeChargedPct,
        lastChargeLevel = levelPercent,
        designCapacityBaselineMah = if (prefs.designCapacityBaselineMah == 0) designBaseline else null,
      )
    }

    if (!isCharging) {
      return@withLock BatteryLifecycleResult(
        loggedNewEntry = false,
        alreadyLoggedToday = false,
        skippedReason = "Skipped: device is not charging",
        latestEntry = _latestEntry.value,
      )
    }

    val dayBucket = nowMillis / MILLIS_PER_DAY
    val dao = appDatabase.netraDao()
    val existingToday = dao.getBatteryHealthLogForDay(dayBucket)
    if (existingToday != null) {
      _latestEntry.value = existingToday
      return@withLock BatteryLifecycleResult(
        loggedNewEntry = false,
        alreadyLoggedToday = true,
        skippedReason = "Already logged battery cycle & capacity metrics today",
        latestEntry = existingToday,
      )
    }

    val osCycleCount =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        if (cycles >= 0) cycles else -1
      } else {
        -1
      }

    val estimatedCycleCount =
      if (osCycleCount >= 0) osCycleCount.toFloat()
      else (cumulativeChargedPct / 100f).coerceAtLeast(0f)

    val sourceLabel =
      when {
        osCycleCount >= 0 && chargeCounterUah > 0L -> "OS_CYCLE_AND_COULOMB"
        osCycleCount >= 0 -> "OS_CYCLE_COUNT"
        chargeCounterUah > 0L -> "COULOMB_COUNTER_ESTIMATE"
        else -> "LEVEL_DELTA_ESTIMATE"
      }

    val effectiveDesignMah = maxOf(designBaseline, prefs.observedCapacityMah)
    val degradationPercent =
      if (effectiveDesignMah > 0) {
        (((effectiveDesignMah - estimatedCapacityMah).toFloat() / effectiveDesignMah.toFloat()) * 100f)
          .coerceIn(0f, 99f)
      } else {
        0f
      }
    val healthPercent = (100f - degradationPercent).coerceIn(1f, 100f)

    val entry = BatteryHealthLogEntity(
      dayBucket = dayBucket,
      timestampMillis = nowMillis,
      estimatedCycleCount = estimatedCycleCount,
      designCapacityMah = effectiveDesignMah,
      estimatedCapacityMah = estimatedCapacityMah,
      degradationPercent = degradationPercent,
      healthPercent = healthPercent,
      sourceLabel = sourceLabel,
    )
    dao.insertBatteryHealthLog(entry)
    _latestEntry.value = entry

    val cycleStr = String.format(Locale.US, "%.2f", estimatedCycleCount)
    val degStr = String.format(Locale.US, "%.1f", degradationPercent)
    val healthStr = String.format(Locale.US, "%.1f", healthPercent)
    logWriter.append(
      "CYCLE_HEALTH",
      "Daily charging check: cycles=$cycleStr, est=${estimatedCapacityMah}mAh/${effectiveDesignMah}mAh, wear=$degStr%, health=$healthStr%, source=$sourceLabel",
    )
    dao.insertActivityLog(
      ActivityLogEntity(
        timestampMillis = nowMillis,
        category = LogCategory.BATTERY.name,
        severity = if (degradationPercent >= 20f) LogSeverity.WARNING.name else LogSeverity.INFO.name,
        titleEn = "Daily Battery Health & Cycle Log",
        titleHi = "दैनिक बैटरी चक्र और क्षमता लॉग",
        detailEn = "Cycles: $cycleStr • Est. Capacity: $estimatedCapacityMah / $effectiveDesignMah mAh • Degradation: $degStr% ($healthStr% health).",
        detailHi = "चक्र: $cycleStr • अनुमानित क्षमता: $estimatedCapacityMah / $effectiveDesignMah mAh • क्षरण: $degStr% ($healthStr% स्वास्थ्य)।",
        whyEn = "Logged once per day while charging using BatteryManager coulomb/cycle metrics with minimal battery overhead.",
        whyHi = "चार्जिंग के दौरान प्रतिदिन केवल एक बार BatteryManager डेटा से बैटरी स्वास्थ्य और चक्र दर्ज किए जाते हैं।",
      )
    )

    BatteryLifecycleResult(
      loggedNewEntry = true,
      alreadyLoggedToday = false,
      latestEntry = entry,
    )
  }
}
