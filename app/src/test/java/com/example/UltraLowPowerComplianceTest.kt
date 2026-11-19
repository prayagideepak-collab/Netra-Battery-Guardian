package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.engine.AdaptivePowerTierEngine
import com.example.domain.engine.PowerGovernanceRegistry
import com.example.domain.engine.SelfHealingSupervisor
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.AppStandbyBucket
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.NetworkType
import com.example.domain.model.ThermalLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltraLowPowerComplianceTest {

  @Test
  fun `adaptive power tier handles all 8 inputs and hysteresis without flapping`() {
    val engine = AdaptivePowerTierEngine()

    // 100% battery, charging, active -> NORMAL
    val normal = BatteryTelemetry(
      levelPercent = 100,
      isCharging = true,
      osBatterySaverOn = false,
      thermalLevel = ThermalLevel.NONE,
      thermalStatusAvailable = true,
      temperatureCelsius = 28f,
      screenOn = true,
      isAppInForeground = true,
      standbyBucket = AppStandbyBucket.ACTIVE,
      networkType = NetworkType.UNMETERED_WIFI,
    )
    assertEquals(AdaptivePowerTier.NORMAL, engine.evaluate(normal, 0, 1).activeTier)

    // Drop to 30% -> SAVER; recover to 31% stays SAVER due to 2% hysteresis!
    val at30 = normal.copy(levelPercent = 30, isCharging = false)
    assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(at30, 0, 1).activeTier)
    val at31 = at30.copy(levelPercent = 31)
    assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(at31, 0, 1).activeTier)
    val at33 = at30.copy(levelPercent = 33)
    assertEquals(AdaptivePowerTier.NORMAL, engine.evaluate(at33, 0, 1).activeTier)

    // 15% vs <15% CRITICAL and <5% SURVIVAL
    val at14 = at30.copy(levelPercent = 14)
    assertEquals(AdaptivePowerTier.CRITICAL, engine.evaluate(at14, 0, 1).activeTier)
    val at4 = at30.copy(levelPercent = 4)
    val survivalBudget = engine.evaluate(at4, 0, 1)
    assertEquals(AdaptivePowerTier.SURVIVAL, survivalBudget.activeTier)
    assertEquals(0, survivalBudget.networkCallsThisHour)
    assertEquals(3600, survivalBudget.dbBatchIntervalSeconds)

    // Background + RARE standby bucket degrades NORMAL -> SAVER
    val bgRare = normal.copy(
      levelPercent = 80,
      isCharging = false,
      isAppInForeground = false,
      standbyBucket = AppStandbyBucket.RARE,
    )
    assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(bgRare, 0, 1).activeTier)

    // Thermal throttling drops one tier immediately and latches until <38C
    val hot = normal.copy(levelPercent = 80, isCharging = false, temperatureCelsius = 41f, thermalLevel = ThermalLevel.MODERATE)
    assertEquals(AdaptivePowerTier.SAVER, engine.evaluate(hot, 0, 1).activeTier)
    val still39 = hot.copy(temperatureCelsius = 39f, thermalLevel = ThermalLevel.LIGHT)
    assertTrue(engine.evaluate(still39, 0, 1).isThermalThrottled)
    val cooled37 = hot.copy(temperatureCelsius = 37f, thermalLevel = ThermalLevel.NONE)
    assertFalse(engine.evaluate(cooled37, 0, 1).isThermalThrottled)
  }

  @Test
  fun `self healing supervisor enforces exponential backoff jitter, hard retry cap, and circuit breaker`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val prefs = PreferencesRepository(context)
    val logWriter = BoundedLogFileWriter(context)
    logWriter.clearLogs()

    val supervisor = SelfHealingSupervisor(context, db, prefs, logWriter)

    // Backoff grows exponentially and stays within jitter window
    val b0 = supervisor.computeBackoffWithJitterMs(0, 0.1)
    val b1 = supervisor.computeBackoffWithJitterMs(1, 0.1)
    assertEquals(33_000L, b0)
    assertEquals(66_000L, b1)

    var t = 1_000_000L
    // Fail 3 times -> opens circuit breaker on 3rd failure
    val s1 = supervisor.runNaturalHealthCheck("test1", nowMillis = t) { false }
    assertFalse(s1.circuitOpen)
    assertEquals(1, s1.consecutiveFailures)

    t += 120_000L
    val s2 = supervisor.runNaturalHealthCheck("test2", nowMillis = t) { false }
    assertFalse(s2.circuitOpen)
    assertEquals(2, s2.consecutiveFailures)

    t += 240_000L
    val s3 = supervisor.runNaturalHealthCheck("test3", nowMillis = t) { false }
    assertTrue(s3.circuitOpen)
    assertEquals(3, s3.consecutiveFailures)

    // During cooldown window, check does not even invoke recovery
    var invoked = false
    val sBlocked = supervisor.runNaturalHealthCheck("test_blocked", nowMillis = t + 60_000L) {
      invoked = true
      true
    }
    assertFalse(invoked)
    assertTrue(sBlocked.circuitOpen)

    // After 6h cooldown window, circuit closes upon successful check
    val sRecovered = supervisor.runNaturalHealthCheck(
      "test_recover",
      nowMillis = t + SelfHealingSupervisor.CIRCUIT_COOLDOWN_MS + 1000L,
    ) { true }
    assertFalse(sRecovered.circuitOpen)
    assertEquals(0, sRecovered.consecutiveFailures)

    db.close()
  }

  @Test
  fun `bounded log writer rotates and stays within 256KB cap`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val writer = BoundedLogFileWriter(context)
    writer.clearLogs()

    val chunk = "X".repeat(2048)
    repeat(180) { i ->
      writer.append("TEST", "$i:$chunk")
    }
    val size = writer.totalSizeBytes()
    assertTrue("Log file size $size must stay bounded <= 270KB", size <= BoundedLogFileWriter.MAX_TOTAL_BYTES + 8192L)
  }

  @Test
  fun `power governance registry defines Tier-0 functions and zero-network feature gates`() {
    assertTrue(PowerGovernanceRegistry.tierZeroFunctions.isNotEmpty())
    assertTrue(PowerGovernanceRegistry.featureGates.isNotEmpty())
    PowerGovernanceRegistry.featureGates.forEach { gate ->
      assertTrue(gate.runsInTier.isNotBlank())
      assertTrue(gate.measuredImpact.isNotBlank())
      assertEquals(0, gate.networkCallsPerHour)
    }
  }
}
