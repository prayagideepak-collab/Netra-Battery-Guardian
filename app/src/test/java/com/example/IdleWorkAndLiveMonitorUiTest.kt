package com.example

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.data.local.db.AppDatabase
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.model.AppLanguage
import com.example.service.BatteryMonitoringService
import com.example.service.BatteryServiceState
import com.example.service.IdleBatteryPersistenceWorker
import com.example.service.NetraWorkScheduler
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.LiveMonitorContent
import com.example.ui.screens.LiveMonitorScreen
import com.example.ui.theme.NetraBatteryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdleWorkAndLiveMonitorUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `buildIdlePersistenceRequest enforces requiresDeviceIdle and requiresBatteryNotLow constraints`() {
    val request = NetraWorkScheduler.buildIdlePersistenceRequest()
    val constraints = request.workSpec.constraints

    assertTrue("Idle persistence worker must require device idle", constraints.requiresDeviceIdle())
    assertTrue("Idle persistence worker must require battery not low", constraints.requiresBatteryNotLow())
    assertTrue("Idle persistence worker must be periodic", request.workSpec.isPeriodic)
    assertEquals(3_600_000L, request.workSpec.intervalDuration)
  }

  @Test
  fun `IdleBatteryPersistenceWorker persists BatterySnapshot to Room database`() = runBlocking {
    val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 67)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4050)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 342)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(stickyBattery)

    val telemetryCollector = BatteryTelemetryCollector(context)
    val prefsRepo = PreferencesRepository(context)
    val classifier = ChargingSpeedClassifier()

    val factory = object : WorkerFactory() {
      override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
      ): ListenableWorker {
        return IdleBatteryPersistenceWorker(
          appContext = appContext,
          params = workerParameters,
          telemetryCollector = telemetryCollector,
          preferencesRepository = prefsRepo,
          chargingClassifier = classifier,
          appDatabase = db,
        )
      }
    }

    val worker = androidx.work.testing.TestListenableWorkerBuilder<IdleBatteryPersistenceWorker>(context)
      .setWorkerFactory(factory)
      .build()

    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)

    val saved = db.batteryDao().observeHourlySnapshots().first()
    assertEquals(1, saved.size)
    assertEquals(67, saved.first().levelPercent)
    assertEquals(34.2f, saved.first().temperatureCelsius, 0.05f)
    assertEquals("CHARGING", saved.first().chargingStatus)
    assertEquals("IDLE_PERIODIC", saved.first().activeProfile)
  }

  @Test
  fun `BatteryMonitoringService publishes real-time serviceState and updates on destroy`() {
    val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 84)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4210)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 368)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(stickyBattery)

    val controller = Robolectric.buildService(BatteryMonitoringService::class.java)
    controller.create().startCommand(0, 0)

    val activeState = BatteryMonitoringService.serviceState.value
    assertTrue(activeState.isRunning)
    assertEquals(84, activeState.levelPercent)
    assertEquals(36.8f, activeState.temperatureCelsius, 0.05f)
    assertEquals("CHARGING", activeState.chargingStatus)
    assertEquals("USB", activeState.plugType)

    controller.destroy()
    assertFalse(BatteryMonitoringService.serviceState.value.isRunning)
  }

  @Test
  fun `LiveMonitorContent displays battery percentage temperature and charging status`() {
    val sampleState = BatteryServiceState(
      isRunning = true,
      levelPercent = 78,
      temperatureCelsius = 35.6f,
      voltageMv = 4180,
      currentMa = 1450f,
      chargingStatus = "CHARGING",
      isCharging = true,
      isPlugged = true,
      plugType = "AC",
      lastUpdatedMillis = 1_700_000_000_000L,
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        LiveMonitorContent(
          serviceState = sampleState,
          latestHourlySnapshot = null,
          strings = AppStrings(AppLanguage.ENGLISH),
          onToggleService = {},
          onNavigateBack = {},
          onOpenBatteryHistory = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("monitoring_service_status_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("service_battery_percent")
      .assertIsDisplayed()
      .assertTextContains("78%", substring = true)
    composeTestRule.onNodeWithTag("service_temperature")
      .assertIsDisplayed()
      .assertTextContains("35.6°C", substring = true)
    composeTestRule.onNodeWithTag("service_charging_status")
      .assertIsDisplayed()
      .assertTextContains("CHARGING", substring = true)
    composeTestRule.onNodeWithText("Plug: AC").assertIsDisplayed()
  }

  @Test
  fun `LiveMonitorScreen observes BatteryMonitoringService serviceState reactively`() {
    val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 52)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 3920)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 314)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(stickyBattery)

    val controller = Robolectric.buildService(BatteryMonitoringService::class.java)
    controller.create().startCommand(0, 0)

    composeTestRule.setContent {
      NetraBatteryTheme {
        LiveMonitorScreen(
          latestHourlySnapshot = null,
          strings = AppStrings(AppLanguage.ENGLISH),
          onNavigateBack = {},
          onOpenBatteryHistory = {},
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("service_battery_percent")
      .assertIsDisplayed()
      .assertTextContains("52%", substring = true)
    composeTestRule.onNodeWithTag("service_temperature")
      .assertIsDisplayed()
      .assertTextContains("31.4°C", substring = true)
    composeTestRule.onNodeWithTag("service_charging_status")
      .assertIsDisplayed()
      .assertTextContains("DISCHARGING", substring = true)

    controller.destroy()
  }
}
