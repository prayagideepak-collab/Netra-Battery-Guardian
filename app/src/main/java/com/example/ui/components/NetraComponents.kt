package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.TemperatureZone
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber

@Composable
fun GuardianCard(
  modifier: Modifier = Modifier,
  accentColor: Color? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(
      width = 1.dp,
      color = accentColor?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
fun AnimatedBatteryRing(
  levelPercent: Int,
  isCharging: Boolean,
  temperatureCelsius: Float,
  animateRing: Boolean,
  statusLabel: String,
  modifier: Modifier = Modifier,
) {
  val targetSweep = (levelPercent.coerceIn(0, 100) / 100f) * 360f
  val sweepAngle = if (animateRing && com.example.domain.engine.MeasuredPowerBudget.permits(com.example.domain.model.PowerFeature.ANIMATION)) {
    val animatedSweep by animateFloatAsState(
      targetValue = targetSweep,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "battery_sweep",
    )
    animatedSweep
  } else targetSweep

  val ringColor =
    when {
      levelPercent !in 0..100 -> Color.Gray
      levelPercent <= 15 -> SemanticCriticalRed
      isCharging -> SemanticCoolMint
      levelPercent <= 30 -> SemanticWarmAmber
      else -> SemanticCoolMint
    }

  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Box(
    modifier = modifier
      .size(176.dp)
      .semantics { contentDescription = if (levelPercent in 0..100) "Battery $levelPercent percent, $statusLabel" else "Battery level unavailable" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
      val strokePx = 14.dp.toPx()
      val arcSize = Size(size.width - strokePx, size.height - strokePx)
      val topLeft = Offset(strokePx / 2f, strokePx / 2f)

      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )

      drawArc(
        brush = Brush.sweepGradient(
          colors = listOf(ringColor.copy(alpha = 0.7f), ringColor, ringColor),
        ),
        startAngle = -90f,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (isCharging) {
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = "Charging",
          tint = ringColor,
          modifier = Modifier.size(22.dp),
        )
      }
      Text(
        text = if (levelPercent in 0..100) "$levelPercent%" else "",
        style = if (levelPercent in 0..100) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge,
        maxLines = 1,
        softWrap = false,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelLarge,
        color = ringColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun WhyExplainChip(
  label: String,
  title: String,
  explanation: String,
  modifier: Modifier = Modifier,
) {
  var showDialog by remember { mutableStateOf(false) }

  Surface(
    onClick = { showDialog = true },
    modifier = modifier.minimumInteractiveComponentSize(),
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.HelpOutline,
        contentDescription = label,
        modifier = Modifier.size(15.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      icon = {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      },
      title = { Text(title, style = MaterialTheme.typography.titleLarge) },
      text = { Text(explanation, style = MaterialTheme.typography.bodyLarge) },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text("OK")
        }
      },
    )
  }
}

@Composable
fun TelemetryLineChart(
  values: List<Float>,
  minRange: Float,
  maxRange: Float,
  lineColor: Color,
  emptyMessage: String,
  modifier: Modifier = Modifier,
) {
  if (values.size < 2) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .height(120.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = emptyMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(130.dp)
      .padding(vertical = 8.dp),
  ) {
    val span = (maxRange - minRange).coerceAtLeast(1f)
    val stepX = size.width / (values.size - 1).coerceAtLeast(1)

    // Draw subtle 3 horizontal gridlines
    listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
      val y = size.height * frac
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
      )
    }

    val linePath = Path()
    val fillPath = Path()

    values.forEachIndexed { idx, v ->
      val normalized = ((v - minRange) / span).coerceIn(0f, 1f)
      val x = idx * stepX
      val y = size.height - (normalized * size.height)
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
      if (idx == values.lastIndex) {
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
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
    )

    val lastVal = values.last()
    val lastNorm = ((lastVal - minRange) / span).coerceIn(0f, 1f)
    drawCircle(
      color = lineColor,
      radius = 4.dp.toPx(),
      center = Offset(size.width, size.height - lastNorm * size.height),
    )
  }
}

@Composable
fun StatusPill(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  if (text.isBlank() || text.contains("Unavailable", ignoreCase = true) || text.contains("अनुपलब्ध")) return
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = color,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

fun zoneColor(zone: TemperatureZone): Color =
  when (zone) {
    TemperatureZone.UNAVAILABLE -> Color.Gray
    TemperatureZone.TOO_COLD, TemperatureZone.COLD -> SemanticIceCyan
    TemperatureZone.NORMAL -> SemanticCoolMint
    TemperatureZone.WARM -> SemanticWarmAmber
    TemperatureZone.HOT -> SemanticHotOrange
    TemperatureZone.VERY_HOT, TemperatureZone.CRITICAL, TemperatureZone.EMERGENCY -> SemanticCriticalRed
  }
