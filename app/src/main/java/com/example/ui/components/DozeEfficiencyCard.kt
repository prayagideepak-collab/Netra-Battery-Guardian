package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.DozeEfficiencyCalculator
import com.example.domain.engine.DozeEfficiencyMetrics
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale

@Composable
fun DozeEfficiencyCard(
  metrics: DozeEfficiencyMetrics,
  isHindi: Boolean,
  whyLabel: String,
  modifier: Modifier = Modifier,
) {
  val totalFormatted = DozeEfficiencyCalculator.formatDuration(metrics.totalDozeTimeMillis)
  val savedPercentFormatted = String.format(Locale.US, "%.1f%%", metrics.estimatedBatterySavedPercent)
  val pillColor = if (metrics.isCurrentlyInDoze) SemanticWarmAmber else SemanticCoolMint
  val statusText =
    if (metrics.isCurrentlyInDoze) {
      if (isHindi) "डोज़ सक्रिय" else "In Doze"
    } else {
      if (isHindi) metrics.efficiencyRatingLabelHi else metrics.efficiencyRatingLabelEn
    }

  Card(
    modifier = modifier
      .widthIn(max = 720.dp)
      .fillMaxWidth()
      .testTag("doze_efficiency_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface,
    ),
    border = BorderStroke(
      width = 1.dp,
      color = pillColor.copy(alpha = 0.42f),
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = pillColor.copy(alpha = 0.16f),
          ) {
            Icon(
              imageVector = Icons.Rounded.Bedtime,
              contentDescription = if (isHindi) "डोज़ दक्षता" else "Doze Efficiency",
              tint = pillColor,
              modifier = Modifier.padding(6.dp).size(20.dp),
            )
          }
          Column {
            Text(
              text = if (isHindi) "डोज़ मोड ऊर्जा बचत" else "Doze Mode Energy Efficiency",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (isHindi) "लॉग किए गए संक्रमणों पर आधारित" else "Calculated from logged idle transitions",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(text = statusText, color = pillColor)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DozeMetricTile(
          label = if (isHindi) "कुल डोज़ समय" else "Total Doze Time",
          value = totalFormatted,
          valueTag = "doze_total_time_value",
          modifier = Modifier.weight(1f),
        )
        DozeMetricTile(
          label = if (isHindi) "अनुमानित बचत" else "Est. Energy Saved",
          value = "$savedPercentFormatted (~${metrics.estimatedMahSaved} mAh)",
          valueTag = "doze_energy_saved_value",
          modifier = Modifier.weight(1f),
        )
        DozeMetricTile(
          label = if (isHindi) "लॉग सत्र" else "Logged Cycles",
          value = "${metrics.completedSessionsCount} (${metrics.transitionsLoggedCount} ev)",
          valueTag = "doze_transitions_value",
          modifier = Modifier.weight(1f),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Bolt,
              contentDescription = null,
              tint = SemanticIceCyan,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = if (isHindi) {
                "निष्क्रिय दक्षता स्कोर: ${metrics.efficiencyScorePercent}%"
              } else {
                "Idle Saving Efficiency: ${metrics.efficiencyScorePercent}%"
              },
              style = MaterialTheme.typography.labelLarge,
            )
          }
          WhyExplainChip(
            label = whyLabel,
            title = if (isHindi) "डोज़ दक्षता गणना" else "Doze Efficiency Metrics",
            explanation = if (isHindi) {
              "डोज़ प्रवेश और निकास ब्रॉडकास्ट से कुल समय जोड़ा जाता है और गैर-जरूरी स्कैन रोकने से होने वाली अनुमानित बैटरी बचत दिखाई जाती है।"
            } else {
              "Paired Entered/Exited Doze Mode logs measure exact idle time. Pausing background jobs and scans during Doze saves ~1.45% battery per hour versus unthrottled standby."
            },
          )
        }
        LinearProgressIndicator(
          progress = { (metrics.efficiencyScorePercent / 100f).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(8.dp),
          color = SemanticCoolMint,
          trackColor = SemanticIceCyan.copy(alpha = 0.25f),
        )
      }
    }
  }
}

@Composable
private fun DozeMetricTile(
  label: String,
  value: String,
  valueTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(valueTag),
      )
    }
  }
}
