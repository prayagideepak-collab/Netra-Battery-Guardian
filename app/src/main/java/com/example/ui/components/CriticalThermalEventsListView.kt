package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.db.ActivityLogEntity
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * List view displaying the historical log of critical thermal / overheating events retrieved
 * from the offline Room database ([ActivityLogEntity] rows with category `HEAT`), allowing users
 * to inspect exactly when and why their device overheated.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CriticalThermalEventsListView(
  criticalThermalEvents: List<ActivityLogEntity>,
  fallbackActivityLogs: List<ActivityLogEntity> = emptyList(),
  isHindi: Boolean = false,
  whyLabel: String = "Why?",
  modifier: Modifier = Modifier,
) {
  var criticalOnly by remember { mutableStateOf(false) }
  val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }

  val allThermalEvents = remember(criticalThermalEvents, fallbackActivityLogs) {
    val source = if (criticalThermalEvents.isNotEmpty()) {
      criticalThermalEvents
    } else {
      fallbackActivityLogs.filter {
        it.category == LogCategory.HEAT.name &&
          (it.severity == LogSeverity.CRITICAL.name || it.severity == LogSeverity.WARNING.name)
      }
    }
    source.sortedByDescending { it.timestampMillis }
  }

  val displayedEvents = remember(allThermalEvents, criticalOnly) {
    if (criticalOnly) {
      allThermalEvents.filter { it.severity == LogSeverity.CRITICAL.name }
    } else {
      allThermalEvents
    }
  }

  val criticalCount = remember(allThermalEvents) {
    allThermalEvents.count { it.severity == LogSeverity.CRITICAL.name }
  }

  GuardianCard(
    modifier = modifier
      .fillMaxWidth()
      .testTag("critical_thermal_events_card"),
    accentColor = if (criticalCount > 0) SemanticCriticalRed else SemanticCoolMint,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (isHindi) "गंभीर थर्मल घटनाओं का इतिहास (Room)" else "Critical Thermal Events Log (Room)",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = if (isHindi) {
            "डिवाइस कब और क्यों गर्म हुआ इसका स्थानीय Room डेटाबेस इतिहास"
          } else {
            "Historical overheating records retrieved from offline Room database"
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusPill(
        text = "${displayedEvents.size} Events",
        color = if (criticalCount > 0) SemanticCriticalRed else SemanticCoolMint,
        modifier = Modifier.testTag("critical_thermal_events_count_pill"),
      )
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterChip(
        selected = !criticalOnly,
        onClick = { criticalOnly = false },
        label = {
          Text(
            if (isHindi) "सभी हीट अलर्ट (${allThermalEvents.size})"
            else "All Overheat (${allThermalEvents.size})"
          )
        },
        modifier = Modifier.testTag("thermal_filter_all_chip"),
      )
      FilterChip(
        selected = criticalOnly,
        onClick = { criticalOnly = true },
        label = {
          Text(
            if (isHindi) "केवल गंभीर ($criticalCount)"
            else "Critical Only ($criticalCount)"
          )
        },
        modifier = Modifier.testTag("thermal_filter_critical_only_chip"),
      )
    }

    if (displayedEvents.isEmpty()) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("critical_thermal_events_empty"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = SemanticCoolMint,
            modifier = Modifier.size(26.dp),
          )
          Column {
            Text(
              text = if (isHindi) {
                "कोई गंभीर ओवरहीटिंग घटना दर्ज नहीं हुई"
              } else {
                "No Critical Overheating Events Recorded"
              },
              style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
              text = if (isHindi) {
                "जब भी बैटरी खतरनाक तापमान (>=45°C या OVERHEAT) पर पहुँचती है, तो यह Room डेटाबेस में यहाँ लॉग हो जाएगी।"
              } else {
                "Whenever the battery reaches critical heat thresholds (>=45°C or OS OVERHEAT), the event is persisted in Room and listed here."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("critical_thermal_events_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        displayedEvents.take(25).forEachIndexed { idx, event ->
          val isCritical = event.severity == LogSeverity.CRITICAL.name
          val badgeColor = if (isCritical) SemanticCriticalRed else SemanticHotOrange
          val itemKey = if (event.id != 0L) event.id else idx.toLong()

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("critical_thermal_event_item_$itemKey"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (idx > 0) {
              HorizontalDivider(
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Whatshot,
                  contentDescription = null,
                  tint = badgeColor,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = if (isHindi && event.titleHi.isNotBlank()) event.titleHi else event.titleEn,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              StatusPill(
                text = event.severity,
                color = badgeColor,
              )
            }

            Text(
              text = dateFmt.format(Date(event.timestampMillis)),
              style = TelemetryMonoStyle,
              color = SemanticWarmAmber,
            )

            Text(
              text = if (isHindi && event.detailHi.isNotBlank()) event.detailHi else event.detailEn,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )

            val whyText = if (isHindi && event.whyHi.isNotBlank()) event.whyHi else event.whyEn
            if (whyText.isNotBlank()) {
              Text(
                text = "$whyLabel: $whyText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
