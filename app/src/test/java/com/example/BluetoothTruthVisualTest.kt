package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.local.system.BluetoothReading
import com.example.ui.components.BluetoothDeviceTiles
import com.example.ui.components.GuardianCard
import com.example.ui.theme.NetraBatteryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi", sdk = [36], application = Application::class)
class BluetoothTruthVisualTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @Test fun missingReadingsNeverBecomeHealthyOrStable() {
    rule.setContent { NetraBatteryTheme { Surface { Column(Modifier.padding(16.dp)) {
      Text("Bluetooth layout test fixture", style = MaterialTheme.typography.titleLarge)
      GuardianCard { BluetoothDeviceTiles(BluetoothReading("fixture", "Connected accessory")) }
      Text("No device readings. Not physical-device evidence.")
    } } } }
    rule.onNodeWithText("Signal: Unavailable").assertExists()
    rule.onNodeWithText("Battery: ").assertExists()
    rule.onAllNodesWithText("Stronger signal", substring = true).assertCountEquals(0)
    capture("bluetooth-missing-readings")
  }
  @Test fun measuredSignalUsesEstimateNotDistanceOrHealth() {
    rule.setContent { NetraBatteryTheme { Surface { Column(Modifier.padding(16.dp)) {
      Text("Bluetooth layout test fixture", style = MaterialTheme.typography.titleLarge)
      for (rssi in listOf(-55, -75, -95)) GuardianCard {
        BluetoothDeviceTiles(BluetoothReading("fixture$rssi", "Sample BLE accessory", true, 64, rssi, 1L))
      }
      Text("Illustrative test values, not device measurements.")
    } } } }
    rule.onNodeWithText("Weaker signal (estimate)").assertExists()
    rule.onAllNodesWithText("almost disconnect", substring = true).assertCountEquals(0)
    capture("bluetooth-signal-estimates")
  }
  @Test fun integratedPermissionAndSectionPreview() {
    org.robolectric.Shadows.shadowOf(rule.activity.application).denyPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
    rule.setContent { NetraBatteryTheme { Surface {
      Column(Modifier.padding(16.dp)) {
        Text("Bluetooth section | automated preview", style = MaterialTheme.typography.titleLarge)
        Text("Permission denied fixture. No accessory data.")
        com.example.ui.components.ConnectedBluetoothCard()
      }
    } } }
    rule.onNodeWithText("Bluetooth devices").assertExists()
    rule.onNodeWithText("Allow Nearby Devices").assertExists()
    rule.onNodeWithText("Allow Nearby Devices to view connected accessories. No location access is needed.").assertExists()
    capture("bluetooth-integrated-permission-section")
    rule.onNodeWithText("Allow Nearby Devices").performClick()
    rule.waitForIdle()
    rule.onAllNodesWithText("Stronger signal", substring = true).assertCountEquals(0)
  }
  private fun capture(name: String) {
    rule.waitForIdle()
    rule.runOnIdle {
      val view = rule.activity.window.decorView
      val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(image))
      java.io.File("build/reports/guardian-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      image.recycle()
    }
  }
}
