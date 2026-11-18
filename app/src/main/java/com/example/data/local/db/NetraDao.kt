package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NetraDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnapshot(snapshot: BatterySnapshot): Long

  @Query("SELECT * FROM battery_snapshots WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
  fun observeSnapshotsSince(sinceMillis: Long): Flow<List<BatterySnapshot>>

  @Query("SELECT * FROM battery_snapshots WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
  suspend fun getSnapshotsSince(sinceMillis: Long): List<BatterySnapshot>

  @Query("SELECT * FROM battery_snapshots ORDER BY timestampMillis DESC LIMIT 1")
  fun observeLatestSnapshot(): Flow<BatterySnapshot?>

  @Query("SELECT COUNT(*) FROM battery_snapshots WHERE timestampMillis >= :sinceMillis")
  fun observeSnapshotCountSince(sinceMillis: Long): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChargingSession(session: ChargingSessionEntity): Long

  @Update
  suspend fun updateChargingSession(session: ChargingSessionEntity)

  @Query("SELECT * FROM charging_sessions ORDER BY startTimeMillis DESC LIMIT 30")
  fun observeRecentChargingSessions(): Flow<List<ChargingSessionEntity>>

  @Query("SELECT * FROM charging_sessions WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
  suspend fun getOpenChargingSession(): ChargingSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDischargeSession(session: DischargeSessionEntity): Long

  @Update
  suspend fun updateDischargeSession(session: DischargeSessionEntity)

  @Query("SELECT * FROM discharge_sessions ORDER BY startTimeMillis DESC LIMIT 30")
  fun observeRecentDischargeSessions(): Flow<List<DischargeSessionEntity>>

  @Query("SELECT * FROM discharge_sessions WHERE endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
  suspend fun getOpenDischargeSession(): DischargeSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertActivityLog(log: ActivityLogEntity): Long

  @Query("SELECT * FROM activity_logs ORDER BY timestampMillis DESC LIMIT 200")
  fun observeActivityLogs(): Flow<List<ActivityLogEntity>>

  @Query("SELECT * FROM activity_logs ORDER BY timestampMillis DESC LIMIT 500")
  suspend fun getAllLogsForExport(): List<ActivityLogEntity>

  @Query("SELECT * FROM charging_sessions ORDER BY startTimeMillis DESC LIMIT 100")
  suspend fun getAllChargingSessionsForExport(): List<ChargingSessionEntity>

  @Query("SELECT * FROM discharge_sessions ORDER BY startTimeMillis DESC LIMIT 100")
  suspend fun getAllDischargeSessionsForExport(): List<DischargeSessionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBatteryHealthLog(entry: BatteryHealthLogEntity): Long

  @Query("SELECT * FROM battery_health_logs WHERE dayBucket = :dayBucket LIMIT 1")
  suspend fun getBatteryHealthLogForDay(dayBucket: Long): BatteryHealthLogEntity?

  @Query("SELECT * FROM battery_health_logs ORDER BY timestampMillis DESC LIMIT 60")
  fun observeBatteryHealthLogs(): Flow<List<BatteryHealthLogEntity>>

  @Query("DELETE FROM battery_health_logs WHERE timestampMillis < :cutoffMillis")
  suspend fun deleteBatteryHealthLogsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM battery_health_logs")
  suspend fun clearBatteryHealthLogs()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBatteryHealthLog(entry: BatteryHealthLogEntity): Long

  @Query("SELECT * FROM battery_health_logs WHERE dayBucket = :dayBucket LIMIT 1")
  suspend fun getBatteryHealthLogForDay(dayBucket: Long): BatteryHealthLogEntity?

  @Query("SELECT * FROM battery_health_logs ORDER BY timestampMillis DESC LIMIT 60")
  fun observeBatteryHealthLogs(): Flow<List<BatteryHealthLogEntity>>

  @Query("DELETE FROM battery_health_logs WHERE timestampMillis < :cutoffMillis")
  suspend fun deleteBatteryHealthLogsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM battery_health_logs")
  suspend fun clearBatteryHealthLogs()

  @Query("DELETE FROM battery_snapshots WHERE timestampMillis < :cutoffMillis")
  suspend fun deleteSnapshotsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM charging_sessions WHERE startTimeMillis < :cutoffMillis")
  suspend fun deleteChargingSessionsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM discharge_sessions WHERE startTimeMillis < :cutoffMillis")
  suspend fun deleteDischargeSessionsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM activity_logs WHERE timestampMillis < :cutoffMillis")
  suspend fun deleteLogsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM battery_snapshots")
  suspend fun clearSnapshots()

  @Query("DELETE FROM charging_sessions")
  suspend fun clearChargingSessions()

  @Query("DELETE FROM discharge_sessions")
  suspend fun clearDischargeSessions()

  @Query("DELETE FROM activity_logs")
  suspend fun clearActivityLogs()
}
