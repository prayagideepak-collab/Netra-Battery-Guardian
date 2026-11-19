package com.example

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.prefs.UserPreferences
import com.example.data.local.system.*
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.model.*
import com.example.service.BatteryEventQueue
import com.example.service.BatteryThresholdTtsService
import com.example.service.NotificationHelper
import com.example.voice.ChargingVoiceAssistant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StabilityCorrectionTest {
  private lateinit var app: Application
  @Before fun setup() {
    app = ApplicationProvider.getApplicationContext()
    app.getSharedPreferences("guardian_voice_milestones_v1", Context.MODE_PRIVATE).edit().clear().commit()
    app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
  }
  private fun assistant() = ChargingVoiceAssistant(app, NotificationHelper(app))
  private val prefs = UserPreferences(voiceEnabled = true, announceCharging = true, announceDischarging = true, nightSilentEnabled = false)

  @Test fun allFivePercentThresholdsAndOnePercentAreEligible() {
    for (level in 5..100 step 5) {
      assertTrue(BatteryThresholdTtsService.isThresholdEligible(level, true))
      assertTrue(BatteryThresholdTtsService.isThresholdEligible(level, false))
    }
    assertTrue(BatteryThresholdTtsService.isThresholdEligible(1, false))
    assertFalse(BatteryThresholdTtsService.isThresholdEligible(53, true))
  }
  @Test fun screenOffToggleAndVoiceDisableAreRespected() {
    val a = assistant()
    a.announceChargingMilestone(80, prefs.copy(announceWhileScreenOff = false), false)
    assertEquals(0, a.assistantState.value.announcementCount)
    a.announceChargingMilestone(80, prefs.copy(announceWhileScreenOff = true), false)
    assertEquals(1, a.assistantState.value.announcementCount)
    a.announceChargingMilestone(85, prefs.copy(voiceEnabled = false), true)
    assertEquals(1, a.assistantState.value.announcementCount)
    a.shutdown()
  }
  @Test fun criticalLowBypassesNightAndScreenButNotUserVoiceSetting() {
    val a = assistant()
    val night = prefs.copy(nightSilentEnabled = true, nightSilentStartMinutes = 0, nightSilentEndMinutes = 23 * 60, announceWhileScreenOff = false)
    a.announceDischargingMilestone(5, night, false)
    assertEquals(1, a.assistantState.value.announcementCount)
    a.announceDischargingMilestone(1, night.copy(announceDischarging = false), false)
    assertEquals(1, a.assistantState.value.announcementCount)
    a.shutdown()
  }
  @Test fun milestonesDeduplicateAcrossAssistantRecreationAndResetOnDirectionChange() {
    val a = assistant()
    a.observeBatteryDirection(false, false)
    for (level in listOf(100, 95, 90, 85)) {
      a.announceDischargingMilestone(level, prefs, true)
      a.announceDischargingMilestone(level, prefs, true)
    }
    assertEquals(4, a.assistantState.value.announcementCount)
    a.shutdown()
    val recreated = assistant()
    recreated.observeBatteryDirection(false, false)
    recreated.announceDischargingMilestone(85, prefs, true)
    assertEquals(0, recreated.assistantState.value.announcementCount)
    recreated.observeBatteryDirection(true, true)
    recreated.announceChargingMilestone(85, prefs, true)
    assertEquals(1, recreated.assistantState.value.announcementCount)
    recreated.shutdown()
  }
  @Test fun unavailableTtsFallsBackWithoutRepeatedMilestone() {
    val a = assistant()
    a.onInit(android.speech.tts.TextToSpeech.ERROR)
    a.announceChargingMilestone(75, prefs, true)
    a.announceChargingMilestone(75, prefs, true)
    assertEquals(1, a.assistantState.value.announcementCount)
    assertFalse(a.assistantState.value.isTtsBound)
    a.shutdown()
  }
  @Test fun repeatedThermalEvaluationsDoNotRatchetBatteryHysteresis() {
    val engine = AdaptivePowerTierEngine()
    val sample = BatteryTelemetry(levelPercent = 31, thermalLevel = ThermalLevel.MODERATE, thermalStatusAvailable = true)
    repeat(30) { assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(sample, 0, 0).activeTier) }
    assertEquals(AdaptivePowerTier.NORMAL, engine.evaluate(sample.copy(thermalLevel = ThermalLevel.NONE), 0, 0).activeTier)
  }
  @Test fun unavailableThermalStatusDoesNotClearALatchedWarning() {
    val engine = AdaptivePowerTierEngine()
    val sample = BatteryTelemetry(levelPercent = 80, thermalLevel = ThermalLevel.MODERATE, thermalStatusAvailable = true)
    assertTrue(engine.evaluate(sample, 0, 0).isThermalThrottled)
    assertTrue(engine.evaluate(sample.copy(thermalLevel = ThermalLevel.NONE, thermalStatusAvailable = false, temperatureCelsius = Float.NaN), 0, 0).isThermalThrottled)
    assertFalse(engine.evaluate(sample.copy(thermalLevel = ThermalLevel.NONE), 0, 0).isThermalThrottled)
  }
  @Test fun storageScanCooldownHotLowUnknownAndCancellation() {
    val cache = File(app.cacheDir, "cooldown").apply { writeBytes(ByteArray(1024)) }
    val inspector = StorageInspector(app)
    assertEquals(-1L, inspector.inspect(45f, 80).appOwnCacheBytes)
    assertEquals(-1L, inspector.inspect(30f, 5).appOwnCacheBytes)
    assertEquals(-1L, inspector.inspect(Float.NaN, 80).appOwnCacheBytes)
    assertEquals(-1L, inspector.inspect(30f, 80, shouldContinue = { false }).appOwnCacheBytes)
    val first = inspector.inspect(30f, 80).appOwnCacheBytes
    assertTrue(first >= 1024L)
    cache.appendBytes(ByteArray(1024))
    assertEquals(first, inspector.inspect(30f, 80).appOwnCacheBytes)
    inspector.scanCooldownMillis = 0
    assertTrue(inspector.inspect(30f, 80).appOwnCacheBytes >= first + 1024L)
  }
  @Test fun cacheCleanupNeverTouchesFilesOrDatabaseAndMeasuresFreedBytes() {
    val database = File(app.filesDir, "important.db").apply { writeText("keep") }
    File(app.cacheDir, "eligible.bin").writeBytes(ByteArray(8192))
    val inspector = StorageInspector(app)
    assertEquals(0L, inspector.cleanOwnCache { false })
    assertTrue(inspector.cleanOwnCache() >= 8192L)
    assertEquals("keep", database.readText())
    database.delete()
  }
  @Test fun maintenanceThresholdAndTierGatesStayConservative() {
    val t = BatteryTelemetry(levelPercent = 80, temperatureCelsius = 30f, healthState = BatteryHealthState.GOOD)
    assertTrue(StorageInspector.maintenanceAllowed(t))
    assertFalse(StorageInspector.maintenanceAllowed(t.copy(levelPercent = 14)))
    assertFalse(StorageInspector.maintenanceAllowed(t.copy(temperatureCelsius = Float.NaN)))
    assertFalse(StorageInspector.maintenanceAllowed(t.copy(thermalLevel = ThermalLevel.MODERATE)))
    val engine = AdaptivePowerTierEngine()
    assertFalse(engine.evaluate(t.copy(levelPercent = 4), 0, 0).allowsUsageInspection())
  }
  @Test fun diskScanRequiresKnownInputsAndAcceptsCancellation() {
    val helper = DiskUsageHelper(app)
    assertFalse(helper.scanAppStorageIfIdle(true).executedDuringIdleWindow)
    assertFalse(helper.scanAppStorageIfIdle(true, 30f, 80, shouldContinue = { false }).executedDuringIdleWindow)
  }
  @Test fun usageRevocationClearsCachedResultsAndDoesNotThrow() {
    val inspector = AppPowerInspector(app)
    val ops = app.getSystemService(AppOpsManager::class.java)
    org.robolectric.Shadows.shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
    assertNotNull(inspector.collectTopApps())
    org.robolectric.Shadows.shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_IGNORED)
    assertFalse(inspector.hasUsageStatsPermission())
    assertTrue(inspector.collectTopApps().isEmpty())
  }
  @Test fun eventBurstRetainsObservedMilestonesAndForceFlagWithoutCoroutinePerEvent() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val gate = CompletableDeferred<Unit>()
    val values = mutableListOf<Pair<Int, Boolean>>()
    val queue = BatteryEventQueue<Int>(scope, { it }) { value, force -> gate.await(); values.add(value to force) }
    queue.submit(100)
    repeat(1000) { queue.submit(95) }
    queue.submit(90, true); queue.submit(90); queue.submit(85)
    gate.complete(Unit)
    yield()
    assertEquals(listOf(100, 95, 90, 85), values.map { it.first })
    assertTrue(values.single { it.first == 90 }.second)
    scope.cancel()
  }
  @Test fun bluetoothNeverTurnsMissingSignalGreenAndDenialIsSafe() {
    assertEquals("Unavailable", bluetoothSignalLabel(null))
    assertEquals("Unavailable", bluetoothSignalLabel(0))
    assertEquals("Stronger signal (estimate)", bluetoothSignalLabel(-55))
    assertEquals("Intermediate signal (estimate)", bluetoothSignalLabel(-75))
    assertEquals("Weaker signal (estimate)", bluetoothSignalLabel(-95))
    val monitor = ConnectedBluetoothMonitor(app)
    monitor.refresh()
    monitor.close()
    assertTrue(monitor.devices.value.isEmpty())
  }
}
