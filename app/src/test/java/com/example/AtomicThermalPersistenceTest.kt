package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AtomicThermalPersistenceTest {
  private fun snapshot(time: Long) = BatterySnapshot(timestampMillis = time, levelPercent = 42,
    voltageMv = 4000, temperatureCelsius = 40f, isCharging = false, thermalLevel = "CRITICAL")
  private fun log(time: Long) = ActivityLogEntity(timestampMillis = time, category = "HEAT",
    severity = "CRITICAL", titleEn = "OS warning", titleHi = "OS warning", detailEn = "observed",
    detailHi = "observed", whyEn = "OS", whyHi = "OS")
  private fun open() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
    .allowMainThreadQueries().build()

  @Test fun secondWriteFailureRollsBackTheSnapshot() = runBlocking {
    val db = open()
    try {
      db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_critical_log BEFORE INSERT ON activity_logs BEGIN SELECT RAISE(ABORT, 'injected second-write failure'); END")
      var failed = false
      try { db.netraDao().recordCriticalThermalEvent(snapshot(1), log(1)) } catch (_: Exception) { failed = true }
      assertTrue(failed)
      assertTrue(db.batteryDao().observeAllSnapshots().first().isEmpty())
      assertTrue(db.netraDao().getAllLogsForExport().isEmpty())
    } finally { db.close() }
  }
  @Test fun concurrentEventsRemainPaired() = runBlocking {
    val db = open()
    try {
      coroutineScope { (1L..8L).map { time -> async(Dispatchers.Default) {
        db.netraDao().recordCriticalThermalEvent(snapshot(time), log(time))
      } }.awaitAll() }
      assertEquals((1L..8L).toSet(), db.batteryDao().observeAllSnapshots().first().map { it.timestampMillis }.toSet())
      assertEquals((1L..8L).toSet(), db.netraDao().getAllLogsForExport().map { it.timestampMillis }.toSet())
    } finally { db.close() }
  }
  @Test fun committedPairSurvivesDatabaseReopen() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val name = "m3-critical-reopen-test.db"
    context.deleteDatabase(name)
    fun build() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
    var db = build()
    try {
      db.netraDao().recordCriticalThermalEvent(snapshot(123), log(123))
      db.close()
      db = build()
      assertEquals(listOf(123L), db.batteryDao().observeAllSnapshots().first().map { it.timestampMillis })
      assertEquals(listOf(123L), db.netraDao().getAllLogsForExport().map { it.timestampMillis })
    } finally { db.close(); context.deleteDatabase(name) }
  }
}
