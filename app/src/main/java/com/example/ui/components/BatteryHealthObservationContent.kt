package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.telemetryText

/** Existing health card content: independent OS observations, never a condition score. */
@Composable
fun BatteryHealthObservationContent(t: BatteryTelemetry, isHindi: Boolean = false) {
  Text(
    text = "${if (isHindi) "Android स्वास्थ्य श्रेणी" else "Android health category"}: ${formatBatteryHealthState(t.healthState, isHindi)}",
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier.testTag("observed_health_category"),
  )
  Text(
    text = if (isHindi) "बैटरी स्वास्थ्य %: Unavailable" else "Battery health %: Unavailable",
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.testTag("observed_health_percentage"),
  )
  Text("${if (isHindi) "तापमान" else "Temperature"}: ${telemetryText(t.temperatureCelsius, " °C").ifBlank { "Unavailable" }}")
  Text("${if (isHindi) "वोल्टेज" else "Voltage"}: ${telemetryText(t.voltageVolts, " V", 2).ifBlank { "Unavailable" }}")
  Text("${if (isHindi) "OS चक्र गणना" else "OS cycle count"}: ${t.chargeCycleCount?.takeIf { it >= 0 }?.toString() ?: "Unavailable"}")
  val remaining = t.chargeCounterUah.takeIf { it > 0 }?.let { "$it µAh" } ?: "Unavailable"
  Text("${if (isHindi) "शेष चार्ज काउंटर" else "Remaining-charge counter"}: $remaining")
  Text(
    text = if (isHindi) "ये अलग-अलग Android माप हैं, बैटरी की हालत का स्कोर नहीं। सत्यापित पूर्ण-चार्ज और डिज़ाइन क्षमता के बिना स्वास्थ्य % नहीं निकाला जाता। शेष चार्ज को स्तर से भाग देना स्वास्थ्य का माप नहीं है।"
      else "Independent Android observations, not a condition score. Verified full-charge and design-capacity inputs are unavailable. Remaining charge divided by battery level is not a measured health baseline.",
    style = MaterialTheme.typography.bodySmall,
  )
}
