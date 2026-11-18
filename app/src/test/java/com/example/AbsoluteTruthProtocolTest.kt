package com.example

import android.app.Application
import com.example.domain.engine.DozeEfficiencyCalculator
import com.example.domain.engine.BatteryHealthOptimizer
import com.example.domain.model.*
import com.example.service.DozeMonitorState
import com.example.ui.components.StorageCategoryBreakdownCalculator
import com.example.ui.components.validateHardwareCurrent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class AbsoluteTruthProtocolTest {
  @Test fun unknownDataHasNoDisplayNumberAndRealZeroIsPreserved() {
    assertEquals("", batteryLevelText(-1))
    assertEquals("0%", batteryLevelText(0))
    assertEquals("", capacityText(-1))
    assertEquals("", telemetryText(Float.NaN))
    assertEquals("0.0 mA", telemetryText(0f," mA"))
  }
  @Test fun dozeDurationCannotInventEnergyOrEfficiency() {
    val m=DozeEfficiencyCalculator().calculate(emptyList(),DozeMonitorState(),nowMillis=1000)
    assertTrue(m.estimatedBatterySavedPercent.isNaN())
    assertEquals(-1,m.estimatedMahSaved)
    assertEquals(-1,m.efficiencyScorePercent)
    assertEquals(0L,m.totalDozeTimeMillis)
  }
  @Test fun rawCurrentDoesNotGetRewrittenToMatchChargingState() {
    val reading=validateHardwareCurrent(-700f,true,true)
    assertEquals(-700f,reading.rawCurrentMa,0f)
    assertEquals("-700 mA",reading.liveCurrentText)
    assertFalse(reading.polarityNoteEn.contains("validated"))
  }
  @Test fun celsiusIsNotAnOemLimit() {
    val engine = com.example.domain.engine.TemperatureControlEngine()
    assertEquals(TemperatureZone.UNAVAILABLE, engine.classifyObserved(BatteryTelemetry(temperatureCelsius=45f)))
    assertEquals(TemperatureZone.CRITICAL, engine.classifyObserved(BatteryTelemetry(temperatureCelsius=30f,healthState=BatteryHealthState.OVERHEAT)))
    assertEquals(TemperatureZone.EMERGENCY, engine.classifyObserved(BatteryTelemetry(thermalLevel=ThermalLevel.EMERGENCY)))
  }
  @Test fun storageNeverInventsMissingCategories() {
    val items=StorageCategoryBreakdownCalculator.computeCategories(StorageInsight(0,0,0,0f,0,0),null)
    assertTrue(items.isEmpty())
  }
}
