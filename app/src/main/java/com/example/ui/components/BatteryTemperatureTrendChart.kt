package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.db.BatterySnapshot
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Jetpack Compose Canvas chart visualizing battery temperature trends over time
 * from Room-persisted [BatterySnapshot] rows (replacing web/JS charting libraries like Recharts
 * so Netra stays 100% native Kotlin, offline, and ultra-low power).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryTemperatureTrendChart(
  snapshots24h: List<BatterySnapshot>,
  snapshots7d: List<BatterySnapshot>,
  isHindi: Boolean = false,
  isLowPowerSurvival: Boolean = false,
  modifier: Modifier = Modifier,
) {
  var show7Days by remember { mutableStateOf(false) }
  val activeSnapshots = remember(show7Days, snapshots24h, snapshots7d) {
    val raw = if (show7Days) snapshots7d else snapshots24h
    raw.sortedBy { it.timestampMillis }
  }
  val timeFormat = remember(show7Days) {
    SimpleDateFormat(if (show7Days) "dd MMM" else "HH:mm", Locale.getDefault())
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("battery_temp_trend_chart"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (isHindi) "बैटरी तापमान ट्रेंड (Room)" else "Battery Temperature Trends (Room)",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = if (isLowPowerSurvival) {
            if (isHindi) "सर्वाइवल मोड: स्थिर दृश्य" else "Survival mode: static Room view"
          } else {
            if (isHindi) "स्थानीय Room इतिहास से समय-आधारित ग्राफ़" else "Time-series from offline Room snapshots"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !show7Days,
          onClick = { show7Days = false },
          label = { Text("24h") },
          modifier = Modifier.testTag("temp_trend_24h_chip"),
        )
        FilterChip(
          selected = show7Days,
          onClick = { show7Days = true },
          label = { Text("7d") },
          modifier = Modifier.testTag("temp_trend_7d_chip"),
        )
      }
    }

    if (activeSnapshots.size < 2) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(148.dp)
          .testTag("temp_trend_empty_state"),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Thermostat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
          )
          Text(
            text = if (isHindi) {
              "तापमान ट्रेंड के लिए कम से कम 2 Room स्नैपशॉट आवश्यक हैं (${activeSnapshots.size}/2)।"
            } else {
              "Collecting Room snapshots for temperature trend (${activeSnapshots.size}/2)."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      return
    }

    val temps = remember(activeSnapshots) { activeSnapshots.map { it.temperatureCelsius } }
    val firstTemp = temps.first()
    val latestTemp = temps.last()
    val deltaTemp = latestTemp - firstTemp
    val minVal = temps.min()
    val maxVal = temps.max()
    val avgVal = temps.average().toFloat()

    val minAxis = remember(minVal) { minOf(18f, minVal - 3f) }
    val maxAxis = remember(maxVal) { maxOf(48f, maxVal + 3f) }
    val lineColor = when {
      maxVal >= 45f -> SemanticCriticalRed
      maxVal >= 40f -> SemanticHotOrange
      maxVal >= 36f -> SemanticWarmAmber
      else -> SemanticCoolMint
    }
    val warn40Color = SemanticCriticalRed.copy(alpha = 0.55f)
    val warm36Color = SemanticWarmAmber.copy(alpha = 0.4f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    // Trend metrics pills
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val deltaStr = String.format(Locale.US, "%+.1f°C", deltaTemp)
      val deltaColor = when {
        deltaTemp >= 1.5f -> SemanticHotOrange
        deltaTemp <= -1.0f -> SemanticCoolMint
        else -> SemanticIceCyan
      }
      StatusPill(
        text = "Peak ${String.format(Locale.US, "%.1f", maxVal)}°C",
        color = lineColor,
        modifier = Modifier.testTag("temp_trend_peak_pill"),
      )
      StatusPill(
        text = "Avg ${String.format(Locale.US, "%.1f", avgVal)}°C",
        color = SemanticIceCyan,
      )
      StatusPill(
        text = "Trend $deltaStr",
        color = deltaColor,
        modifier = Modifier.testTag("temp_trend_delta_pill"),
      )
    }

    val a11ySummary =
      "Battery temperature trend across ${activeSnapshots.size} Room points: " +
        "min ${String.format(Locale.US, "%.1f", minVal)}°C, " +
        "max ${String.format(Locale.US, "%.1f", maxVal)}°C, " +
        "latest ${String.format(Locale.US, "%.1f", latestTemp)}°C."

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.height(160.dp).padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text("${maxAxis.toInt()}°C", style = TelemetryMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${((maxAxis + minAxis) / 2f).toInt()}°C", style = TelemetryMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${minAxis.toInt()}°C", style = TelemetryMonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Canvas(
        modifier = Modifier
          .weight(1f)
          .height(160.dp)
          .padding(vertical = 8.dp)
          .testTag("battery_temp_trend_canvas")
          .semantics { contentDescription = a11ySummary },
      ) {
        val span = (maxAxis - minAxis).coerceAtLeast(1f)
        val firstTs = activeSnapshots.first().timestampMillis
        val lastTs = activeSnapshots.last().timestampMillis
        val timeSpan = (lastTs - firstTs).coerceAtLeast(1L)

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

        // Reference thermal threshold lines: 36°C (Warm) and 40°C (Hot)
        if (36f in minAxis..maxAxis) {
          val y36 = size.height - (((36f - minAxis) / span).coerceIn(0f, 1f) * size.height)
          drawLine(
            color = warm36Color,
            start = Offset(0f, y36),
            end = Offset(size.width, y36),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
          )
        }
        if (40f in minAxis..maxAxis) {
          val y40 = size.height - (((40f - minAxis) / span).coerceIn(0f, 1f) * size.height)
          drawLine(
            color = warn40Color,
            start = Offset(0f, y40),
            end = Offset(size.width, y40),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
          )
        }

        val linePath = Path()
        val fillPath = Path()
        val points = ArrayList<Pair<Offset, BatterySnapshot>>(activeSnapshots.size)

        activeSnapshots.forEachIndexed { idx, snap ->
          val x = if (timeSpan <= 1L) {
            (idx.toFloat() / (activeSnapshots.size - 1).coerceAtLeast(1)) * size.width
          } else {
            ((snap.timestampMillis - firstTs).toFloat() / timeSpan.toFloat()) * size.width
          }
          val norm = ((snap.temperatureCelsius - minAxis) / span).coerceIn(0f, 1f)
          val y = size.height - (norm * size.height)
          val pt = Offset(x, y)
          points += pt to snap

          if (idx == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, size.height)
            fillPath.lineTo(x, y)
          } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
          }
          if (idx == activeSnapshots.lastIndex) {
            fillPath.lineTo(x, size.height)
            fillPath.close()
          }
        }

        drawPath(
          path = fillPath,
          brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
          ),
        )
        drawPath(
          path = linePath,
          color = lineColor,
          style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
        )

        val dotRadius = if (points.size <= 32) 3.5.dp.toPx() else 2.2.dp.toPx()
        points.forEach { (pt, snap) ->
          val ptColor = when {
            snap.temperatureCelsius >= 43f -> SemanticCriticalRed
            snap.temperatureCelsius >= 40f -> SemanticHotOrange
            snap.temperatureCelsius >= 36f -> SemanticWarmAmber
            snap.isCharging -> SemanticIceCyan
            else -> SemanticCoolMint
          }
          drawCircle(color = ptColor, radius = dotRadius, center = pt)
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = timeFormat.format(Date(activeSnapshots.first().timestampMillis)),
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "${activeSnapshots.size} Room pts • Range ${String.format(Locale.US, "%.1f", minVal)}–${
          String.format(Locale.US, "%.1f", maxVal)
        }°C",
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("temp_trend_range_footer"),
      )
      Text(
        text = timeFormat.format(Date(activeSnapshots.last().timestampMillis)),
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
