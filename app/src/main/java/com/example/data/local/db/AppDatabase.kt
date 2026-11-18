package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    BatterySnapshot::class,
    ChargingSessionEntity::class,
    DischargeSessionEntity::class,
    ActivityLogEntity::class,
    BatteryHealthLogEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun batteryDao(): BatteryDao
  abstract fun netraDao(): NetraDao
  abstract fun chargingSessionDao(): ChargingSessionDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `battery_health_logs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `dayBucket` INTEGER NOT NULL,
            `timestampMillis` INTEGER NOT NULL,
            `estimatedCycleCount` REAL NOT NULL,
            `designCapacityMah` INTEGER NOT NULL,
            `estimatedCapacityMah` INTEGER NOT NULL,
            `degradationPercent` REAL NOT NULL,
            `healthPercent` REAL NOT NULL,
            `sourceLabel` TEXT NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_battery_health_logs_dayBucket` ON `battery_health_logs` (`dayBucket`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_health_logs_timestampMillis` ON `battery_health_logs` (`timestampMillis`)")
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `charging_sessions` ADD COLUMN `percentageGained` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
          "UPDATE `charging_sessions` SET `percentageGained` = MAX(0, `endLevel` - `startLevel`)"
        )
      }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "battery_guardian.db",
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
          .fallbackToDestructiveMigration(dropAllTables = false)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
