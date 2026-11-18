package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.system.StorageInspector
import com.example.domain.model.StorageActionTarget
import com.example.ui.NetraUiState
import com.example.ui.components.GuardianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.StorageAnalysisDashboard
import com.example.ui.components.WhyExplainChip
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle

@Composable
fun StorageScreen(
  state: NetraUiState,
  strings: AppStrings,
  onCleanOwnCache: () -> Unit,
  onOpenStorageTarget: (StorageActionTarget, String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val st = state.storageInsight
  val usedFrac = if (st.totalBytes > 0L) (st.usedBytes.toFloat() / st.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
  val statusColor =
    when {
      st.freePercent < 5f -> SemanticCriticalRed
      st.freePercent < 10f -> SemanticWarmAmber
      else -> SemanticCoolMint
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("storage_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Text(
          text = if (strings.isHindi) "सुरक्षित स्टोरेज क्लीनअप सहायक" else "Safe Storage Cleanup Assistant",
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }

    // Storage Analysis Dashboard (Occupied vs Free Space + Top Storage-Consuming App Categories)
    item {
      StorageAnalysisDashboard(
        storageInsight = st,
        diskUsageReport = state.diskUsageReport,
        isHindi = strings.isHindi,
        onCategoryAction = { target, pkg ->
          if (target == StorageActionTarget.APP_OWN_CACHE) onCleanOwnCache()
          else onOpenStorageTarget(target, pkg)
        },
        modifier = Modifier.widthIn(max = 720.dp),
      )
    }

    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp), accentColor = statusColor) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "डिवाइस स्टोरेज" else "Internal Storage",
            style = MaterialTheme.typography.titleMedium,
          )
          StatusPill(
            text = "${String.format("%.1f", st.freePercent)}% Free",
            color = statusColor,
          )
        }
        LinearProgressIndicator(
          progress = { usedFrac },
          modifier = Modifier.fillMaxWidth().height(10.dp),
          color = statusColor,
        )
        Text(
          text = "Used: ${StorageInspector.formatBytes(st.usedBytes)} / ${StorageInspector.formatBytes(st.totalBytes)} • Free: ${StorageInspector.formatBytes(st.freeBytes)}",
          style = TelemetryMonoStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val postpone = if (strings.isHindi) st.heavyScanPostponedReasonHi else st.heavyScanPostponedReasonEn
        if (postpone != null) {
          StatusPill(text = postpone, color = SemanticWarmAmber)
        }
      }
    }

    // Idle-Window StorageStatsManager App-Specific Disk Usage Card
    item {
      val report = state.diskUsageReport
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "ऐप-विशिष्ट डिस्क उपयोग (StorageStatsManager)" else "App-Specific Disk Usage (Idle Only)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          StatusPill(
            text = if (report?.executedDuringIdleWindow == true) "IDLE SCANNED" else "IDLE WINDOW",
            color = SemanticCoolMint,
          )
        }
        if (report == null) {
          Text(
            text = if (strings.isHindi) {
              "DiskUsageHelper बैटरी बचाने के लिए केवल आइडल मेंटेनेंस विंडो में भारी फ़ाइलों को स्कैन करता है।"
            } else {
              "DiskUsageHelper scans StorageStatsManager and app directories only during OS idle maintenance windows to save battery."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = "App: ${StorageInspector.formatBytes(report.selfAppBytes)} • Data: ${
              StorageInspector.formatBytes(report.selfDataBytes)
            } • Cache: ${StorageInspector.formatBytes(report.selfCacheBytes)}",
            style = TelemetryMonoStyle,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (report.heavyAppFiles.isNotEmpty()) {
            Text(
              text = if (strings.isHindi) "पहचानी गई भारी फ़ाइलें:" else "Detected Heavy App Files:",
              style = MaterialTheme.typography.labelLarge,
            )
            report.heavyAppFiles.take(4).forEach { file ->
              Text(
                text = "• ${file.name} (${StorageInspector.formatBytes(file.sizeBytes)}) [${file.directoryKind}]",
                style = MaterialTheme.typography.bodySmall,
                color = SemanticWarmAmber,
              )
            }
          }
          if (report.heavyApps.isNotEmpty()) {
            Text(
              text = if (strings.isHindi) "भारी स्टोरेज वाले ऐप्स:" else "Heavy Storage Apps:",
              style = MaterialTheme.typography.labelLarge,
            )
            report.heavyApps.take(4).forEach { app ->
              Text(
                text = "• ${app.appName}: Data ${StorageInspector.formatBytes(app.dataBytes)} • Cache ${StorageInspector.formatBytes(app.cacheBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    items(st.recommendations, key = { it.id }) { rec ->
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) rec.titleHi else rec.titleEn,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          if (rec.estimatedReclaimableMb > 0) {
            StatusPill(
              text = "~${rec.estimatedReclaimableMb} MB est.",
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        Text(
          text = if (strings.isHindi) rec.descriptionHi else rec.descriptionEn,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = {
              if (rec.target == StorageActionTarget.APP_OWN_CACHE) onCleanOwnCache()
              else onOpenStorageTarget(rec.target, rec.packageHint)
            },
            modifier = Modifier.testTag("clean_action_${rec.id}"),
          ) {
            Text(
              if (rec.target == StorageActionTarget.APP_OWN_CACHE) {
                if (strings.isHindi) "अपना कैश साफ़ करें" else "Clean App Cache"
              } else {
                if (strings.isHindi) "अभी साफ़ करें" else "Clean Now"
              }
            )
          }
          WhyExplainChip(
            label = strings.whyButton,
            title = if (strings.isHindi) rec.titleHi else rec.titleEn,
            explanation = if (strings.isHindi) rec.whyHi else rec.whyEn,
          )
        }
      }
    }
  }
}
