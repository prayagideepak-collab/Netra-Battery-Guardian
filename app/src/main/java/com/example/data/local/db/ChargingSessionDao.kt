package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSession(session: ChargingSessionEntity): Long

  @Update
  suspend fun updateSession(session: ChargingSessionEntity): Int

  @Query("SELECT * FROM charging_sessions ORDER BY startTimeMillis DESC LIMIT 50")
  fun observeChargingSessions(): Flow<List<ChargingSessionEntity>>

  @Query("SELECT * FROM charging_sessions ORDER BY startTimeMillis DESC")
  suspend fun getAllChargingSessions(): List<ChargingSessionEntity>

  @Query("SELECT * FROM charging_sessions WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
  suspend fun getOpenSession(): ChargingSessionEntity?

  @Query("SELECT COALESCE(SUM(percentageGained), 0) FROM charging_sessions")
  fun observeTotalPercentageGained(): Flow<Int>

  @Query("DELETE FROM charging_sessions WHERE startTimeMillis < :cutoffMillis")
  suspend fun deleteSessionsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM charging_sessions")
  suspend fun clearAllSessions()

  /**
   * Records charging session transitions strictly when a meaningful charging state change occurs:
   * - Disconnected -> Plugged: inserts a new open [ChargingSessionEntity].
   * - Plugged -> Disconnected: closes the open session with endTimeMillis and percentageGained.
   * - Already Plugged: updates only when battery level %, charging speed class, plug type,
   *   or peak temperature (>= +0.5°C) changes. Redundant identical ticks perform zero DB writes.
   */
  @Transaction
  suspend fun recordChargingStateChange(
    isPlugged: Boolean,
    timestampMillis: Long,
    levelPercent: Int,
    temperatureCelsius: Float,
    powerWatts: Float,
    speedClass: String,
    plugType: String,
  ): Boolean {
    val open = getOpenSession()
    if (!isPlugged) {
      if (open == null) return false
      val gained = (levelPercent - open.startLevel).coerceAtLeast(0)
      updateSession(
        open.copy(
          endTimeMillis = timestampMillis,
          endLevel = levelPercent,
          percentageGained = gained,
          peakTempCelsius = maxOf(open.peakTempCelsius, temperatureCelsius),
          maxPowerWatts = maxOf(open.maxPowerWatts, powerWatts),
        )
      )
      return true
    }

    if (open == null) {
      insertSession(
        ChargingSessionEntity(
          startTimeMillis = timestampMillis,
          endTimeMillis = null,
          startLevel = levelPercent,
          endLevel = levelPercent,
          percentageGained = 0,
          peakTempCelsius = temperatureCelsius,
          avgPowerWatts = powerWatts,
          maxPowerWatts = powerWatts,
          speedClass = speedClass,
          plugType = plugType,
        )
      )
      return true
    }

    val levelChanged = levelPercent != open.endLevel
    val speedChanged = speedClass != open.speedClass
    val plugChanged = plugType != open.plugType
    val peakTempIncreased = temperatureCelsius >= open.peakTempCelsius + 0.5f

    if (!levelChanged && !speedChanged && !plugChanged && !peakTempIncreased) {
      return false
    }

    val gained = (levelPercent - open.startLevel).coerceAtLeast(0)
    updateSession(
      open.copy(
        endLevel = levelPercent,
        percentageGained = gained,
        peakTempCelsius = maxOf(open.peakTempCelsius, temperatureCelsius),
        avgPowerWatts = (open.avgPowerWatts + powerWatts) / 2f,
        maxPowerWatts = maxOf(open.maxPowerWatts, powerWatts),
        speedClass = speedClass,
        plugType = plugType,
      )
    )
    return true
  }
}
