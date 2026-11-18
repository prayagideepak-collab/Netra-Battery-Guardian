package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** A view of the existing monitor's last reading. No receiver for battery polling, job or alarm. */
class GuardianBatteryWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val model = Model(KEYS.associateWith { p.getString(it, null) ?: defaults.getValue(it) })
    ids.forEach { manager.updateAppWidget(it, views(context, model)) }
  }

  data class Model(val text: Map<String, String>)

  companion object {
    private const val PREFS = "guardian_widget_last_reading"
    private const val U = "Unavailable"
    private val defaults = linkedMapOf(
      "percent" to U, "status" to "Status: $U", "temperature" to U,
      "voltage" to U, "current" to U, "power" to U, "health" to U,
      "etaLabel" to "Time left", "eta" to U,
      "updated" to "No reading yet · Tap to open Guardian",
    )
    private val KEYS = defaults.keys

    fun buildModel(t: BatteryTelemetry, diagnosis: ChargingDiagnosis? = null): Model {
      val knownStatus = when (t.batteryStatus) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> U
      }
      val health = when (t.healthState) {
        BatteryHealthState.UNKNOWN -> U
        else -> t.healthState.name.lowercase(Locale.US).replace('_', ' ')
          .replaceFirstChar { it.uppercase() }
      }
      val eta = if (t.isCharging) diagnosis?.estimatedMinutesRemaining?.takeIf { it > 0 }
        ?.let { "Approx. ${it / 60}h ${it % 60}m" } ?: U else U
      val stamp = if (t.timestampMillis > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.timestampMillis)) else null
      return Model(linkedMapOf(
        "percent" to batteryLevelText(t.levelPercent), "status" to "Status: $knownStatus",
        "temperature" to telemetryText(t.temperatureCelsius, "°C"),
        "voltage" to telemetryText(t.voltageVolts, " V", 2),
        "current" to telemetryText(abs(t.currentMa), " mA", 0),
        "power" to telemetryText(abs(t.instantaneousPowerWatts), " W"),
        "health" to health, "etaLabel" to if (t.isCharging) "Full in" else "Time left",
        "eta" to eta,
        // Always label this as a last reading. With no polling, its age changes even
        // while a launcher retains the same RemoteViews after the monitor stops.
        "updated" to (stamp?.let { "Last reading $it · Tap to open Guardian" }
          ?: "No reading yet · Tap to open Guardian"),
      ))
    }

    fun views(context: Context, model: Model): RemoteViews {
      val v = RemoteViews(context.packageName, R.layout.guardian_battery_widget)
      val fields = mapOf("percent" to R.id.widget_battery_percent, "status" to R.id.widget_battery_status,
        "temperature" to R.id.widget_temp, "voltage" to R.id.widget_voltage,
        "current" to R.id.widget_current, "power" to R.id.widget_power,
        "health" to R.id.widget_health, "etaLabel" to R.id.widget_eta_label,
        "eta" to R.id.widget_eta, "updated" to R.id.widget_updated)
      fields.forEach { (key, id) -> v.setTextViewText(id, model.text.getValue(key)) }
      val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      v.setOnClickPendingIntent(R.id.widget_root, open)
      return v
    }

    @Synchronized fun publish(context: Context, t: BatteryTelemetry, diagnosis: ChargingDiagnosis? = null) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, GuardianBatteryWidget::class.java))
      if (ids.isEmpty()) return
      val model = buildModel(t, diagnosis)
      val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      if (KEYS.all { p.getString(it, null) == model.text[it] }) return
      val edit = p.edit()
      model.text.forEach { (key, value) -> edit.putString(key, value) }
      edit.apply()
      ids.forEach { manager.updateAppWidget(it, views(context, model)) }
    }

    fun clear(context: Context) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, GuardianBatteryWidget::class.java))
      ids.forEach { manager.updateAppWidget(it, views(context, Model(defaults))) }
    }
  }
}
