package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.db.BatterySnapshot
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Jetpack Compose Canvas visualization plotting hourly Battery Temperature (°C)
 * and Voltage (V) stored in Room [BatterySnapshot] rows.
 *
 * Refreshes strictly while the hosting UI is visible (Lifecycle.State.STARTED),
 * avoiding background database observation and unnecessary recompositions.
 */
@Composable
fun HourlyTempVoltageChart(
  snapshots: List<BatterySnapshot>,
  isHindi: Boolean = false,
  isLowPowerSurvival: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val ordered = remember(snapshots) { snapshots.sortedBy { it.timestampMillis } }
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  val tempColor = SemanticWarmAmber
  val voltColor = SemanticIceCyan
  val warnColor = SemanticCriticalRed.copy(alpha = 0.55f)
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("hourly_temp_voltage_chart"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Legend & Active Power-Aware Refresh Note
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusPill(
          text = if (isHindi) "तापमान (°C)" else "Temp (°C)",
          color = tempColor,
        )
        StatusPill(
          text = if (isHindi) "वोल्टेज (V)" else "Voltage (V)",
          color = voltColor,
        )
      }
      Text(
        text = if (isLowPowerSurvival) {
          if (isHindi) "सर्वाइवल: लाइव रिफ्रेश विराम" else "Survival: static view"
        } else {
          if (isHindi) "केवल दृश्यमान होने पर रिफ्रेश" else "Visible-only sync"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (ordered.size < 2) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(150.dp)
          .testTag("hourly_chart_empty_state"),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ShowChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
          )
          Text(
            text = if (isHindi) {
              "ग्राफ़ के लिए कम से कम 2 प्रति घंटा स्नैपशॉट प्रतीक्षारत हैं (${ordered.size}/2)।"
            } else {
              "Waiting for at least 2 hourly Room snapshots to plot chart (${ordered.size}/2)."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      return
    }

    val temps = remember(ordered) { ordered.map { it.temperatureCelsius } }
    val volts = remember(ordered) { ordered.map { it.voltageMv / 1000f } }

    val minTemp = remember(temps) { minOf(15f, (temps.minOrNull() ?: 20f) - 3f) }
    val maxTemp = remember(temps) { maxOf(48f, (temps.maxOrNull() ?: 40f) + 3f) }
    val minVolt = remember(volts) { minOf(3.2f, (volts.minOrNull() ?: 3.5f) - 0.15f) }
    val maxVolt = remember(volts) { maxOf(4.5f, (volts.maxOrNull() ?: 4.3f) + 0.15f) }

    val summaryDescription =
      "Hourly chart with ${ordered.size} points. Latest ${String.format(Locale.US, "%.1f", temps.last())}°C and ${
        String.format(Locale.US, "%.2f", volts.last())
      }V."

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left axis (°C) labels
      Column(
        modifier = Modifier.height(160.dp).padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text("${maxTemp.toInt()}°", style = TelemetryMonoStyle, color = tempColor)
        Text("${((maxTemp + minTemp) / 2f).toInt()}°", style = TelemetryMonoStyle, color = tempColor.copy(alpha = 0.75f))
        Text("${minTemp.toInt()}°", style = TelemetryMonoStyle, color = tempColor)
      }

      Canvas(
        modifier = Modifier
          .weight(1f)
          .height(160.dp)
          .padding(vertical = 8.dp)
          .testTag("hourly_temp_voltage_canvas")
          .semantics { contentDescription = summaryDescription },
      ) {
        val tempSpan = (maxTemp - minTemp).coerceAtLeast(1f)
        val voltSpan = (maxVolt - minVolt).coerceAtLeast(0.1f)
        val stepX = size.width / (ordered.size - 1).coerceAtLeast(1)

        // Subtle horizontal gridlines
        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
          val y = size.height * frac
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
          )
        }

        // High-temperature 40°C warning threshold dashed line
        if (40f in minTemp..maxTemp) {
          val warnY = size.height - (((40f - minTemp) / tempSpan).coerceIn(0f, 1f) * size.height)
          drawLine(
            color = warnColor,
            start = Offset(0f, warnY),
            end = Offset(size.width, warnY),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
          )
        }

        val tempLine = Path()
        val tempFill = Path()
        val voltLine = Path()

        val tempPoints = ArrayList<Offset>(ordered.size)
        val voltPoints = ArrayList<Offset>(ordered.size)

        ordered.forEachIndexed { idx, snap ->
          val x = idx * stepX
          val tempNorm = ((snap.temperatureCelsius - minTemp) / tempSpan).coerceIn(0f, 1f)
          val tempY = size.height - tempNorm * size.height
          val voltVal = snap.voltageMv / 1000f
          val voltNorm = ((voltVal - minVolt) / voltSpan).coerceIn(0f, 1f)
          val voltY = size.height - voltNorm * size.height

          val tPt = Offset(x, tempY)
          val vPt = Offset(x, voltY)
          tempPoints += tPt
          voltPoints += vPt

          if (idx == 0) {
            tempLine.moveTo(x, tempY)
            tempFill.moveTo(x, size.height)
            tempFill.lineTo(x, tempY)
            voltLine.moveTo(x, voltY)
          } else {
            tempLine.lineTo(x, tempY)
            tempFill.lineTo(x, tempY)
            voltLine.lineTo(x, voltY)
          }
          if (idx == ordered.lastIndex) {
            tempFill.lineTo(x, size.height)
            tempFill.close()
          }
        }

        // Temperature area gradient + curve
        drawPath(
          path = tempFill,
          brush = Brush.verticalGradient(
            colors = listOf(tempColor.copy(alpha = 0.24f), tempColor.copy(alpha = 0.02f)),
          ),
        )
        drawPath(
          path = tempLine,
          color = tempColor,
          style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
        )

        // Voltage curve
        drawPath(
          path = voltLine,
          color = voltColor,
          style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Plot individual checkpoint nodes
        val dotRadius = if (ordered.size <= 24) 3.5.dp.toPx() else 2.dp.toPx()
        tempPoints.forEach { pt ->
          drawCircle(color = tempColor, radius = dotRadius, center = pt)
        }
        voltPoints.forEach { pt ->
          drawCircle(color = voltColor, radius = dotRadius, center = pt)
        }
      }

      // Right axis (V) labels
      Column(
        modifier = Modifier.height(160.dp).padding(start = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
      ) {
        Text("${String.format(Locale.US, "%.1f", maxVolt)}V", style = TelemetryMonoStyle, color = voltColor)
        Text("${String.format(Locale.US, "%.1f", (maxVolt + minVolt) / 2f)}V", style = TelemetryMonoStyle, color = voltColor.copy(alpha = 0.75f))
        Text("${String.format(Locale.US, "%.1f", minVolt)}V", style = TelemetryMonoStyle, color = voltColor)
      }
    }

    // X-axis timestamp range & Peak metrics footer
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = timeFormat.format(Date(ordered.first().timestampMillis)),
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Peak ${String.format(Locale.US, "%.1f", temps.max())} °C • ${
          String.format(Locale.US, "%.2f", volts.min())
        }–${String.format(Locale.US, "%.2f", volts.max())} V",
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("hourly_chart_summary_metrics"),
      )
      Text(
        text = timeFormat.format(Date(ordered.last().timestampMillis)),
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
