package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import com.example.ui.components.SentinelTemperatureStatus
import com.example.ui.components.classifySentinelTemperature
import java.util.Locale

/**
 * Home screen widget displaying the real-time battery percentage (%) and high-level
 * thermal status indicator (Normal, Warm, Hot, Critical, or Unavailable) from
 * native Android [BatteryManager] & [Intent.ACTION_BATTERY_CHANGED].
 */
class BatteryThermalWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    val snapshot = readHardwareWidgetSnapshot(context)
    for (appWidgetId in appWidgetIds) {
      val views = buildRemoteViews(context, snapshot)
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH_WIDGET || intent.action == Intent.ACTION_BATTERY_CHANGED) {
      updateAllWidgets(context)
    }
  }

  data class WidgetHardwareSnapshot(
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val thermalStatus: SentinelTemperatureStatus,
    val isCharging: Boolean,
    val isPlugged: Boolean,
    val plugTypeLabel: String,
    val voltageMv: Int,
  ) {
    val batteryPercentText: String
      get() = batteryLevelText(levelPercent)

    val thermalBadgeText: String
      get() = if (temperatureCelsius.isFinite()) {
        telemetryText(temperatureCelsius, "°C", 1)
      } else {
        ""
      }

    val chargingStatusText: String
      get() = when {
        levelPercent !in 0..100 -> ""
        isCharging && plugTypeLabel.isNotBlank() && plugTypeLabel != "NONE" -> "Charging ($plugTypeLabel)"
        isCharging -> "Charging"
        isPlugged -> "Plugged (Not Charging)"
        else -> "Discharging"
      }

    val subtitleText: String
      get() = if (voltageMv > 0) {
        String.format(Locale.US, "%d mV (%.2f V) • Tap for Sentinel", voltageMv, voltageMv / 1000f)
      } else {
        ""
      }
  }

  companion object {
    const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_BATTERY_WIDGET"

    fun readHardwareWidgetSnapshot(
      context: Context,
      intentOverride: Intent? = null,
    ): WidgetHardwareSnapshot {
      val sticky = intentOverride ?: context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
      )

      val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
      val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      val capacityProp = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE

      val levelPercent = when {
        level >= 0 && scale > 0 -> ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        capacityProp in 0..100 -> capacityProp
        else -> -1
      }

      val tempTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
      val tempCelsius = if (tempTenths != Int.MIN_VALUE && tempTenths in -400..1200) {
        tempTenths / 10f
      } else {
        Float.NaN
      }

      val status = sticky?.getIntExtra(
        BatteryManager.EXTRA_STATUS,
        BatteryManager.BATTERY_STATUS_UNKNOWN,
      ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
      val isCharging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL

      val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
      val isPlugged = plugged != 0
      val plugLabel = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
        else -> "NONE"
      }

      val rawVoltage = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
      val voltageMv = when {
        rawVoltage <= 0 -> -1
        rawVoltage in 1..20 -> -1
        else -> rawVoltage
      }

      val thermalStatus = classifySentinelTemperature(tempCelsius)

      return WidgetHardwareSnapshot(
        levelPercent = levelPercent,
        temperatureCelsius = tempCelsius,
        thermalStatus = thermalStatus,
        isCharging = isCharging,
        isPlugged = isPlugged,
        plugTypeLabel = plugLabel,
        voltageMv = voltageMv,
      )
    }

    fun buildRemoteViews(
      context: Context,
      snapshot: WidgetHardwareSnapshot,
    ): RemoteViews {
      val views = RemoteViews(context.packageName, R.layout.widget_battery_thermal)

      views.setTextViewText(R.id.widget_battery_percent, snapshot.batteryPercentText)
      views.setTextViewText(R.id.widget_thermal_badge, snapshot.thermalBadgeText)
      views.setTextViewText(R.id.widget_charging_status, snapshot.chargingStatusText)
      views.setTextViewText(R.id.widget_voltage_subtitle, snapshot.subtitleText)

      val badgeTextColor = when (snapshot.thermalStatus) {
        SentinelTemperatureStatus.NORMAL -> Color.parseColor("#2EE59D")
        SentinelTemperatureStatus.WARM -> Color.parseColor("#FFB547")
        SentinelTemperatureStatus.HOT -> Color.parseColor("#FF7A45")
        SentinelTemperatureStatus.CRITICAL -> Color.parseColor("#FF4D6D")
        SentinelTemperatureStatus.UNAVAILABLE -> Color.parseColor("#5CE1E6")
      }
      views.setTextColor(R.id.widget_thermal_badge, badgeTextColor)

      val percentColor = when {
        snapshot.levelPercent !in 0..100 -> Color.parseColor("#94A3B8")
        snapshot.levelPercent <= 15 && !snapshot.isCharging -> Color.parseColor("#FF4D6D")
        snapshot.isCharging -> Color.parseColor("#2EE59D")
        else -> Color.parseColor("#F8FAFC")
      }
      views.setTextColor(R.id.widget_battery_percent, percentColor)

      val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
      val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, pendingFlags)
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

      return views
    }

    fun updateAllWidgets(
      context: Context,
      intentOverride: Intent? = null,
    ) {
      runCatching {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, BatteryThermalWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
          val snapshot = readHardwareWidgetSnapshot(context, intentOverride)
          for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context, snapshot))
          }
        }
      }
    }
  }
}
