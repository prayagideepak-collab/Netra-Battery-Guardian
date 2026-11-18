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
  val estimatedBatterySavedPercent: Float = 0f,
  val estimatedMahSaved: Int = 0,
  val efficiencyScorePercent: Int = 0,
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

    // Conservative Android idle savings estimate (~1.45% / hr when Doze pauses wakeups vs unthrottled idle)
    private const val ESTIMATED_SAVED_PERCENT_PER_HOUR = 1.45f

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
    estimatedCapacityMah: Int = 4500,
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
      activeStartMillis ?: if (dozeState.isInDozeMode && dozeState.lastTransitionMillis > 0L) {
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

    val hoursInDoze = totalMillis.toFloat() / 3_600_000f
    val savedPercent = (hoursInDoze * ESTIMATED_SAVED_PERCENT_PER_HOUR).coerceAtLeast(0f)
    val capacityMah = estimatedCapacityMah.coerceAtLeast(1000)
    val savedMah = ((savedPercent / 100f) * capacityMah).roundToInt().coerceAtLeast(0)

    val score =
      if (totalMillis == 0L && transitionsCount == 0) {
        0
      } else {
        (35f + (hoursInDoze * 13f) + (completedCount * 7f)).roundToInt().coerceIn(20, 100)
      }

    val (ratingEn, ratingHi) =
      when {
        score >= 85 -> "Ultra Efficient" to "अत्यंत कुशल"
        score >= 55 -> "Optimal Saving" to "उत्तम बचत"
        transitionsCount > 0 || dozeState.isInDozeMode -> "Active Guard" to "सक्रिय सुरक्षा"
        else -> "Standby Ready" to "तैयार स्थिति"
      }

    return DozeEfficiencyMetrics(
      totalDozeTimeMillis = totalMillis,
      currentSessionMillis = currentSessionMillis,
      completedSessionsCount = completedCount,
      transitionsLoggedCount = transitionsCount,
      isCurrentlyInDoze = dozeState.isInDozeMode || activeStartMillis != null,
      estimatedBatterySavedPercent = savedPercent,
      estimatedMahSaved = savedMah,
      efficiencyScorePercent = score,
      efficiencyRatingLabelEn = ratingEn,
      efficiencyRatingLabelHi = ratingHi,
    )
  }
}
