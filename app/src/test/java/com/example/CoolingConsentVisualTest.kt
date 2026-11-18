package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.model.*
import com.example.ui.NetraUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.HeatScreen
import com.example.ui.theme.NetraBatteryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1400dp-xhdpi", sdk = [36], application = Application::class)
class CoolingConsentVisualTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  @Test fun coolingControlStartsOffAndShowsRestoreTerms() {
    val t = BatteryTelemetry()
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface { HeatScreen(
      state = NetraUiState(telemetry = t, heatReport = HeatDiagnosisReport(
        zone = TemperatureZone.UNAVAILABLE, trend = TemperatureTrendResult(Float.NaN, TrendStatus.UNAVAILABLE, "Unavailable", "Unavailable"),
        confidence = ConfidenceLevel.LOW, causes = emptyList(), recommendedActionsEn = emptyList(), recommendedActionsHi = emptyList(),
        coolingWorkflowActive = false, coolingStepsAppliedEn = emptyList(), coolingStepsAppliedHi = emptyList(),
      )), strings = AppStrings(AppLanguage.ENGLISH),
      onToggleCoolingMode = {}, onOpenWriteSettings = {},
    ) } } }
    rule.waitForIdle()
    rule.onNodeWithTag("cooling_brightness_consent").performScrollTo().assertIsOff()
    rule.onNodeWithText("OFF by default", substring = true).assertExists()
    rule.onNodeWithText("Heat safety cannot be confirmed", substring = true).assertExists()
    rule.onAllNodesWithText("within safe bounds", substring = true).assertCountEquals(0)
    rule.runOnIdle {
      val view = rule.activity.window.decorView
      val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(bitmap))
      java.io.File("build/reports/guardian-ui/cooling-consent-off.png").apply { parentFile?.mkdirs() }.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }
}
