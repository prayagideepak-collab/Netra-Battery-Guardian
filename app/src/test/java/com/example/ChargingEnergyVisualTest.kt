package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.model.*
import com.example.ui.NetraUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
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
class ChargingEnergyVisualTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private fun capture(name:String) = rule.runOnIdle {
    val view=rule.activity.window.decorView
    val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bitmap))
    java.io.File("build/reports/guardian-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
    }
    bitmap.recycle()
  }
  @Test fun stableEnergyRateHasAnExplicitEstimateLabel() {
    val c=ChargingSpeedClassifier()
    var t=BatteryTelemetry()
    for(i in 0L..20L) { t=BatteryTelemetry(timestampMillis=i*1000,collectionStartedElapsedMillis=i*1000,
      levelPercent=45,isCharging=true,isPlugged=true,batteryStatus=2,energyCounterNwh=1_000_000_000L+i*2_500_000L,
      voltageMv=4000,currentMa=2200f);c.recordSample(t) }
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface { HomeScreen(
      state=NetraUiState(telemetry=t,chargingDiagnosis=c.diagnose(t)), strings=AppStrings(AppLanguage.ENGLISH),
      onRefresh={},onToggleBatterySaver={},onToggleCooling={},onToggleLocationSaver={},onToggleNightSilent={},
      onOpenStorageTab={},onOpenBatteryTab={},onOpenHeatTab={},onOpenAppsTab={},
    ) } } }
    rule.onNodeWithText("Stored rate (est.)").assertExists()
    capture("energy-counter-stable-home")
  }
  @Test fun independentSavedSpeedToggleShowsHonestBudgetDeferral() {
    rule.setContent { NetraBatteryTheme { androidx.compose.material3.Surface { SettingsScreen(
      state=NetraUiState(prefs=com.example.data.local.prefs.UserPreferences(announceChargingSpeed=false)),
      strings=AppStrings(AppLanguage.ENGLISH),onSetAppLanguage={},onSetVoiceLanguage={},onSetThemeMode={},
      onSetVoiceEnabled={},onSetVoiceVolume={},onSetAnnounceCharging={},onSetAnnounceDischarging={},
      onSetAnnounceScreenOff={},onSetAnnouncementStep={},onSetNightSilent={ _, _, _ -> },onSetCoolingSensitivity={},
      onSetStorageNotifications={},onSetBatterySaverThreshold={},onSetLowBatteryAlerts={},onSetDataRetentionDays={},
      onSetLiveGuardianService={},onTestVoice={},onExportCsv={},onExportPdf={},onClearAllData={},
    ) } } }
    rule.onNodeWithText("Charging type announcements").performScrollTo().assertIsDisplayed()
    capture("energy-counter-speed-setting")
  }
}
