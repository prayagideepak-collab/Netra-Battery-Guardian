package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.BatterySnapshot
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.ChargingLimitingFactor
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.PlugType
import com.example.ui.components.BatteryMonitoringDashboardView
import com.example.ui.components.SentinelTemperatureStatus
import com.example.ui.components.classifySentinelTemperature
import com.example.ui.components.validateHardwareCurrent
import com.example.ui.theme.NetraBatteryTheme
import com.example.widget.BatteryThermalWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatterySentinelDashboardAndWidgetTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `BatteryMonitoringDashboardView displays real charging telemetry power and history charts`() {
    val now = 1_710_000_000_000L
    val telemetry = BatteryTelemetry(
      timestampMillis = now,
      levelPercent = 76,
      isCharging = true,
      isPlugged = true,
      plugType = PlugType.AC,
      temperatureCelsius = 37.5f,
      voltageMv = 4200,
      currentMa = 2500f,
      estimatedCapacityMah = 4500,
      healthState = BatteryHealthState.GOOD,
      technology = "Li-ion",
    )
    val diagnosis = ChargingDiagnosis(
      speedClass = ChargingSpeedClass.FAST,
      netPowerWatts = 10.5f,
      rollingAvgCurrentMa = 2500f,
      estimatedMinutesRemaining = 28,
      isTimeEstimateApproximate = true,
      limitingFactor = ChargingLimitingFactor.NONE,
      explanationEn = "Fast charging active",
      explanationHi = "तेज़ चार्जिंग सक्रिय",
      whyEn = "4.2V x 2.5A = 10.5W",
      whyHi = "4.2V x 2.5A = 10.5W",
    )
    val snapshots = listOf(
      BatterySnapshot(
        id = 1,
        timestampMillis = now - 600_000L,
        levelPercent = 70,
        voltageMv = 4120,
        temperatureCelsius = 35.5f,
        currentMa = 2400f,
        powerWatts = 9.88f,
        isCharging = true,
        chargingStatus = "CHARGING",
      ),
      BatterySnapshot(
        id = 2,
        timestampMillis = now - 300_000L,
        levelPercent = 73,
        voltageMv = 4160,
        temperatureCelsius = 36.5f,
        currentMa = 2450f,
        powerWatts = 10.19f,
        isCharging = true,
        chargingStatus = "CHARGING",
      ),
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          BatteryMonitoringDashboardView(
            telemetry = telemetry,
            chargingDiagnosis = diagnosis,
            historySnapshots = snapshots,
            isHindi = false,
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    // 1. Battery Monitoring card & circular indicator
    composeTestRule.onNodeWithTag("sentinel_circular_battery_indicator").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sentinel_battery_percentage_value")
      .assertTextContains("76%", substring = true)
    composeTestRule.onNodeWithTag("sentinel_battery_health_value")
      .assertTextContains("Good", substring = true)
    composeTestRule.onNodeWithTag("sentinel_battery_technology_value")
      .assertTextContains("Li-ion", substring = true)
    composeTestRule.onNodeWithTag("sentinel_battery_capacity_value")
      .assertTextContains("Counter-derived ~4500 mAh", substring = true)

    // 2. Temperature Monitoring card
    composeTestRule.onNodeWithTag("sentinel_temperature_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("sentinel_temperature_status_badge").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sentinel_temp_delta_value")
      .assertTextContains("+2.0°C", substring = true)

    // 3. Voltage Monitoring card
    composeTestRule.onNodeWithTag("sentinel_voltage_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("sentinel_voltage_fluctuation_value")
      .assertTextContains("Δ 80 mV", substring = true)

    // 4. Current Monitoring card
    composeTestRule.onNodeWithTag("sentinel_current_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("sentinel_charging_current_value")
      .assertTextContains("+2500 mA", substring = true)

    // 5. Power Monitoring card (4.20 V * 2500 mA = 10.50 W)
    composeTestRule.onNodeWithTag("sentinel_power_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("sentinel_power_speed_value")
      .assertTextContains("Fast", substring = true)
    composeTestRule.onNodeWithTag("sentinel_power_formula_value")
      .assertTextContains("10.50 W", substring = true)

    // 6. Historical charts
    composeTestRule.onNodeWithTag("sentinel_history_charts_card")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `validateHardwareCurrent normalizes inverted OEM current polarity and handles Unavailable`() {
    // OEM reporting negative current during charging -> normalized to Incoming (Charging)
    val invertedCharge = validateHardwareCurrent(
      rawCurrentMa = -1820f,
      isCharging = true,
      isPlugged = true,
    )
    assertTrue(invertedCharge.isAvailable)
    assertEquals(1820f, invertedCharge.magnitudeMa, 0.01f)
    assertEquals("Signed sensor reading", invertedCharge.directionLabelEn)
    assertEquals("-1820 mA", invertedCharge.chargingCurrentText)

    // OEM reporting positive current during discharging -> normalized to Outgoing (Discharging)
    val invertedDischarge = validateHardwareCurrent(
      rawCurrentMa = 460f,
      isCharging = false,
      isPlugged = false,
    )
    assertTrue(invertedDischarge.isAvailable)
    assertEquals(460f, invertedDischarge.magnitudeMa, 0.01f)
    assertEquals("Signed sensor reading", invertedDischarge.directionLabelEn)
    assertEquals("+460 mA", invertedDischarge.dischargingCurrentText)

    // Unavailable sensor -> strictly "Unavailable", never fake numbers
    val unavailable = validateHardwareCurrent(
      rawCurrentMa = Float.NaN,
      isCharging = false,
      isPlugged = false,
    )
    assertFalse(unavailable.isAvailable)
    assertEquals("Unavailable", unavailable.directionLabelEn)
    assertEquals("Unavailable", unavailable.liveCurrentText)
    assertEquals("Unavailable", unavailable.chargingCurrentText)
    assertEquals("Unavailable", unavailable.dischargingCurrentText)
  }

  @Test
  fun `BatteryMonitoringDashboardView displays Unavailable when hardware readings are missing`() {
    val missingTelemetry = BatteryTelemetry(
      levelPercent = 64,
      isCharging = false,
      isPlugged = false,
      temperatureCelsius = Float.NaN,
      voltageMv = -1,
      currentMa = Float.NaN,
      estimatedCapacityMah = -1,
      healthState = BatteryHealthState.UNKNOWN,
      technology = "",
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          BatteryMonitoringDashboardView(
            telemetry = missingTelemetry,
            chargingDiagnosis = null,
            historySnapshots = emptyList(),
            isHindi = false,
          )
        }
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sentinel_battery_health_value").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sentinel_battery_technology_value").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sentinel_battery_capacity_value").assertDoesNotExist()

    composeTestRule.onNodeWithTag("sentinel_current_card").performScrollTo()
    composeTestRule.onNodeWithTag("sentinel_charging_current_value").assertDoesNotExist()
    composeTestRule.onNodeWithTag("sentinel_discharging_current_value").assertDoesNotExist()

    composeTestRule.onNodeWithTag("sentinel_power_card").performScrollTo()
    composeTestRule.onNodeWithTag("sentinel_power_speed_value").assertDoesNotExist()
  }

  @Test
  fun `BatteryThermalWidgetProvider updates home screen widget with battery percentage and thermal status`() {
    val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
      putExtra(BatteryManager.EXTRA_LEVEL, 88)
      putExtra(BatteryManager.EXTRA_SCALE, 100)
      putExtra(BatteryManager.EXTRA_VOLTAGE, 4310)
      putExtra(BatteryManager.EXTRA_TEMPERATURE, 412) // 41.2°C -> Hot
      putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
    }
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(stickyBattery)

    val appWidgetManager = AppWidgetManager.getInstance(context)
    val shadowWidgetManager = shadowOf(appWidgetManager)
    val widgetId = shadowWidgetManager.createWidget(
      BatteryThermalWidgetProvider::class.java,
      R.layout.widget_battery_thermal,
    )

    val provider = BatteryThermalWidgetProvider()
    provider.onUpdate(context, appWidgetManager, intArrayOf(widgetId))

    val view: View = shadowWidgetManager.getViewFor(widgetId)
    val percentView = view.findViewById<TextView>(R.id.widget_battery_percent)
    val thermalBadge = view.findViewById<TextView>(R.id.widget_thermal_badge)
    val statusView = view.findViewById<TextView>(R.id.widget_charging_status)

    assertEquals("88%", percentView.text.toString())
    assertEquals("41.2°C", thermalBadge.text.toString())
    assertEquals("Charging (AC)", statusView.text.toString())

    // Also verify Critical and Normal classifications
    assertEquals(SentinelTemperatureStatus.UNAVAILABLE, classifySentinelTemperature(32.0f))
    assertEquals(SentinelTemperatureStatus.UNAVAILABLE, classifySentinelTemperature(37.2f))
    assertEquals(SentinelTemperatureStatus.UNAVAILABLE, classifySentinelTemperature(41.2f))
    assertEquals(SentinelTemperatureStatus.UNAVAILABLE, classifySentinelTemperature(45.0f))
    assertEquals(SentinelTemperatureStatus.UNAVAILABLE, classifySentinelTemperature(Float.NaN))
  }
}
