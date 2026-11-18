package com.example

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.*
import com.example.widget.GuardianBatteryWidget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h600dp-mdpi", application = Application::class)
class GuardianWidgetTest {
  private val c: Context get() = ApplicationProvider.getApplicationContext()

  @Test fun unknownSensorsNeverBecomeNumbersOrChargingClaims() {
    val m = GuardianBatteryWidget.buildModel(BatteryTelemetry(timestampMillis = 0)).text
    listOf("percent", "temperature", "voltage", "current", "power", "health", "eta").forEach {
      assertEquals(it, "Unavailable", m[it])
    }
    assertEquals("Status: Unavailable", m["status"])
    assertTrue(m.getValue("updated").startsWith("No reading yet"))
  }

  @Test fun realZeroAndColdTemperatureRemainRealButUnknownPowerStaysUnavailable() {
    val m = GuardianBatteryWidget.buildModel(BatteryTelemetry(levelPercent = 0,
      temperatureCelsius = -2f, currentMa = 0f, voltageMv = -1,
      batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING)).text
    assertEquals("0%", m["percent"])
    assertEquals("-2.0°C", m["temperature"])
    assertEquals("0 mA", m["current"])
    assertEquals("Unavailable", m["power"])
    assertEquals("Status: Discharging", m["status"])
    assertTrue(m.getValue("updated").startsWith("Last reading"))
  }

  @Test fun providerAddsNoPeriodicPollingAndRetainsPrivateCachedLastReading() {
    val info = c.packageManager.getReceiverInfo(ComponentName(c, GuardianBatteryWidget::class.java), android.content.pm.PackageManager.GET_META_DATA)
    assertFalse(info.exported)
    val parser = c.resources.getXml(info.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
    while (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) parser.next()
    assertEquals("0", parser.getAttributeValue("http://schemas.android.com/apk/res/android", "updatePeriodMillis"))
    parser.close()
  }

  @Test fun actualRemoteViewsRenderMissingValuesAtMinimumWidth() {
    render(BatteryTelemetry(timestampMillis = 0), "widget-unavailable")
  }
  @Test fun actualRemoteViewsRenderMeasuredFixtureAndZeroCurrent() {
    render(BatteryTelemetry(timestampMillis = 1795000000000L, levelPercent = 72,
      batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING, isCharging = true,
      isPlugged = true, temperatureCelsius = 31.2f, voltageMv = 4120,
      currentMa = 0f, healthState = BatteryHealthState.GOOD), "widget-reported-fixture")
  }

  private fun render(t: BatteryTelemetry, name: String) {
    val root = GuardianBatteryWidget.views(c, GuardianBatteryWidget.buildModel(t)).apply(c, FrameLayout(c))
    root.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(230, View.MeasureSpec.EXACTLY))
    root.layout(0, 0, 280, 230)
    val bitmap = Bitmap.createBitmap(280, 230, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bitmap))
    java.io.File("build/reports/guardian-ui/$name.png").apply { parentFile?.mkdirs() }
      .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    assertEquals(if (t.hasLevel) "${t.levelPercent}%" else "Unavailable", root.findViewById<TextView>(R.id.widget_battery_percent).text.toString())
    assertTrue(root.findViewById<TextView>(R.id.widget_updated).bottom <= root.height - root.paddingBottom)
  }
}
