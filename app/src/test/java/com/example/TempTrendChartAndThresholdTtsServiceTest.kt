package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.prefs.UserPreferences
import com.example.service.BatteryThresholdTtsService
import com.example.service.NotificationHelper
import com.example.ui.components.BatteryTemperatureTrendChart
import com.example.ui.theme.NetraBatteryTheme
import com.example.voice.ChargingVoiceAssistant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TempTrendChartAndThresholdTtsServiceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var voiceAssistant: ChargingVoiceAssistant

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    voiceAssistant = ChargingVoiceAssistant(context, NotificationHelper(context))
  }

  @After
  fun tearDown() {
    voiceAssistant.shutdown()
    db.close()
  }

  @Test
  fun `BatteryTemperatureTrendChart plots Room snapshots and switches between 24h and 7d`(): Unit = runBlocking {
    val now = 1_700_000_000_000L
    // Insert 3 recent (24h) + 2 older (3d) snapshots in Room
    val samples = listOf(
      BatterySnapshot(timestampMillis = now - 3 * 86_400_000L, levelPercent = 90, voltageMv = 4200, temperatureCelsius = 29.0f, isCharging = false),
      BatterySnapshot(timestampMillis = now - 2 * 86_400_000L, levelPercent = 50, voltageMv = 3850, temperatureCelsius = 33.0f, isCharging = false),
      BatterySnapshot(timestampMillis = now - 4 * 3_600_000L, levelPercent = 40, voltageMv = 3800, temperatureCelsius = 31.5f, isCharging = false),
      BatterySnapshot(timestampMillis = now - 2 * 3_600_000L, levelPercent = 65, voltageMv = 4050, temperatureCelsius = 37.0f, isCharging = true),
      BatterySnapshot(timestampMillis = now - 1 * 3_600_000L, levelPercent = 82, voltageMv = 4250, temperatureCelsius = 41.2f, isCharging = true),
    )
    samples.forEach { db.netraDao().insertSnapshot(it) }

    val s24 = db.netraDao().observeSnapshotsSince(now - 24L * 3600_000L).first()
    val s7d = db.netraDao().observeSnapshotsSince(now - 7L * 24L * 3600_000L).first()
    assertEquals(3, s24.size)
    assertEquals(5, s7d.size)

    composeTestRule.setContent {
      NetraBatteryTheme {
        BatteryTemperatureTrendChart(
          snapshots24h = s24,
          snapshots7d = s7d,
          isHindi = false,
          isLowPowerSurvival = false,
        )
      }
    }

    composeTestRule.onNodeWithTag("battery_temp_trend_chart").assertIsDisplayed()
    composeTestRule.onNodeWithTag("battery_temp_trend_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("temp_trend_peak_pill").assertTextContains("41.2", substring = true)
    composeTestRule.onNodeWithTag("temp_trend_delta_pill").assertTextContains("+9.7", substring = true)
    composeTestRule.onNodeWithTag("temp_trend_range_footer").assertTextContains("3 Room pts", substring = true)

    // Switch to 7d window -> updates point count & baseline range from Room data
    composeTestRule.onNodeWithTag("temp_trend_7d_chip").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("temp_trend_range_footer").assertTextContains("5 Room pts", substring = true)
    composeTestRule.onNodeWithTag("temp_trend_delta_pill").assertTextContains("+12.2", substring = true)
  }

  @Test
  fun `BatteryThresholdTtsService triggers TTS on 80 percent charge and low battery levels`() {
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(80, isCharging = true))
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(100, isCharging = true))
    assertFalse(BatteryThresholdTtsService.isThresholdEligible(53, isCharging = true))
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(20, isCharging = false))
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(5, isCharging = false))
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(45, isCharging = false))

    val prefs = UserPreferences(
      voiceEnabled = true,
      announceCharging = true,
      announceDischarging = true,
      nightSilentEnabled = false,
    )

    // 1. Reached 80% while charging (screen on) -> speaks charge threshold notification
    BatteryThresholdTtsService.triggerSafely(
      context = context,
      voiceAssistant = voiceAssistant,
      prefs = prefs,
      levelPercent = 80,
      isCharging = true,
      screenOn = true,
      allowServiceStart = false,
    )
    assertEquals(1, voiceAssistant.assistantState.value.announcementCount)
    assertEquals(80, voiceAssistant.assistantState.value.lastMilestoneLevel)
    assertEquals("Reached 80 percent charge.", voiceAssistant.assistantState.value.lastSpokenMessage)

    // 2. At screen-off20%, preserve established quiet policy; no extra speech.
    BatteryThresholdTtsService.triggerSafely(
      context = context,
      voiceAssistant = voiceAssistant,
      prefs = prefs,
      levelPercent = 20,
      isCharging = false,
      screenOn = false,
      allowServiceStart = false,
    )
    assertEquals(1, voiceAssistant.assistantState.value.announcementCount)
    assertEquals(80, voiceAssistant.assistantState.value.lastMilestoneLevel)

    // 3. Dropped to 5% critically low battery -> speaks critical low-battery alert
    BatteryThresholdTtsService.triggerSafely(
      context = context,
      voiceAssistant = voiceAssistant,
      prefs = prefs,
      levelPercent = 5,
      isCharging = false,
      screenOn = false,
      allowServiceStart = false,
    )
    assertEquals(2, voiceAssistant.assistantState.value.announcementCount)
    assertEquals(5, voiceAssistant.assistantState.value.lastMilestoneLevel)
    assertTrue(voiceAssistant.assistantState.value.lastSpokenMessage?.contains("critically low") == true)

    val buildIntent = BatteryThresholdTtsService.buildTriggerIntent(
      context = context,
      levelPercent = 80,
      isCharging = true,
      screenOn = true,
    )
    assertEquals(BatteryThresholdTtsService.ACTION_TRIGGER_THRESHOLD, buildIntent.action)
    assertEquals(80, buildIntent.getIntExtra(BatteryThresholdTtsService.EXTRA_LEVEL_PERCENT, -1))
  }
}
