package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.data.local.db.AppDatabase
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.engine.BatteryLifecycleEstimator
import com.example.service.DailyBatteryHealthWorker
import com.example.service.DozeModeMonitor
import com.example.service.NetraWorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryLifecycleAndDozeTest {

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var prefsRepo: PreferencesRepository
  private lateinit var logWriter: BoundedLogFileWriter
  private lateinit var lifecycleEstimator: BatteryLifecycleEstimator
  private lateinit var dozeMonitor: DozeModeMonitor

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefsRepo = PreferencesRepository(context)
    logWriter = BoundedLogFileWriter(context)
    logWriter.clearLogs()
    lifecycleEstimator = BatteryLifecycleEstimator(context, db, prefsRepo, logWriter)
    dozeMonitor = DozeModeMonitor(context, logWriter, db)
  }

  @After
  fun tearDown() {
    dozeMonitor.unregister(DozeModeMonitor.OWNER_DEFAULT)
    db.close()
  }

  @Test
  fun `BatteryLifecycleEstimator skips discharging and logs cycles and degradation once per day while charging`() = runBlocking {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val shadowBm = Shadows.shadowOf(bm)
    // Android counter supports approximate capacity, never an assumed design baseline or health.
    shadowBm.setLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, 4_050_000L)

    val day1Base = 20_000L * BatteryLifecycleEstimator.MILLIS_PER_DAY + 3_600_000L

    // 1. Discharging attempt must NOT log daily health entry
    val dischargeIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 40)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }
    val skippedRes = lifecycleEstimator.evaluateAndLogDailyWhileCharging(
      intentOverride = dischargeIntent,
      nowMillis = day1Base,
    )
    assertFalse(skippedRes.loggedNewEntry)
    assertEquals("Discharging must not seed a capacity baseline", 0, prefsRepo.preferences.first().designCapacityBaselineMah)
    assertEquals(0, db.netraDao().observeBatteryHealthLogs().first().size)

    // 2. Charging at 100% with OS cycle count = 42 -> logs once for Day 1
    val chargingIntentDay1 = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 100)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
      putExtra(BatteryManager.EXTRA_CYCLE_COUNT, 42)
    }
    val firstChargeRes = lifecycleEstimator.evaluateAndLogDailyWhileCharging(
      intentOverride = chargingIntentDay1,
      nowMillis = day1Base + 60_000L,
    )
    assertTrue("First charging evaluation on day 1 must log entry", firstChargeRes.loggedNewEntry)
    assertFalse(firstChargeRes.alreadyLoggedToday)
    val entry1 = firstChargeRes.latestEntry
    assertNotNull(entry1)
    assertEquals(42f, entry1!!.estimatedCycleCount, 0.01f)
    assertEquals(4050, entry1.estimatedCapacityMah)
    assertEquals(-1, entry1.designCapacityMah)
    assertEquals(-1f, entry1.degradationPercent, 0f)
    assertEquals(-1f, entry1.healthPercent, 0f)

    // 3. Second evaluation on the same day must be deduplicated
    val sameDayRes = lifecycleEstimator.evaluateAndLogDailyWhileCharging(
      intentOverride = chargingIntentDay1,
      nowMillis = day1Base + 2 * 3_600_000L,
    )
    assertFalse(sameDayRes.loggedNewEntry)
    assertTrue(sameDayRes.alreadyLoggedToday)
    assertEquals(1, db.netraDao().observeBatteryHealthLogs().first().size)

    // 4. Next day while charging -> logs Day 2 entry
    val nextDayRes = lifecycleEstimator.evaluateAndLogDailyWhileCharging(
      intentOverride = chargingIntentDay1,
      nowMillis = day1Base + BatteryLifecycleEstimator.MILLIS_PER_DAY + 60_000L,
    )
    assertTrue(nextDayRes.loggedNewEntry)
    assertEquals(2, db.netraDao().observeBatteryHealthLogs().first().size)
  }

  @Test
  fun `buildDailyBatteryHealthRequest enforces charging and daily cadence`() {
    val request = NetraWorkScheduler.buildDailyBatteryHealthRequest()
    assertTrue(request.workSpec.isPeriodic)
    assertTrue(request.workSpec.constraints.requiresCharging())
    assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
    assertEquals(86_400_000L, request.workSpec.intervalDuration)
  }

  @Test
  fun `DozeModeMonitor listens for ACTION_DEVICE_IDLE_MODE_CHANGED, logs transitions, and gates non-essential work`() = runBlocking {
    val shadowApp = Shadows.shadowOf(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val shadowPm = Shadows.shadowOf(pm)

    dozeMonitor.register(DozeModeMonitor.OWNER_DEFAULT)
    assertTrue(dozeMonitor.state.value.isRegistered)
    assertFalse(dozeMonitor.shouldSkipNonEssentialWork())

    val hasDozeReceiver = shadowApp.registeredReceivers.any { wrapper ->
      wrapper.intentFilter.hasAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
    }
    assertTrue("DozeModeMonitor must register ACTION_DEVICE_IDLE_MODE_CHANGED", hasDozeReceiver)

    // Transition into OS Doze mode via broadcast
    shadowPm.setIsDeviceIdleMode(true)
    context.sendBroadcast(Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val entered = dozeMonitor.state.value
    assertTrue(entered.isInDozeMode)
    assertTrue(entered.nonEssentialWorkSuspended)
    assertEquals(1, entered.enterDozeCount)
    assertTrue(dozeMonitor.shouldSkipNonEssentialWork())

    // Verify DailyBatteryHealthWorker skips non-essential execution during Doze
    val chargingSticky = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 80)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(chargingSticky)

    val worker = androidx.work.testing.TestListenableWorkerBuilder<DailyBatteryHealthWorker>(context)
      .setWorkerFactory(
        object : WorkerFactory() {
          override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
          ): ListenableWorker {
            return DailyBatteryHealthWorker(
              appContext = appContext,
              params = workerParameters,
              batteryLifecycleEstimator = lifecycleEstimator,
              dozeModeMonitor = dozeMonitor,
            )
          }
        }
      )
      .build()

    val workerResult = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), workerResult)
    assertEquals(
      "Non-essential daily worker must skip while device is in Doze mode",
      0,
      db.netraDao().observeBatteryHealthLogs().first().size,
    )

    // Transition out of Doze mode
    shadowPm.setIsDeviceIdleMode(false)
    context.sendBroadcast(Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val exited = dozeMonitor.state.value
    assertFalse(exited.isInDozeMode)
    assertFalse(exited.nonEssentialWorkSuspended)
    assertEquals(1, exited.exitDozeCount)
    assertFalse(dozeMonitor.shouldSkipNonEssentialWork())

    val logText = File(context.filesDir, "logs/netra_health_0.log").readText()
    assertTrue("Log must record Doze entry", logText.contains("[DOZE] Entered OS Doze mode"))
    assertTrue("Log must record Doze exit", logText.contains("[DOZE] Exited OS Doze mode"))
  }
}
