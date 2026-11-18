package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.db.AppDatabase
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.service.DozeModeMonitor
import com.example.service.NetraWorkScheduler
import com.example.service.NotificationHelper
import com.example.service.ThermalSafetyMonitorWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThermalSafetyWorkerTest {

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var prefsRepo: PreferencesRepository
  private lateinit var telemetryCollector: BatteryTelemetryCollector
  private lateinit var temperatureEngine: TemperatureControlEngine
  private lateinit var notificationHelper: NotificationHelper
  private lateinit var logWriter: BoundedLogFileWriter
  private lateinit var dozeModeMonitor: DozeModeMonitor

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefsRepo = PreferencesRepository(context)
    runBlocking { prefsRepo.resetAllPreferences() }
    telemetryCollector = BatteryTelemetryCollector(context)
    temperatureEngine = TemperatureControlEngine()
    notificationHelper = NotificationHelper(context)
    logWriter = BoundedLogFileWriter(context)
    logWriter.clearLogs()
    dozeModeMonitor = DozeModeMonitor(context, logWriter, db)
  }

  @After
  fun tearDown() {
    dozeModeMonitor.unregister(DozeModeMonitor.OWNER_DEFAULT)
    db.close()
  }

  private fun buildWorker(): ThermalSafetyMonitorWorker =
    TestListenableWorkerBuilder<ThermalSafetyMonitorWorker>(context)
      .setWorkerFactory(
        object : WorkerFactory() {
          override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
          ): ListenableWorker =
            ThermalSafetyMonitorWorker(
              appContext = appContext,
              params = workerParameters,
              telemetryCollector = telemetryCollector,
              temperatureEngine = temperatureEngine,
              preferencesRepository = prefsRepo,
              appDatabase = db,
              notificationHelper = notificationHelper,
              logWriter = logWriter,
              dozeModeMonitor = dozeModeMonitor,
            )
        }
      )
      .build()

  @Test
  fun `buildThermalMonitorRequest configures 30-minute periodic WorkRequest`() {
    val req = NetraWorkScheduler.buildThermalMonitorRequest()
    assertTrue(req.workSpec.isPeriodic)
    assertEquals(1_800_000L, req.workSpec.intervalDuration)
  }

  @Test
  fun `ThermalSafetyMonitorWorker skips Room writes under normal temperature`() = runBlocking {
    val normalBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 64)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 335) // 33.5°C
      putExtra(BatteryManager.EXTRA_VOLTAGE, 3920)
      putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(normalBattery)

    val result = buildWorker().doWork()
    assertEquals(ListenableWorker.Result.success(), result)
    assertEquals(0, db.netraDao().observeActivityLogs().first().size)
    assertEquals(0, db.batteryDao().observeAllSnapshots().first().size)
  }

  @Test
  fun `ThermalSafetyMonitorWorker logs critical temperature event and snapshot to Room`() = runBlocking {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    Shadows.shadowOf(bm).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 72)

    val hotBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 72)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 475) // 47.5°C CRITICAL
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4210)
      putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_OVERHEAT)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(hotBattery)

    val result = buildWorker().doWork()
    assertEquals(ListenableWorker.Result.success(), result)

    val logs = db.netraDao().observeActivityLogs().first()
    assertEquals(1, logs.size)
    assertEquals(LogCategory.HEAT.name, logs[0].category)
    assertEquals(LogSeverity.CRITICAL.name, logs[0].severity)
    assertTrue(logs[0].titleEn.contains("47.5°C"))

    val snapshots = db.batteryDao().observeAllSnapshots().first()
    assertEquals(1, snapshots.size)
    assertEquals(47.5f, snapshots[0].temperatureCelsius, 0.05f)
    assertEquals("THERMAL_WORKER", snapshots[0].activeProfile)
  }

  @Test
  fun `ThermalSafetyMonitorWorker logs BatteryManager OVERHEAT health event to Room`() = runBlocking {
    val overheatBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 55)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 440) // 44.0°C with OS BATTERY_HEALTH_OVERHEAT
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4050)
      putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_OVERHEAT)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(overheatBattery)

    val result = buildWorker().doWork()
    assertEquals(ListenableWorker.Result.success(), result)

    val logs = db.netraDao().observeActivityLogs().first()
    assertEquals(1, logs.size)
    assertEquals(LogSeverity.CRITICAL.name, logs[0].severity)
    assertTrue(logs[0].detailEn.contains("BATTERY_HEALTH_OVERHEAT"))
  }
}
