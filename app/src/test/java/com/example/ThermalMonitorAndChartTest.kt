package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.TemperatureZone
import com.example.service.NotificationHelper
import com.example.service.ThermalMonitor
import com.example.ui.components.HourlyTempVoltageChart
import com.example.ui.theme.NetraBatteryTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThermalMonitorAndChartTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var thermalMonitor: ThermalMonitor

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    thermalMonitor = ThermalMonitor(
      context = context,
      telemetryCollector = BatteryTelemetryCollector(context),
      temperatureEngine = TemperatureControlEngine(),
      adaptivePowerTierEngine = AdaptivePowerTierEngine(),
      notificationHelper = NotificationHelper(context),
    )
  }

  @After
  fun tearDown() {
    thermalMonitor.unregister(ThermalMonitor.DEFAULT_OWNER)
    db.close()
  }

  @Test
  fun `ThermalMonitor registers ACTION_BATTERY_CHANGED and posts local notification on high temp`() {
    val shadowApp = Shadows.shadowOf(context)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)

    thermalMonitor.register(ThermalMonitor.DEFAULT_OWNER)
    assertTrue(thermalMonitor.state.value.isRegistered)

    val hasReceiver = shadowApp.registeredReceivers.any { wrapper ->
      wrapper.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED)
    }
    assertTrue("ThermalMonitor must register ACTION_BATTERY_CHANGED", hasReceiver)

    // Broadcast high temperature (41.2°C) while in NORMAL battery range (65%)
    val hotIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 65)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4120)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 412)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(hotIntent)
    thermalMonitor.processBatteryIntent(hotIntent)

    val st = thermalMonitor.state.value
    assertEquals(TemperatureZone.UNAVAILABLE, st.zone)
    assertFalse(st.isThermalThrottled)
    assertEquals(AdaptivePowerTier.NORMAL, st.activeTier)
    assertEquals(0, st.alertCount)

    val posted = shadowNm.getNotification(NotificationHelper.ID_THERMAL_ALERT)
    assertTrue("Celsius alone must not claim an OEM thermal limit", posted == null)

    // Second HOT reading within hysteresis/cooldown must not spam notifications
    val hotAgainIntent = Intent(hotIntent).apply {
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 418)
    }
    thermalMonitor.processBatteryIntent(hotAgainIntent)
    assertEquals(0, thermalMonitor.state.value.alertCount)

    thermalMonitor.unregister(ThermalMonitor.DEFAULT_OWNER)
    assertFalse(thermalMonitor.state.value.isRegistered)
  }

  @Test
  fun `ThermalMonitor enforces power tier limits in SURVIVAL and still triggers Tier-0 critical heat`() {
    thermalMonitor.register(ThermalMonitor.DEFAULT_OWNER)

    // At 3% battery (SURVIVAL), 41.0°C (HOT) should throttle without firing non-Tier0 advisory alert
    val survivalHot = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 3)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 3620)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 410)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
    }
    thermalMonitor.processBatteryIntent(survivalHot)

    assertEquals(AdaptivePowerTier.SURVIVAL, thermalMonitor.state.value.activeTier)
    assertEquals(0, thermalMonitor.state.value.alertCount)

    // At 3% battery (SURVIVAL), 46.8°C (CRITICAL Tier-0 safety) MUST notify immediately
    val survivalCritical = Intent(survivalHot).apply {
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 468)
      putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_OVERHEAT)
    }
    thermalMonitor.processBatteryIntent(survivalCritical)

    assertEquals(TemperatureZone.CRITICAL, thermalMonitor.state.value.zone)
    assertEquals(1, thermalMonitor.state.value.alertCount)
    assertTrue(thermalMonitor.state.value.lastNotificationTitle?.contains("Critical") == true)
  }

  @Test
  fun osSevereWarningIsNotSuppressedBySurvivalPowerMode() {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    Shadows.shadowOf(pm).setCurrentThermalStatus(android.os.PowerManager.THERMAL_STATUS_SEVERE)
    val sample = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 3)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
      putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
    }
    NotificationHelper(context).createChannels()
    thermalMonitor.processBatteryIntent(sample)
    assertEquals(AdaptivePowerTier.SURVIVAL, thermalMonitor.state.value.activeTier)
    assertEquals(TemperatureZone.VERY_HOT, thermalMonitor.state.value.zone)
    assertEquals(1, thermalMonitor.state.value.alertCount)
    thermalMonitor.processBatteryIntent(sample)
    assertEquals("Same warning within cooldown must not duplicate", 1, thermalMonitor.state.value.alertCount)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    assertTrue(Shadows.shadowOf(nm).getNotification(NotificationHelper.ID_THERMAL_ALERT) != null)
  }

  @Test
  fun `HourlyTempVoltageChart displays empty state below two snapshots and Canvas with Room snapshots`() {
    composeTestRule.setContent {
      NetraBatteryTheme {
        HourlyTempVoltageChart(
          snapshots = listOf(
            BatterySnapshot(
              timestampMillis = 1_700_000_000_000L,
              levelPercent = 80,
              voltageMv = 4150,
              temperatureCelsius = 32.5f,
              isCharging = false,
              isHourlyCheckpoint = true,
            ),
          ),
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("hourly_chart_empty_state").assertIsDisplayed()
  }

  @Test
  fun `HourlyTempVoltageChart renders custom Canvas and peak summary for hourly Room data`() {
    val baseTime = 1_700_000_000_000L
    val snapshots = listOf(
      BatterySnapshot(
        timestampMillis = baseTime,
        levelPercent = 70,
        voltageMv = 3950,
        temperatureCelsius = 31.2f,
        isCharging = true,
        isHourlyCheckpoint = true,
      ),
      BatterySnapshot(
        timestampMillis = baseTime + 3_600_000L,
        levelPercent = 82,
        voltageMv = 4210,
        temperatureCelsius = 37.8f,
        isCharging = true,
        isHourlyCheckpoint = true,
      ),
      BatterySnapshot(
        timestampMillis = baseTime + 7_200_000L,
        levelPercent = 91,
        voltageMv = 4320,
        temperatureCelsius = 41.4f,
        isCharging = false,
        isHourlyCheckpoint = true,
      ),
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        HourlyTempVoltageChart(snapshots = snapshots)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("hourly_temp_voltage_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("hourly_chart_summary_metrics")
      .assertIsDisplayed()
      .assertTextContains("41.4 °C", substring = true)
      .assertTextContains("3.95–4.32 V", substring = true)
  }
}
