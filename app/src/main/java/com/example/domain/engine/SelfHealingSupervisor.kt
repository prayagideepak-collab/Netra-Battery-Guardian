package com.example.domain.engine

import android.content.Context
import com.example.data.local.db.AppDatabase
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.model.SelfHealingStatus
import com.example.service.NetraWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * Runs health checks ONLY at natural wake points (app launch, battery broadcasts, WorkManager windows).
 * Implements exponential backoff with jitter, a hard retry cap (3), and a circuit breaker
 * that stops recovery attempts and defers to the next scheduled window.
 */
@Singleton
class SelfHealingSupervisor @Inject constructor(
  @ApplicationContext private val context: Context,
  private val appDatabase: AppDatabase,
  private val preferencesRepository: PreferencesRepository,
  private val logWriter: BoundedLogFileWriter,
) {
  companion object {
    const val MAX_RETRIES = 3
    const val BASE_BACKOFF_MS = 30_000L
    const val CIRCUIT_COOLDOWN_MS = 6L * 3_600_000L
  }

  private var consecutiveFailures = 0
  private var circuitOpenUntilMillis = 0L
  private var nextAllowedRetryMillis = 0L
  private var lastStatus = SelfHealingStatus(rotatedLogBytes = 0L)

  fun computeBackoffWithJitterMs(attempt: Int, jitterFraction: Double = Random.nextDouble(0.0, 0.25)): Long {
    val exp = 1L shl attempt.coerceIn(0, 6)
    val base = min(BASE_BACKOFF_MS * exp, 30 * 60_000L)
    return base + (base * jitterFraction).toLong()
  }

  @Synchronized
  fun currentStatus(): SelfHealingStatus =
    lastStatus.copy(rotatedLogBytes = logWriter.totalSizeBytes())

  suspend fun runNaturalHealthCheck(
    triggerSource: String,
    nowMillis: Long = System.currentTimeMillis(),
    checkOverride: (suspend () -> Boolean)? = null,
  ): SelfHealingStatus {
    // Respect circuit breaker until next scheduled cooldown window
    if (consecutiveFailures >= MAX_RETRIES && nowMillis < circuitOpenUntilMillis) {
      logWriter.append("SELF_HEAL", "Circuit OPEN (trigger=$triggerSource). Deferring recovery until $circuitOpenUntilMillis.")
      lastStatus = SelfHealingStatus(
        circuitOpen = true,
        consecutiveFailures = consecutiveFailures,
        maxRetries = MAX_RETRIES,
        nextRetryAtMillis = circuitOpenUntilMillis,
        lastCheckMillis = nowMillis,
        lastCheckSummary = "Circuit breaker OPEN — deferred to next scheduled window",
        rotatedLogBytes = logWriter.totalSizeBytes(),
      )
      return lastStatus
    }

    // Respect exponential backoff window
    if (consecutiveFailures in 1 until MAX_RETRIES && nowMillis < nextAllowedRetryMillis) {
      return currentStatus()
    }

    // Reset circuit if cooldown expired
    if (consecutiveFailures >= MAX_RETRIES && nowMillis >= circuitOpenUntilMillis) {
      consecutiveFailures = 0
      circuitOpenUntilMillis = 0L
    }

    val healthy =
      if (checkOverride != null) {
        runCatching { checkOverride() }.getOrDefault(false)
      } else {
        runCatching {
          appDatabase.batteryDao().getLatestHourlySnapshot()
          preferencesRepository.preferences.first()
          NetraWorkScheduler.schedulePeriodicMaintenance(context)
          true
        }.getOrDefault(false)
      }

    if (healthy) {
      if (consecutiveFailures > 0) {
        logWriter.append("SELF_HEAL", "Recovered cleanly on trigger=$triggerSource after $consecutiveFailures failure(s).")
      }
      consecutiveFailures = 0
      nextAllowedRetryMillis = 0L
      circuitOpenUntilMillis = 0L
      lastStatus = SelfHealingStatus(
        circuitOpen = false,
        consecutiveFailures = 0,
        maxRetries = MAX_RETRIES,
        nextRetryAtMillis = 0L,
        lastCheckMillis = nowMillis,
        lastCheckSummary = "Healthy ($triggerSource)",
        rotatedLogBytes = logWriter.totalSizeBytes(),
      )
    } else {
      consecutiveFailures++
      if (consecutiveFailures >= MAX_RETRIES) {
        circuitOpenUntilMillis = nowMillis + CIRCUIT_COOLDOWN_MS
        nextAllowedRetryMillis = circuitOpenUntilMillis
        logWriter.append("SELF_HEAL", "Hard retry cap ($MAX_RETRIES) reached on $triggerSource. Opening circuit breaker for 6h.")
        lastStatus = SelfHealingStatus(
          circuitOpen = true,
          consecutiveFailures = consecutiveFailures,
          maxRetries = MAX_RETRIES,
          nextRetryAtMillis = circuitOpenUntilMillis,
          lastCheckMillis = nowMillis,
          lastCheckSummary = "Circuit breaker OPEN after $MAX_RETRIES failures",
          rotatedLogBytes = logWriter.totalSizeBytes(),
        )
      } else {
        val backoff = computeBackoffWithJitterMs(consecutiveFailures - 1)
        nextAllowedRetryMillis = nowMillis + backoff
        logWriter.append("SELF_HEAL", "Health check failed ($consecutiveFailures/$MAX_RETRIES) on $triggerSource; backoff ${backoff}ms.")
        lastStatus = SelfHealingStatus(
          circuitOpen = false,
          consecutiveFailures = consecutiveFailures,
          maxRetries = MAX_RETRIES,
          nextRetryAtMillis = nextAllowedRetryMillis,
          lastCheckMillis = nowMillis,
          lastCheckSummary = "Retry $consecutiveFailures/$MAX_RETRIES scheduled with backoff",
          rotatedLogBytes = logWriter.totalSizeBytes(),
        )
      }
    }

    return lastStatus
  }
}
