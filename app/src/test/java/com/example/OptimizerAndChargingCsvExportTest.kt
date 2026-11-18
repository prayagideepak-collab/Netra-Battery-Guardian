package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatteryDao
import com.example.data.local.db.BatteryHealthLogEntity
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.db.DischargeSessionEntity
import com.example.data.local.prefs.UserPreferences
import com.example.data.local.system.BoundedLogFileWriter
import com.example.data.local.system.LocalReportExporter
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.engine.BatteryHealthOptimizer
import com.example.domain.engine.OptimizationPriority
import com.example.domain.engine.OptimizerActionTarget
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppPowerInsight
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.PowerBudgetMetrics
import com.example.service.ChargingCsvExportWorker
import com.example.service.DozeModeMonitor
import com.example.service.DozeMonitorState
import com.example.service.NetraWorkScheduler
import com.example.ui.NetraUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.BatteryScreen
import com.example.ui.theme.NetraBatteryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OptimizerAndChargingCsvExportTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var historyRepo: BatteryHistoryRepository
  private lateinit var logWriter: BoundedLogFileWriter
  private lateinit var exporter: LocalReportExporter
  private lateinit var optimizer: BatteryHealthOptimizer
  private lateinit var dozeMonitor: DozeModeMonitor

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    historyRepo = BatteryHistoryRepository(db.netraDao(), db.batteryDao(), db.chargingSessionDao())
    logWriter = BoundedLogFileWriter(context)
    logWriter.clearLogs()
    exporter = LocalReportExporter(context, historyRepo, logWriter)
    optimizer = BatteryHealthOptimizer()
    dozeMonitor = DozeModeMonitor(context, logWriter, db)
  }

  @After
  fun tearDown() {
    dozeMonitor.unregister(DozeModeMonitor.OWNER_DEFAULT)
    db.close()
  }

  @Test
  fun `BatteryHealthOptimizer offers Doze and heavy app advice and throttles in Doze or Survival`() {
    val normalBudget = PowerBudgetMetrics(AdaptivePowerTier.NORMAL, 0, 0, 1, 15, false)
    val heavyApp = AppPowerInsight(
      packageName = "com.heavy.video",
      appName = "Heavy Video",
      foregroundMinutesToday = 45,
      estimatedWakeups = 12,
      batteryRank = 1,
      estimatedDrainSharePercent = 38f,
      hasLocationPermission = false,
      isBatteryOptimized = false,
      isLikelyGame = false,
      recommendedActionEn = "Restrict",
      recommendedActionHi = "सीमित करें",
      whyEn = "Heavy foreground",
      whyHi = "भारी उपयोग",
    )
    val telemetry = BatteryTelemetry(
      levelPercent = 42,
      isCharging = false,
      temperatureCelsius = 39.2f,
      osBatterySaverOn = false,
    )
    val abnormalDischarge = DischargeSessionEntity(
      startTimeMillis = System.currentTimeMillis() - 7_200_000L,
      endTimeMillis = System.currentTimeMillis() - 3_600_000L,
      startLevel = 60,
      endLevel = 42,
      screenOnMinutes = 10,
      screenOffMinutes = 50,
      minTempCelsius = 31f,
      maxTempCelsius = 39f,
      abnormalDrainDetected = true,
    )

    val report = optimizer.analyze(
      telemetry = telemetry,
      prefs = UserPreferences(),
      powerBudget = normalBudget,
      dozeState = DozeMonitorState(isInDozeMode = false),
      topApps = listOf(heavyApp),
      recentDischarges = listOf(abnormalDischarge),
    )

    assertFalse(report.throttledDueToLowPower)
    assertEquals(-1, report.healthScore)
    assertTrue(
      "Must recommend closing/restricting heavy app",
      report.recommendations.any { it.actionTarget == OptimizerActionTarget.APP_INFO && it.packageHint == "com.heavy.video" },
    )
    assertTrue(
      "Must recommend Doze & Battery Optimization settings",
      report.recommendations.any {
        it.actionTarget == OptimizerActionTarget.BATTERY_OPTIMIZATION_SETTINGS ||
          it.actionTarget == OptimizerActionTarget.BATTERY_SAVER_SETTINGS
      },
    )
    assertFalse("Celsius alone must not assert an OEM thermal limit",
      report.recommendations.any { it.actionTarget == OptimizerActionTarget.THERMAL_COOLING_MODE })
    val osOverheatReport = optimizer.analyze(telemetry.copy(healthState=com.example.domain.model.BatteryHealthState.OVERHEAT), UserPreferences(), normalBudget, DozeMonitorState(), emptyList())
    assertTrue(osOverheatReport.recommendations.any { it.actionTarget == OptimizerActionTarget.THERMAL_COOLING_MODE })

    // In Doze / SURVIVAL mode, heavy app advice is skipped to honor ultra-low-power constraints
    val survivalBudget = PowerBudgetMetrics(AdaptivePowerTier.SURVIVAL, 0, 0, 1, 180, false)
    val lowPowerReport = optimizer.analyze(
      telemetry = telemetry.copy(levelPercent = 4, temperatureCelsius = 30f),
      prefs = UserPreferences(),
      powerBudget = survivalBudget,
      dozeState = DozeMonitorState(isInDozeMode = true),
      topApps = listOf(heavyApp),
      recentDischarges = listOf(abnormalDischarge),
    )
    assertTrue(lowPowerReport.throttledDueToLowPower)
    assertFalse(
      "Heavy app heuristic must be skipped during Doze / SURVIVAL",
      lowPowerReport.recommendations.any { it.actionTarget == OptimizerActionTarget.APP_INFO },
    )
    assertEquals(OptimizationPriority.URGENT, lowPowerReport.recommendations.first().priority)
  }

  @Test
  fun `CSV export triggers only when device is charging and includes health and temperature logs`() = runBlocking {
    val now = System.currentTimeMillis()
    db.netraDao().insertBatteryHealthLog(
      BatteryHealthLogEntity(
        dayBucket = now / 86_400_000L,
        timestampMillis = now,
        estimatedCycleCount = 34.5f,
        designCapacityMah = 4500,
        estimatedCapacityMah = 4185,
        degradationPercent = 7.0f,
        healthPercent = 93.0f,
        sourceLabel = "OBSERVED_V2_OS_CYCLE_AND_COUNTER_OR_UNAVAILABLE",
      )
    )
    db.batteryDao().insertSnapshot(
      BatterySnapshot(
        timestampMillis = now - 300_000L,
        levelPercent = 76,
        voltageMv = 4120,
        temperatureCelsius = 36.4f,
        isCharging = true,
      )
    )

    // 1. Discharging -> MUST NOT export CSV
    val dischargingIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }
    val blocked = exporter.exportBatteryHealthAndTempCsvIfCharging(intentOverride = dischargingIntent)
    assertFalse("CSV export must be blocked while discharging", blocked.exported)
    assertNull(blocked.file)

    // 2. Charging -> MUST export CSV containing HEALTH_LOG and TEMP_SNAPSHOT rows
    val chargingIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    val exported = exporter.exportBatteryHealthAndTempCsvIfCharging(intentOverride = chargingIntent)
    assertTrue("CSV export must succeed while charging", exported.exported)
    assertEquals(1, exported.healthRows)
    assertEquals(1, exported.snapshotRows)
    val csvContent = exported.file!!.readText()
    assertTrue(csvContent.contains("HEALTH_LOG"))
    assertTrue(csvContent.contains("34.5,,4185,,,"))
    assertFalse(csvContent.contains("4500"))
    exporter.clearPrivateReports()
    assertFalse(exported.file!!.exists())
    assertTrue(csvContent.contains("TEMP_SNAPSHOT"))
    assertTrue(csvContent.contains(",76,36.4,4120,"))
  }

  @Test
  fun `ChargingCsvExportWorker enforces requiresCharging constraint and exports when plugged`() = runBlocking {
    val req = NetraWorkScheduler.buildChargingCsvExportRequest()
    assertTrue("CSV export worker must require charging", req.workSpec.constraints.requiresCharging())

    val chargingIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(chargingIntent)

    val worker = androidx.work.testing.TestListenableWorkerBuilder<ChargingCsvExportWorker>(context)
      .setWorkerFactory(
        object : WorkerFactory() {
          override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
          ): ListenableWorker {
            return ChargingCsvExportWorker(
              appContext = appContext,
              params = workerParameters,
              reportExporter = exporter,
              dozeModeMonitor = dozeMonitor,
            )
          }
        }
      )
      .build()

    val res = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), res)
  }

  @Test
  fun `BatteryScreen renders Optimizer advice and gates CSV export button by charging state`() {
    val optimizerReport = optimizer.analyze(
      telemetry = BatteryTelemetry(levelPercent = 40, isCharging = false, temperatureCelsius = 39.5f),
      prefs = UserPreferences(),
      powerBudget = PowerBudgetMetrics(AdaptivePowerTier.NORMAL, 0, 0, 0, 15, false),
      dozeState = DozeMonitorState(),
      topApps = emptyList(),
    )

    var exportClicked = false
    val dischargingState = NetraUiState(
      telemetry = BatteryTelemetry(levelPercent = 40, isCharging = false, temperatureCelsius = 39.5f),
      optimizationReport = optimizerReport,
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        BatteryScreen(
          state = dischargingState,
          strings = AppStrings(AppLanguage.ENGLISH),
          onExportCsv = { exportClicked = true },
        )
      }
    }
    composeTestRule.waitForIdle()

    val list = composeTestRule.onNodeWithTag("battery_screen_list")
    list.performScrollToNode(hasTestTag("battery_export_csv_button"))
    composeTestRule.onNodeWithTag("battery_export_csv_button")
      .assertIsDisplayed()
      .assertIsNotEnabled()
    list.performScrollToNode(hasTestTag("queue_charging_csv_button"))
    composeTestRule.onNodeWithTag("queue_charging_csv_button")
      .assertIsDisplayed()
    list.performScrollToNode(hasTestTag("battery_health_optimizer_card"))
    composeTestRule.onNodeWithTag("battery_health_optimizer_card")
      .assertIsDisplayed()
    assertFalse(exportClicked)
  }
}
