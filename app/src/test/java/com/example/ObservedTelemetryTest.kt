package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.system.BatteryTelemetryCollector
import com.example.domain.engine.ChargingSpeedClassifier
import com.example.domain.engine.TemperatureControlEngine
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObservedTelemetryTest {
  @Test fun `missing sensors stay unavailable despite a legacy capacity cache`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val shadow = Shadows.shadowOf(manager)
    shadow.setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
    shadow.setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, Int.MIN_VALUE)
    shadow.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, Int.MIN_VALUE)
    shadow.setLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, Long.MIN_VALUE)
    val t = BatteryTelemetryCollector(context).collect(Intent(Intent.ACTION_BATTERY_CHANGED), 4500)
    assertFalse(t.hasLevel)
    assertFalse(t.hasTemperature)
    assertFalse(t.hasPower)
    assertEquals(-1, t.estimatedCapacityMah)
    assertEquals("Unavailable", telemetryText(t.currentMa))
    assertEquals(TemperatureZone.UNAVAILABLE, TemperatureControlEngine().classifyZone(t.temperatureCelsius))
    assertNull(ChargingSpeedClassifier().diagnose(t).estimatedMinutesRemaining)
  }

  @Test fun `partial observations survive Room without inventing missing fields`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.db.AppDatabase::class.java)
      .allowMainThreadQueries().build()
    try {
      val repo = com.example.data.repository.BatteryHistoryRepository(db.netraDao(), db.batteryDao())
      repo.recordSnapshot(BatteryTelemetry(levelPercent = 67), GuardianProfile.NORMAL)
      val rows = repo.getSnapshotsSince(0)
      assertEquals(1, rows.size)
      assertEquals(67, rows.first().levelPercent)
      assertFalse(rows.first().temperatureCelsius.isFinite())
      assertFalse(rows.first().currentMa.isFinite())
      assertEquals("Unavailable", telemetryText(rows.first().currentMa))
    } finally { db.close() }
  }

  @Test fun `microamps and sign are never guessed from magnitude or charging status`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    Shadows.shadowOf(manager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, -20_000)
    val intent = Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
      .putExtra(BatteryManager.EXTRA_LEVEL, 80).putExtra(BatteryManager.EXTRA_SCALE, 100)
    assertEquals(-20f, BatteryTelemetryCollector(context).collect(intent).currentMa, 0f)
  }

  @Test fun `ETA waits for real progression and resets across sessions or stale gaps`() {
    val classifier = ChargingSpeedClassifier()
    fun sample(time: Long, level: Int, plugged: Boolean = true) = BatteryTelemetry(timestampMillis = time, levelPercent = level, isPlugged = plugged, isCharging = plugged)
    val first = sample(1_000_000, 80)
    classifier.recordSample(first)
    assertNull(classifier.diagnose(first).estimatedMinutesRemaining)
    classifier.recordSample(sample(1_060_000, 81))
    val third = sample(1_120_000, 82)
    classifier.recordSample(third)
    assertEquals(18, classifier.diagnose(third).estimatedMinutesRemaining)
    assertTrue(classifier.diagnose(third).isTimeEstimateApproximate)
    val unplugged = sample(1_180_000, 82, false)
    classifier.recordSample(unplugged)
    assertNull(classifier.diagnose(unplugged).estimatedMinutesRemaining)
    val stale = sample(2_000_000, 80, false)
    classifier.recordSample(stale)
    assertNull(classifier.diagnose(stale).estimatedMinutesRemaining)
  }
}
