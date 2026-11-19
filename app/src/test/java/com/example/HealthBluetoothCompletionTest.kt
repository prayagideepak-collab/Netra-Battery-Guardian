package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.local.system.BluetoothReading
import com.example.data.local.system.ConnectedBluetoothMonitor
import com.example.domain.engine.MeasuredPowerBudget
import com.example.domain.model.*
import com.example.ui.NetraUiState
import com.example.ui.components.*
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.BatteryScreen
import com.example.ui.theme.NetraBatteryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi", sdk = [36], application = Application::class)
class HealthBluetoothCompletionTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private fun capture(name:String) = rule.runOnIdle {
    val v=rule.activity.window.decorView
    val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
    v.draw(android.graphics.Canvas(b))
    java.io.File("build/reports/guardian-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    b.recycle()
  }
  @Test fun integratedUnknownHealthStaysUnavailable() {
    rule.setContent { NetraBatteryTheme { Surface { BatteryScreen(NetraUiState(), AppStrings(AppLanguage.ENGLISH)) } } }
    rule.onNodeWithTag("battery_screen_list").performScrollToNode(hasTestTag("observed_health_percentage"))
    rule.onNodeWithTag("observed_health_category").assertTextContains("Unavailable")
    rule.onNodeWithTag("observed_health_percentage").assertTextEquals("Battery health %: Unavailable")
    capture("health-observations-unknown")
  }
  @Test fun hindiHealthKeepsIndependentInputsWithoutInventedBaseline() {
    val t=BatteryTelemetry(levelPercent=50,healthState=BatteryHealthState.GOOD,temperatureCelsius=32.5f,
      voltageMv=4100,chargeCycleCount=112,chargeCounterUah=2_000_000,estimatedCapacityMah=4000)
    rule.setContent { NetraBatteryTheme { Surface { Column(Modifier.padding(16.dp)) {
      Text("स्वचालित स्क्रीन परीक्षण, फ़ोन का माप नहीं")
      GuardianCard { BatteryHealthObservationContent(t,true) }
    } } } }
    rule.onNodeWithTag("observed_health_percentage").assertTextContains("Unavailable")
    rule.onNodeWithText("OS चक्र गणना: 112").assertExists()
    rule.onAllNodesWithText("100%",substring=true).assertCountEquals(0)
    capture("health-observations-hindi")
  }
  @Test fun bluetoothMissingInvalidAndDeferredAreExplicitInHindi() {
    rule.setContent { NetraBatteryTheme { Surface { Column(Modifier.padding(16.dp)) {
      Text("स्वचालित स्क्रीन परीक्षण, एक्सेसरी डेटा नहीं")
      GuardianCard { BluetoothDeviceTiles(BluetoothReading("fixture","जुड़ी एक्सेसरी",true,201,0),true) }
      ConnectedBluetoothCard(isHindi=true,measurementsAllowed=true)
    } } } }
    rule.onNodeWithText("बैटरी: Unavailable").assertExists()
    rule.onNodeWithText("सिग्नल: Unavailable").assertExists()
    rule.onNodeWithText("वैकल्पिक BLE माप स्थगित:",substring=true).assertExists()
    rule.onAllNodesWithText("0 dBm",substring=true).assertCountEquals(0)
    capture("bluetooth-hindi-deferred")
  }
  @Test fun monitorEnforcesBudgetEvenWhenUiPassesTrueAndClosesSafely() {
    assertFalse(MeasuredPowerBudget.permits(PowerFeature.CONNECTED_ACCESSORY_READ))
    val m=ConnectedBluetoothMonitor(rule.activity)
    m.read("not-an-accessory")
    assertTrue(m.lastReadDeferred)
    assertTrue(m.devices.value.isEmpty())
    m.pause();m.close();m.refresh()
    assertTrue(m.devices.value.isEmpty())
  }
}
