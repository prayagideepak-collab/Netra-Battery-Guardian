package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.ChargingSessionEntity
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingSpeedClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=Application::class)
class DeviceTruthPersistenceTest {
  @Test fun v2UpgradePreservesSessionAndValidatesRoomSchema() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val name = "truth-upgrade-test.db"
    context.deleteDatabase(name)
    val initial = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
    initial.chargingSessionDao().insertSession(ChargingSessionEntity(startTimeMillis=1000L, endTimeMillis=2000L, startLevel=40, endLevel=52, peakTempCelsius=31f, avgPowerWatts=5f, maxPowerWatts=6f, speedClass="STANDARD", plugType="USB"))
    initial.close()
    // Build the actual v2 charging table shape, retaining all other v2 tables.
    SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
      db.execSQL("DROP INDEX index_charging_sessions_startTimeMillis")
      db.execSQL("ALTER TABLE charging_sessions RENAME TO old_sessions")
      db.execSQL("CREATE TABLE charging_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startTimeMillis INTEGER NOT NULL, endTimeMillis INTEGER, startLevel INTEGER NOT NULL, endLevel INTEGER NOT NULL, peakTempCelsius REAL NOT NULL, avgPowerWatts REAL NOT NULL, maxPowerWatts REAL NOT NULL, speedClass TEXT NOT NULL, plugType TEXT NOT NULL)")
      db.execSQL("INSERT INTO charging_sessions SELECT id,startTimeMillis,endTimeMillis,startLevel,endLevel,peakTempCelsius,avgPowerWatts,maxPowerWatts,speedClass,plugType FROM old_sessions")
      db.execSQL("DROP TABLE old_sessions")
      db.execSQL("CREATE INDEX index_charging_sessions_startTimeMillis ON charging_sessions(startTimeMillis)")
      db.version = 2
    }
    val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_2_3).build()
    try {
      val sessions = upgraded.chargingSessionDao().getAllChargingSessions()
      assertEquals(1, sessions.size)
      assertEquals(12, sessions.single().percentageGained)
      assertEquals(52, sessions.single().endLevel)
      assertEquals(1000L, sessions.single().startTimeMillis)
    } finally { upgraded.close(); context.deleteDatabase(name) }
  }

  @Test fun productionSessionWriterStartsAtZeroAndClosesWithObservedGain() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      val repository = BatteryHistoryRepository(db.netraDao(),db.batteryDao())
      val t = BatteryTelemetry(timestampMillis=1000L,levelPercent=40,isCharging=true,isPlugged=true)
      repository.startOrUpdateChargingSession(t,ChargingSpeedClass.UNAVAILABLE,Float.NaN)
      assertEquals(0,db.chargingSessionDao().getOpenSession()!!.percentageGained)
      repository.closeOpenChargingSession(t.copy(timestampMillis=2000L,levelPercent=43,isCharging=false,isPlugged=false))
      val closed = db.chargingSessionDao().getAllChargingSessions().single()
      assertEquals(3,closed.percentageGained)
      assertEquals(2000L,closed.endTimeMillis)
      assertFalse(closed.avgPowerWatts.isFinite())
    } finally { db.close() }
  }
}
