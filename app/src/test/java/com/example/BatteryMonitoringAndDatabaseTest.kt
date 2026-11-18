package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryMonitoringAndDatabaseTest {

  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `AppDatabase and BatteryDao store and query hourly BatterySnapshot metrics`() = runBlocking {
    val dao = db.batteryDao()
    val now = 1_700_000_000_000L

    dao.insertSnapshot(
      BatterySnapshot(
        timestampMillis = now,
        levelPercent = 72,
        voltageMv = 4120,
        temperatureCelsius = 33.4f,
        isCharging = true,
        chargingStatus = "CHARGING",
        plugType = "AC",
        isHourlyCheckpoint = true,
      )
    )
    dao.insertSnapshot(
      BatterySnapshot(
        timestampMillis = now + 3_600_000L,
        levelPercent = 94,
        voltageMv = 4310,
        temperatureCelsius = 35.1f,
        isCharging = false,
        chargingStatus = "DISCHARGING",
        plugType = "NONE",
        isHourlyCheckpoint = true,
      )
    )

    val hourly = dao.observeHourlySnapshots().first()
    assertEquals(2, hourly.size)
    assertEquals(4310, hourly.first().voltageMv)
    assertEquals(35.1f, hourly.first().temperatureCelsius, 0.01f)
    assertEquals("DISCHARGING", hourly.first().chargingStatus)

    val latest = dao.getLatestHourlySnapshot()
    assertNotNull(latest)
    assertEquals(94, latest?.levelPercent)
  }

  @Test
  fun `BatteryMonitoringService registers ACTION_BATTERY_CHANGED receiver`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shadowApp = Shadows.shadowOf(context as android.app.Application)

    // Broadcast a sticky battery intent before starting service
    val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 80)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4150)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 315)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    context.sendStickyBroadcast(stickyBattery)

    val controller = Robolectric.buildService(com.example.service.BatteryMonitoringService::class.java)
    controller.create().startCommand(0, 0)

    val hasBatteryReceiver = shadowApp.registeredReceivers.any { wrapper ->
      wrapper.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED)
    }
    assertTrue("BatteryMonitoringService must register ACTION_BATTERY_CHANGED", hasBatteryReceiver)

    controller.destroy()
  }
}
