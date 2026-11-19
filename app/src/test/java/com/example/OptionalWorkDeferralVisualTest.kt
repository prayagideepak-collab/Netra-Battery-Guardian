package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MeasuredPowerBudget
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
class OptionalWorkDeferralVisualTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  @Test fun approvedStatusFitsExistingSnackbarAtPhoneWidth() {
    rule.setContent { NetraBatteryTheme { Surface(Modifier.fillMaxSize()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Optional work deferral", style = MaterialTheme.typography.titleLarge)
        Text("Automated snackbar fixture. Not a device measurement or a full activity test.")
        Snackbar { Text(MeasuredPowerBudget.DEFERRED_MESSAGE) }
      }
    } } }
    rule.onNodeWithText(MeasuredPowerBudget.DEFERRED_MESSAGE).assertIsDisplayed()
    rule.runOnIdle {
      val view = rule.activity.window.decorView
      val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(image))
      java.io.File("build/reports/guardian-ui/optional-work-deferred.png").apply { parentFile?.mkdirs() }.outputStream().use {
        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      image.recycle()
    }
  }
}
