package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    BatterySnapshot::class,
    ChargingSessionEntity::class,
    DischargeSessionEntity::class,
    ActivityLogEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class BatteryDatabase : RoomDatabase() {
  abstract fun batterySnapshotDao(): BatterySnapshotDao
  abstract fun netraDao(): NetraDao

  companion object {
    @Volatile
    private var INSTANCE: BatteryDatabase? = null

    fun getInstance(context: Context): BatteryDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          BatteryDatabase::class.java,
          "battery_guardian.db",
        )
          .fallbackToDestructiveMigration(dropAllTables = false)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
