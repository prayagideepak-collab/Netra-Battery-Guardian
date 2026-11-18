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
    private const val U = ""
    private val defaults = linkedMapOf(
      "percent" to U, "status" to U, "temperature" to U,
      "voltage" to U, "current" to U, "power" to U, "health" to U,
      "etaLabel" to "Time left", "eta" to U,
      "updated" to "No reading yet · Tap to open Guardian", "title" to "NETRA BATTERY GUARDIAN",
      "tempLabel" to "Temperature", "voltageLabel" to "Voltage", "currentLabel" to "Signed current",
      "powerLabel" to "Signed power", "healthLabel" to "OS health",
    )
    private val KEYS = defaults.keys

    fun buildModel(t: BatteryTelemetry, diagnosis: ChargingDiagnosis? = null, language: AppLanguage = AppLanguage.ENGLISH): Model {
      val hi = language == AppLanguage.HINDI
      fun word(en: String, hindi: String) = if (hi) hindi else en
      val knownStatus = when (t.batteryStatus) {
        BatteryManager.BATTERY_STATUS_CHARGING -> word("Charging", "चार्जिंग")
        BatteryManager.BATTERY_STATUS_DISCHARGING -> word("Discharging", "डिस्चार्जिंग")
        BatteryManager.BATTERY_STATUS_FULL -> word("Full", "पूर्ण")
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> word("Not charging", "चार्ज नहीं हो रहा")
        else -> U
      }
      val health = when (t.healthState) {
        BatteryHealthState.UNKNOWN -> U
        BatteryHealthState.GOOD -> word("Good", "अच्छी")
        BatteryHealthState.OVERHEAT -> word("Overheat", "अधिक गर्म")
        BatteryHealthState.DEAD -> word("Dead", "खराब")
        BatteryHealthState.OVER_VOLTAGE -> word("Over voltage", "अधिक वोल्टेज")
        BatteryHealthState.UNSPECIFIED_FAILURE -> word("Failure", "विफलता")
        BatteryHealthState.COLD -> word("Cold", "ठंडी")
      }
      val full = t.batteryStatus == BatteryManager.BATTERY_STATUS_FULL && t.levelPercent == 100
      val eta = if (full) word("Full", "पूर्ण") else diagnosis?.estimatedMinutesRemaining?.takeIf { it >= 0 }
        ?.let { word("Approx. ${it / 60}h ${it % 60}m", "लगभग ${it / 60}घं ${it % 60}मि") } ?: U
      val stamp = if (t.timestampMillis > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.timestampMillis)) else null
      return Model(linkedMapOf(
        "percent" to batteryLevelText(t.levelPercent), "status" to if (knownStatus.isBlank()) "" else "${word("Status", "स्थिति")}: $knownStatus",
        "temperature" to telemetryText(t.temperatureCelsius, "°C"),
        "voltage" to telemetryText(t.voltageVolts, " V", 2),
        "current" to telemetryText(t.currentMa, " mA", 0),
        "power" to telemetryText(t.instantaneousPowerWatts, " W"),
        "health" to health, "etaLabel" to if (full) word("Battery", "बैटरी") else if (t.isCharging) word("Full in", "पूर्ण होने में") else word("Time left", "शेष समय"),
        "eta" to eta, "title" to word("NETRA BATTERY GUARDIAN", "नेत्र बैटरी गार्जियन"),
        "tempLabel" to word("Temperature", "तापमान"), "voltageLabel" to word("Voltage", "वोल्टेज"),
        "currentLabel" to word("Signed current", "करंट (+/−)"), "powerLabel" to word("Signed power", "पावर (+/−)"),
        "healthLabel" to word("OS health", "OS स्थिति"),
        // Always label this as a last reading. With no polling, its age changes even
        // while a launcher retains the same RemoteViews after the monitor stops.
        "updated" to (stamp?.let { word("Last reading $it · Tap to open Guardian", "अंतिम रीडिंग $it · Guardian खोलें") }
          ?: word("No reading yet · Tap to open Guardian", "अभी रीडिंग नहीं · Guardian खोलें")),
      ))
    }

    fun views(context: Context, model: Model): RemoteViews {
      val v = RemoteViews(context.packageName, R.layout.guardian_battery_widget)
      val fields = mapOf("percent" to R.id.widget_battery_percent, "status" to R.id.widget_battery_status,
        "temperature" to R.id.widget_temp, "voltage" to R.id.widget_voltage,
        "current" to R.id.widget_current, "power" to R.id.widget_power,
        "health" to R.id.widget_health, "etaLabel" to R.id.widget_eta_label,
        "eta" to R.id.widget_eta, "updated" to R.id.widget_updated, "title" to R.id.widget_title,
        "tempLabel" to R.id.t_label, "voltageLabel" to R.id.v_label, "currentLabel" to R.id.c_label,
        "powerLabel" to R.id.p_label, "healthLabel" to R.id.h_label)
      fields.forEach { (key, id) -> v.setTextViewText(id, model.text.getValue(key)) }
      val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      v.setOnClickPendingIntent(R.id.widget_root, open)
      return v
    }

    @Synchronized fun publish(context: Context, t: BatteryTelemetry, diagnosis: ChargingDiagnosis? = null, language: AppLanguage = AppLanguage.ENGLISH) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, GuardianBatteryWidget::class.java))
      val model = buildModel(t, diagnosis, language)
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
