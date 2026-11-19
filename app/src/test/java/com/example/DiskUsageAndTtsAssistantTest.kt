package com.example

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.prefs.UserPreferences
import com.example.data.local.system.DiskUsageHelper
import com.example.service.NetraWorkScheduler
import com.example.service.NotificationHelper
import com.example.voice.ChargingVoiceAssistant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiskUsageAndTtsAssistantTest {

  private lateinit var context: Application
  private var tempHeavyFile: File? = null

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    tempHeavyFile?.delete()
  }

  @Test
  fun `buildStorageAuditRequest requires device idle and battery not low`() {
    val request = NetraWorkScheduler.buildStorageAuditRequest()
    assertTrue(request.workSpec.constraints.requiresDeviceIdle())
    assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
  }

  @Test
  fun `DiskUsageHelper skips when device is active and identifies heavy app files during idle window`() {
    val helper = DiskUsageHelper(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val shadowPm = Shadows.shadowOf(pm)
    shadowPm.setIsInteractive(true)
    shadowPm.setIsDeviceIdleMode(false)

    // Active screen-on state -> must skip to preserve battery
    val skippedReport = helper.scanAppStorageIfIdle(fromIdleWorker = false)
    assertFalse(skippedReport.executedDuringIdleWindow)
    assertTrue(skippedReport.skippedReason?.contains("idle") == true)

    // Place a heavy file inside app-specific filesDir
    tempHeavyFile = File(context.filesDir, "heavy_test_dump.bin").apply {
      writeBytes(ByteArray(2048))
    }

    // Idle maintenance window -> scans app-specific storage & detects heavy file
    shadowPm.setIsInteractive(false)
    val idleReport = helper.scanAppStorageIfIdle(
      fromIdleWorker = false,
      temperatureCelsius = 31f,
      batteryPercent = 70,
      heavyFileThresholdBytes = 1024L,
    )
    assertTrue(idleReport.executedDuringIdleWindow)
    assertTrue(idleReport.heavyAppFiles.any { it.name == "heavy_test_dump.bin" && it.sizeBytes >= 2048L })

    // Hot thermal guard postpones even if fromIdleWorker = true
    val hotReport = helper.scanAppStorageIfIdle(
      fromIdleWorker = true,
      temperatureCelsius = 41.5f,
      batteryPercent = 70,
    )
    assertFalse(hotReport.executedDuringIdleWindow)
  }

  @Test
  fun `ChargingVoiceAssistant announces 80 percent milestone only when charging and screen is on`() {
    val assistant = ChargingVoiceAssistant(
      context = context,
      notificationHelper = NotificationHelper(context),
    )
    val prefs = UserPreferences(
      voiceEnabled = true,
      announceCharging = true,
      announceWhileScreenOff = false,
      nightSilentEnabled = false,
    )

    // 1. Not charging, screen on -> must NOT bind or announce
    assistant.announceChargingMilestone(level = 80, prefs = prefs, screenOn = true, isCharging = false)
    assertEquals(0, assistant.assistantState.value.announcementCount)
    assertFalse(assistant.assistantState.value.isTtsBound)

    // 2. Charging, screen off -> must NOT bind or announce
    assistant.announceChargingMilestone(level = 80, prefs = prefs, screenOn = false, isCharging = true)
    assertEquals(0, assistant.assistantState.value.announcementCount)
    assertFalse(assistant.assistantState.value.isTtsBound)

    // 3. Charging AND screen on -> announces milestone cleanly and lazily initializes TTS
    assistant.announceChargingMilestone(level = 80, prefs = prefs, screenOn = true, isCharging = true)
    assertEquals(1, assistant.assistantState.value.announcementCount)
    assertEquals(80, assistant.assistantState.value.lastMilestoneLevel)
    assertEquals("Reached 80 percent charge.", assistant.assistantState.value.lastSpokenMessage)

    assistant.shutdown()
    assertFalse(assistant.assistantState.value.isTtsBound)
  }
}
