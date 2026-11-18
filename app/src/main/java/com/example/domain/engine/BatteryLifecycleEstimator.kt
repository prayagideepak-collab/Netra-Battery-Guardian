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
 * Logs Android cycle count and approximate counter-derived capacity, never guessed degradation
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
    val intent = intentOverride ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val reportedLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val levelPercent = if (scale > 0 && levelRaw in 0..scale) ((levelRaw * 100f) / scale).toInt()
      else reportedLevel?.takeIf { it in 0..100 } ?: -1

    val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val isCharging =
      statusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
        statusCode == BatteryManager.BATTERY_STATUS_FULL ||
        (intent == null && (batteryManager?.isCharging == true))

    // A discharging observation must not seed a charging-health baseline or
    // cycle state. Wait for the charging gate before deriving any capacity.
    if (!isCharging) {
      return@withLock BatteryLifecycleResult(
        loggedNewEntry = false,
        alreadyLoggedToday = false,
        skippedReason = "Skipped: device is not charging",
        latestEntry = _latestEntry.value,
      )
    }


    val chargeCounterRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1L
    val chargeCounterUah = if (chargeCounterRaw > 0L) chargeCounterRaw else -1L
    val estimatedCapacityMah = if (chargeCounterUah > 0L && levelPercent in 15..100) {
      ((chargeCounterUah / 1000.0) / (levelPercent / 100.0)).toInt()
    } else -1
    // No API-backed design baseline exists here. Old seeded baselines are not evidence.
    // Preserve old records, but never derive health/wear from them or infer lifetime cycles.
    val dayBucket = nowMillis / MILLIS_PER_DAY
    val dao = appDatabase.netraDao()
    val existingToday = dao.getBatteryHealthLogForDay(dayBucket)
    if (existingToday?.sourceLabel?.startsWith("OBSERVED_V2") == true) {
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

    val estimatedCycleCount = if (osCycleCount >= 0) osCycleCount.toFloat() else -1f
    val sourceLabel = "OBSERVED_V2_OS_CYCLE_AND_COUNTER_OR_UNAVAILABLE"
    val effectiveDesignMah = -1
    val degradationPercent = -1f
    val healthPercent = -1f

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

    val cycleStr = if (estimatedCycleCount >= 0) estimatedCycleCount.toInt().toString() else "Unavailable"
    val capacityStr = if (estimatedCapacityMah > 0) "approximately ${estimatedCapacityMah}mAh" else "Unavailable"
    logWriter.append("CYCLE_HEALTH", "Observed daily check: OS cycles=$cycleStr; capacity=$capacityStr; design/wear/health=Unavailable")
    dao.insertActivityLog(ActivityLogEntity(
      timestampMillis = nowMillis, category = LogCategory.BATTERY.name, severity = LogSeverity.INFO.name,
      titleEn = "Daily Battery Observation", titleHi = "दैनिक बैटरी मापन",
      detailEn = "OS cycles: $cycleStr; capacity: $capacityStr. Design capacity, wear and health: Unavailable.",
      detailHi = "OS cycles: $cycleStr; capacity: $capacityStr. Design capacity, wear and health: Unavailable.",
      whyEn = "Only Android-reported cycle count and counter-derived approximate capacity. No assumed design baseline.",
      whyHi = "केवल Android का चक्र और काउंटर आधारित अनुमान। काल्पनिक क्षमता का उपयोग नहीं।",
    ))

    BatteryLifecycleResult(
      loggedNewEntry = true,
      alreadyLoggedToday = false,
      latestEntry = entry,
    )
  }
}
