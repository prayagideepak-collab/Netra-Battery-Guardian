package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.BatterySnapshot
import com.example.domain.model.*
import com.example.service.NotificationHelper
import com.example.ui.components.HourlyTempVoltageChart
import com.example.ui.theme.NetraBatteryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi", sdk = [36], application = Application::class)
class M2RenderingAndThermalOwnershipTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private fun sample(time: Long, temp: Float) = BatterySnapshot(
    timestampMillis = time, levelPercent = 50, temperatureCelsius = temp,
    voltageMv = 4000, currentMa = 0f, powerWatts = 0f, isCharging = false,
    chargingStatus = "UNKNOWN", isPlugged = false, plugType = "NONE",
    screenOn = true, thermalLevel = "NONE", activeProfile = "NORMAL",
  )
  @Test fun firstLoadedHistoryStaysVisibleWithoutOptionalRecalculation() {
    val data = mutableStateOf(emptyList<BatterySnapshot>())
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface {
      HourlyTempVoltageChart(data.value)
    } } }
    rule.onNodeWithTag("hourly_chart_empty_state").assertExists()
    rule.runOnIdle { data.value = listOf(sample(1000, 30f), sample(2000, 31f)) }
    rule.onNodeWithTag("hourly_temp_voltage_canvas").assertContentDescriptionContains("2 observations", substring = true)
    rule.runOnIdle { data.value = data.value + sample(3000, 40f) }
    rule.onNodeWithTag("hourly_temp_voltage_canvas").assertContentDescriptionContains("2 observations", substring = true)
    rule.onNodeWithText("Saved history: static").assertExists()
    rule.runOnIdle {
      val view = rule.activity.window.decorView
      val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(bitmap))
      java.io.File("build/reports/guardian-ui/cached-history-m2.png").apply { parentFile?.mkdirs() }.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }
  @Test fun sharedHelperSuppressesCrossPathRepeatsButEscalatesAndRearms() {
    val ctx = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val helper = NotificationHelper(ctx)
    val nm = Shadows.shadowOf(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    fun t(level: ThermalLevel, at: Long) = BatteryTelemetry(thermalLevel = level, thermalStatusAvailable = true, collectionStartedElapsedMillis = at)
    assertTrue(helper.postThermalObservation(t(ThermalLevel.MODERATE, 0), "service", "observed warning"))
    assertFalse(helper.postThermalObservation(t(ThermalLevel.MODERATE, 1), "rules", "duplicate"))
    assertEquals("service", nm.getNotification(NotificationHelper.ID_THERMAL_ALERT).extras.getString(Notification.EXTRA_TITLE))
    assertTrue(helper.postThermalObservation(t(ThermalLevel.SEVERE, 2), "worker escalation", "observed severe"))
    assertEquals("worker escalation", nm.getNotification(NotificationHelper.ID_THERMAL_ALERT).extras.getString(Notification.EXTRA_TITLE))
    helper.postThermalObservation(t(ThermalLevel.NONE, 1_000))
    helper.postThermalObservation(t(ThermalLevel.NONE, 31_000))
    assertTrue(helper.postThermalObservation(t(ThermalLevel.MODERATE, 31_001), "new episode", "observed warning"))
  }
}
