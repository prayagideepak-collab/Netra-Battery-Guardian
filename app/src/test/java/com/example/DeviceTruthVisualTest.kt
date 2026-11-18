package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.StorageInsight
import com.example.ui.components.BatteryMonitoringDashboardView
import com.example.ui.components.StorageAnalysisDashboard
import com.example.ui.theme.NetraBatteryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w390dp-h844dp-xhdpi",sdk=[36],application=Application::class)
class DeviceTruthVisualTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private fun capture(name:String) {
    rule.waitForIdle()
    rule.runOnIdle {
      val view = rule.activity.window.decorView
      val image = android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(image))
      java.io.File("build/reports/guardian-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
      image.recycle()
    }
  }
  @Test fun missingSensorsHaveNoInventedCapacityOrThermalBadge() {
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        BatteryMonitoringDashboardView(BatteryTelemetry(),null,emptyList(),false)
      }
    } } }
    rule.onNodeWithTag("sentinel_battery_capacity_value").assertDoesNotExist()
    rule.onNodeWithTag("sentinel_temperature_status_badge").assertDoesNotExist()
    rule.onAllNodesWithText("4500",substring=true).assertCountEquals(0)
    capture("truth-missing-dashboard")
  }
  @Test fun signedSensorRemainsSignedWhileCharging() {
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        BatteryMonitoringDashboardView(BatteryTelemetry(levelPercent=62,isCharging=true,isPlugged=true,temperatureCelsius=37.5f,voltageMv=4200,currentMa=-700f),null,emptyList(),false)
      }
    } } }
    rule.onNodeWithTag("sentinel_current_card").performScrollTo()
    rule.onNodeWithTag("sentinel_charging_current_value").assertTextContains("-700 mA")
    capture("truth-signed-current")
    rule.onNodeWithTag("sentinel_power_card").performScrollTo()
    rule.onNodeWithTag("sentinel_power_formula_value").assertTextContains("-2.94 W",substring=true)
    capture("truth-signed-power")
  }
  @Test fun storageLabelsOnlyMeasuredCacheAndUnattributedUsage() {
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        StorageAnalysisDashboard(StorageInsight(100_000_000L,60_000_000L,40_000_000L,40f,2_000_000L,0L),null)
      }
    } } }
    rule.onAllNodesWithText("Installed Apps & Binaries").assertCountEquals(0)
    capture("truth-storage")
  }
}
