package com.example

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.AppDatabase
import com.example.data.local.system.AppStorageBreakdown
import com.example.data.local.system.DiskUsageReport
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.domain.model.StorageActionTarget
import com.example.domain.model.StorageInsight
import com.example.ui.components.CriticalThermalEventsListView
import com.example.ui.components.StorageAnalysisDashboard
import com.example.ui.theme.NetraBatteryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StorageDashboardAndThermalLogTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Application
  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `StorageAnalysisDashboard visualizes occupied vs free space and top storage categories`() {
    val gb = 1024L * 1024L * 1024L
    val mb = 1024L * 1024L
    val insight = StorageInsight(
      totalBytes = 128L * gb,
      usedBytes = 96L * gb,
      freeBytes = 32L * gb,
      freePercent = 25.0f,
      appOwnCacheBytes = 120L * mb,
      downloadsEstimatedBytes = 4L * gb,
    )
    val diskReport = DiskUsageReport(
      scannedAtMillis = 1_700_000_000_000L,
      executedDuringIdleWindow = true,
      selfAppBytes = 45L * mb,
      selfDataBytes = 20L * mb,
      selfCacheBytes = 120L * mb,
      heavyApps = listOf(
        AppStorageBreakdown(
          packageName = "com.example.videoeditor",
          appName = "Studio Video",
          appBytes = 2L * gb,
          dataBytes = 5L * gb,
          cacheBytes = 800L * mb,
        )
      ),
    )

    var lastTarget: StorageActionTarget? = null
    composeTestRule.setContent {
      NetraBatteryTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          StorageAnalysisDashboard(
            storageInsight = insight,
            diskUsageReport = diskReport,
            isHindi = false,
            onCategoryAction = { target, _ -> lastTarget = target },
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("storage_analysis_dashboard_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("storage_analysis_chart_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("storage_occupied_pill")
      .assertTextContains("96.00 GB", substring = true)
      .assertTextContains("75.0%", substring = true)
    composeTestRule.onNodeWithTag("storage_free_pill")
      .assertTextContains("32.00 GB", substring = true)
      .assertTextContains("25.0%", substring = true)

    // Verify top storage-consuming app categories are listed
    composeTestRule.onNodeWithTag("storage_category_row_apps_binaries").assertDoesNotExist()
    composeTestRule.onNodeWithTag("storage_category_row_app_data").assertDoesNotExist()
    composeTestRule.onNodeWithTag("storage_category_row_app_caches")
      .performScrollTo()
      .assertIsDisplayed()

    // Trigger category action button
    composeTestRule.onNodeWithTag("storage_category_action_app_caches")
      .performScrollTo()
      .performClick()
    assertEquals(StorageActionTarget.APP_OWN_CACHE, lastTarget)

    // Switch to Bar mode and verify canvas remains displayed
    composeTestRule.onNodeWithTag("storage_chart_bar_chip")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("storage_analysis_chart_canvas")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `CriticalThermalEventsListView retrieves and filters historical overheating events from Room`(): Unit = runBlocking {
    val now = 1_700_000_000_000L
    // Insert 1 non-heat log, 1 heat warning, and 2 critical thermal events into Room
    db.netraDao().insertActivityLog(
      ActivityLogEntity(
        timestampMillis = now - 3_600_000L,
        category = LogCategory.BATTERY.name,
        severity = LogSeverity.INFO.name,
        titleEn = "Charger Connected",
        titleHi = "",
        detailEn = "Fast charging started",
        detailHi = "",
        whyEn = "Normal plug event",
        whyHi = "",
      )
    )
    val warnId = db.netraDao().insertActivityLog(
      ActivityLogEntity(
        timestampMillis = now - 1_800_000L,
        category = LogCategory.HEAT.name,
        severity = LogSeverity.WARNING.name,
        titleEn = "Thermal Zone: HOT (41.2°C)",
        titleHi = "",
        detailEn = "Battery temperature measured at 41.2°C while gaming.",
        detailHi = "",
        whyEn = "Cooling workflow activated",
        whyHi = "",
      )
    )
    val crit1Id = db.netraDao().insertActivityLog(
      ActivityLogEntity(
        timestampMillis = now - 600_000L,
        category = LogCategory.HEAT.name,
        severity = LogSeverity.CRITICAL.name,
        titleEn = "Critical Thermal Event Detected (46.8°C)",
        titleHi = "",
        detailEn = "46.8°C (CRITICAL) at 78%, 4.18 V • Charger plugged",
        detailHi = "",
        whyEn = "Background WorkManager thermal guard detected dangerous battery heat",
        whyHi = "",
      )
    )
    val crit2Id = db.netraDao().insertActivityLog(
      ActivityLogEntity(
        timestampMillis = now - 120_000L,
        category = LogCategory.HEAT.name,
        severity = LogSeverity.CRITICAL.name,
        titleEn = "Emergency Heat Alert (49.1°C)",
        titleHi = "",
        detailEn = "49.1°C (EMERGENCY) • OS BATTERY_HEALTH_OVERHEAT",
        detailHi = "",
        whyEn = "Immediate cooldown required",
        whyHi = "",
      )
    )

    val roomThermalEvents = db.netraDao().observeCriticalThermalEvents().first()
    assertEquals("Room query must return only HEAT WARNING/CRITICAL events", 3, roomThermalEvents.size)

    composeTestRule.setContent {
      NetraBatteryTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CriticalThermalEventsListView(
            criticalThermalEvents = roomThermalEvents,
            isHindi = false,
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("critical_thermal_events_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("critical_thermal_events_count_pill")
      .assertTextContains("3 Events", substring = true)
    composeTestRule.onNodeWithTag("critical_thermal_event_item_$crit2Id")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("critical_thermal_event_item_$crit1Id")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("critical_thermal_event_item_$warnId")
      .performScrollTo()
      .assertIsDisplayed()

    // Filter to Critical Only -> 2 events
    composeTestRule.onNodeWithTag("thermal_filter_critical_only_chip")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("critical_thermal_events_count_pill")
      .performScrollTo()
      .assertTextContains("2 Events", substring = true)
  }
}
