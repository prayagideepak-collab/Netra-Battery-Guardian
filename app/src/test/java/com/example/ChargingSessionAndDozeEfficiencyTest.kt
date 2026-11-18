package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.ChargingSessionDao
import com.example.domain.engine.DozeEfficiencyCalculator
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.service.DozeMonitorState
import com.example.ui.components.DozeEfficiencyCard
import com.example.ui.theme.NetraBatteryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChargingSessionAndDozeEfficiencyTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Application
  private lateinit var db: AppDatabase
  private lateinit var chargingSessionDao: ChargingSessionDao
  private lateinit var dozeCalculator: DozeEfficiencyCalculator

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    chargingSessionDao = db.chargingSessionDao()
    dozeCalculator = DozeEfficiencyCalculator()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `ChargingSessionDao tracks start end and percentageGained strictly on state changes`() = runBlocking {
    val base = 1_700_000_000_000L

    // 1. Disconnected with no open session -> no write
    assertFalse(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = false,
        timestampMillis = base,
        levelPercent = 40,
        temperatureCelsius = 31.0f,
        powerWatts = 0f,
        speedClass = "UNKNOWN",
        plugType = "NONE",
      )
    )
    assertEquals(0, chargingSessionDao.getAllChargingSessions().size)

    // 2. Plugged in at 40% -> inserts session
    assertTrue(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = true,
        timestampMillis = base + 10_000L,
        levelPercent = 40,
        temperatureCelsius = 32.0f,
        powerWatts = 18.0f,
        speedClass = "FAST",
        plugType = "AC",
      )
    )
    val initialSessions = chargingSessionDao.getAllChargingSessions()
    assertEquals(1, initialSessions.size)
    assertEquals(40, initialSessions[0].startLevel)
    assertNull(initialSessions[0].endTimeMillis)
    assertEquals(0, initialSessions[0].percentageGained)

    // 3. Redundant tick with identical state -> skipped (no DB write)
    assertFalse(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = true,
        timestampMillis = base + 25_000L,
        levelPercent = 40,
        temperatureCelsius = 32.2f,
        powerWatts = 18.1f,
        speedClass = "FAST",
        plugType = "AC",
      )
    )

    // 4. Level change 40% -> 46% triggers state-change update
    assertTrue(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = true,
        timestampMillis = base + 300_000L,
        levelPercent = 46,
        temperatureCelsius = 33.0f,
        powerWatts = 18.0f,
        speedClass = "FAST",
        plugType = "AC",
      )
    )
    val updated = chargingSessionDao.getOpenSession()
    assertNotNull(updated)
    assertEquals(46, updated!!.endLevel)
    assertEquals(6, updated.percentageGained)

    // 5. Unplugging at 62% closes session cleanly with endTimeMillis & percentageGained = 22
    assertTrue(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = false,
        timestampMillis = base + 900_000L,
        levelPercent = 62,
        temperatureCelsius = 34.0f,
        powerWatts = 0f,
        speedClass = "UNKNOWN",
        plugType = "NONE",
      )
    )
    val allAfterUnplug = chargingSessionDao.getAllChargingSessions()
    assertEquals(1, allAfterUnplug.size)
    val closed = allAfterUnplug[0]
    assertEquals(base + 10_000L, closed.startTimeMillis)
    assertEquals(base + 900_000L, closed.endTimeMillis)
    assertEquals(40, closed.startLevel)
    assertEquals(62, closed.endLevel)
    assertEquals(22, closed.percentageGained)
    assertEquals(22, chargingSessionDao.observeTotalPercentageGained().first())

    // 6. Extra disconnected tick -> no DB write
    assertFalse(
      chargingSessionDao.recordChargingStateChange(
        isPlugged = false,
        timestampMillis = base + 950_000L,
        levelPercent = 61,
        temperatureCelsius = 33.5f,
        powerWatts = 0f,
        speedClass = "UNKNOWN",
        plugType = "NONE",
      )
    )
  }

  @Test
  fun `DozeEfficiencyCalculator totals Doze duration and energy savings from logged transitions`() {
    val t0 = 1_700_000_000_000L
    val logs = listOf(
      ActivityLogEntity(
        timestampMillis = t0,
        category = LogCategory.POWER_BUDGET.name,
        severity = LogSeverity.INFO.name,
        titleEn = DozeEfficiencyCalculator.TITLE_ENTERED_DOZE,
        titleHi = "",
        detailEn = "",
        detailHi = "",
        whyEn = "",
        whyHi = "",
      ),
      ActivityLogEntity(
        timestampMillis = t0 + 2 * 3_600_000L, // +2 hours
        category = LogCategory.POWER_BUDGET.name,
        severity = LogSeverity.INFO.name,
        titleEn = DozeEfficiencyCalculator.TITLE_EXITED_DOZE,
        titleHi = "",
        detailEn = "",
        detailHi = "",
        whyEn = "",
        whyHi = "",
      ),
    )

    val metrics = dozeCalculator.calculate(
      activityLogs = logs,
      dozeState = DozeMonitorState(isInDozeMode = false, enterDozeCount = 1, exitDozeCount = 1),
      estimatedCapacityMah = 5000,
      nowMillis = t0 + 3 * 3_600_000L,
    )

    assertEquals(2 * 3_600_000L, metrics.totalDozeTimeMillis)
    assertEquals(1, metrics.completedSessionsCount)
    assertEquals(2, metrics.transitionsLoggedCount)
    assertTrue(metrics.estimatedBatterySavedPercent.isNaN())
    assertEquals(-1, metrics.estimatedMahSaved)
    assertEquals(-1, metrics.efficiencyScorePercent)
    assertEquals("2h 0m", DozeEfficiencyCalculator.formatDuration(metrics.totalDozeTimeMillis))
  }

  @Test
  fun `DozeEfficiencyCard Material 3 Card displays total Doze time and energy-saving metrics`() {
    val t0 = 1_700_000_000_000L
    val logs = listOf(
      ActivityLogEntity(
        timestampMillis = t0,
        category = LogCategory.POWER_BUDGET.name,
        severity = LogSeverity.INFO.name,
        titleEn = DozeEfficiencyCalculator.TITLE_ENTERED_DOZE,
        titleHi = "",
        detailEn = "",
        detailHi = "",
        whyEn = "",
        whyHi = "",
      ),
      ActivityLogEntity(
        timestampMillis = t0 + 2 * 3_600_000L,
        category = LogCategory.POWER_BUDGET.name,
        severity = LogSeverity.INFO.name,
        titleEn = DozeEfficiencyCalculator.TITLE_EXITED_DOZE,
        titleHi = "",
        detailEn = "",
        detailHi = "",
        whyEn = "",
        whyHi = "",
      ),
    )
    val metrics = dozeCalculator.calculate(
      activityLogs = logs,
      dozeState = DozeMonitorState(isInDozeMode = false, enterDozeCount = 1, exitDozeCount = 1),
      estimatedCapacityMah = 4500,
      nowMillis = t0 + 2 * 3_600_000L,
    )

    composeTestRule.setContent {
      NetraBatteryTheme {
        DozeEfficiencyCard(
          metrics = metrics,
          isHindi = false,
          whyLabel = "Why?",
        )
      }
    }

    composeTestRule.onNodeWithTag("doze_efficiency_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("doze_total_time_value").assertTextContains("2h 0m")
    composeTestRule.onNodeWithTag("doze_energy_saved_value").assertDoesNotExist()
    composeTestRule.onNodeWithTag("doze_transitions_value").assertTextContains("1 (2 ev)")
  }
}
