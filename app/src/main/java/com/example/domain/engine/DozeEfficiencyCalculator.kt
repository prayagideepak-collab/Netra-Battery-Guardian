package com.example.domain.engine

import com.example.data.local.db.ActivityLogEntity
import com.example.domain.model.LogCategory
import com.example.service.DozeMonitorState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class DozeEfficiencyMetrics(
  val totalDozeTimeMillis: Long = 0L,
  val currentSessionMillis: Long = 0L,
  val completedSessionsCount: Int = 0,
  val transitionsLoggedCount: Int = 0,
  val isCurrentlyInDoze: Boolean = false,
  val estimatedBatterySavedPercent: Float = Float.NaN,
  val estimatedMahSaved: Int = -1,
  val efficiencyScorePercent: Int = -1,
  val efficiencyRatingLabelEn: String = "Standby",
  val efficiencyRatingLabelHi: String = "स्टैंडबाय",
)

/**
 * Pure offline engine that computes cumulative Doze mode duration and conservative
 * energy-saving efficiency metrics directly from logged Doze transition events
 * (`Entered Doze Mode` / `Exited Doze Mode`) without background polling.
 */
@Singleton
class DozeEfficiencyCalculator @Inject constructor() {

  companion object {
    const val TITLE_ENTERED_DOZE = "Entered Doze Mode"
    const val TITLE_EXITED_DOZE = "Exited Doze Mode"

    fun formatDuration(millis: Long): String {
      val safeMillis = millis.coerceAtLeast(0L)
      val totalSeconds = safeMillis / 1000L
      val hours = totalSeconds / 3600L
      val minutes = (totalSeconds % 3600L) / 60L
      val seconds = totalSeconds % 60L
      return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m"
        totalSeconds > 0L -> "${seconds}s"
        else -> "0m"
      }
    }
  }

  fun calculate(
    activityLogs: List<ActivityLogEntity>,
    dozeState: DozeMonitorState,
    estimatedCapacityMah: Int = -1,
    nowMillis: Long = System.currentTimeMillis(),
  ): DozeEfficiencyMetrics {
    val dozeTransitions = activityLogs
      .filter {
        it.category == LogCategory.POWER_BUDGET.name &&
          (it.titleEn == TITLE_ENTERED_DOZE || it.titleEn == TITLE_EXITED_DOZE)
      }
      .sortedBy { it.timestampMillis }

    var activeStartMillis: Long? = null
    var completedDurationMillis = 0L
    var completedCount = 0

    for (entry in dozeTransitions) {
      when (entry.titleEn) {
        TITLE_ENTERED_DOZE -> {
          if (activeStartMillis == null) {
            activeStartMillis = entry.timestampMillis
          }
        }
        TITLE_EXITED_DOZE -> {
          val start = activeStartMillis
          if (start != null && entry.timestampMillis >= start) {
            completedDurationMillis += (entry.timestampMillis - start)
            completedCount++
            activeStartMillis = null
          }
        }
      }
    }

    val ongoingStartMillis =
      activeStartMillis?.takeIf { dozeState.isInDozeMode } ?: if (dozeState.isInDozeMode && dozeState.lastTransitionMillis > 0L) {
        dozeState.lastTransitionMillis
      } else {
        null
      }

    val currentSessionMillis =
      if (ongoingStartMillis != null && nowMillis >= ongoingStartMillis) {
        nowMillis - ongoingStartMillis
      } else {
        0L
      }

    val totalMillis = (completedDurationMillis + currentSessionMillis).coerceAtLeast(0L)
    val transitionsCount = maxOf(
      dozeTransitions.size,
      dozeState.enterDozeCount + dozeState.exitDozeCount,
    )

    // Transition duration is observable. Energy saved requires a verified
    // counterfactual baseline that Android does not supply here.
    val savedPercent = Float.NaN
    val savedMah = -1
    val score = -1
    val ratingEn = if (dozeState.isInDozeMode) "In Doze" else "Not in Doze"
    val ratingHi = if (dozeState.isInDozeMode) "डोज़ सक्रिय" else "डोज़ सक्रिय नहीं"

    return DozeEfficiencyMetrics(
      totalDozeTimeMillis = totalMillis,
      currentSessionMillis = currentSessionMillis,
      completedSessionsCount = completedCount,
      transitionsLoggedCount = transitionsCount,
      isCurrentlyInDoze = dozeState.isInDozeMode,
      estimatedBatterySavedPercent = savedPercent,
      estimatedMahSaved = savedMah,
      efficiencyScorePercent = score,
      efficiencyRatingLabelEn = ratingEn,
      efficiencyRatingLabelHi = ratingHi,
    )
  }
}
