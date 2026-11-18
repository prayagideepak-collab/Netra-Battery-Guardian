package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnapshot(snapshot: BatterySnapshot): Long

  @Query("SELECT * FROM battery_snapshots ORDER BY timestampMillis DESC")
  fun observeAllSnapshots(): Flow<List<BatterySnapshot>>

  @Query("SELECT * FROM battery_snapshots WHERE isHourlyCheckpoint = 1 ORDER BY timestampMillis DESC LIMIT 168")
  fun observeHourlySnapshots(): Flow<List<BatterySnapshot>>

  @Query("SELECT * FROM battery_snapshots WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
  fun observeSnapshotsSince(sinceMillis: Long): Flow<List<BatterySnapshot>>

  @Query("SELECT * FROM battery_snapshots WHERE isHourlyCheckpoint = 1 ORDER BY timestampMillis DESC LIMIT 1")
  suspend fun getLatestHourlySnapshot(): BatterySnapshot?

  @Query("DELETE FROM battery_snapshots WHERE timestampMillis < :cutoffMillis")
  suspend fun deleteSnapshotsOlderThan(cutoffMillis: Long)

  @Query("DELETE FROM battery_snapshots")
  suspend fun deleteAllSnapshots()
}
