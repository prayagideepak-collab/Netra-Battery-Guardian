package com.example.ui.screens

import com.example.domain.model.capacityText
import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.OptimizationPriority
import com.example.domain.engine.OptimizerActionTarget
import com.example.domain.engine.PowerGovernanceRegistry
import com.example.domain.model.AdaptivePowerTier
import com.example.ui.NetraUiState
import com.example.ui.components.BatteryMonitoringDashboardView
import com.example.ui.components.BatteryTemperatureTrendChart
import com.example.ui.components.DozeEfficiencyCard
import com.example.ui.components.GuardianCard
import com.example.ui.components.HourlyTempVoltageChart
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryLineChart
import com.example.ui.components.WhyExplainChip
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryScreen(
  state: NetraUiState,
  strings: AppStrings,
  onOpenLiveMonitor: () -> Unit = {},
  onOptimizerAction: (OptimizerActionTarget, String?) -> Unit = { _, _ -> },
  onExportCsv: () -> Unit = {},
  onQueueChargingCsvExport: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var show7Days by remember { mutableStateOf(false) }
  var showGovernanceDialog by remember { mutableStateOf(false) }
  val snapshots = if (show7Days) state.snapshots7d else state.snapshots24h
  val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
  val t = state.telemetry
  val diag = state.chargingDiagnosis

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("battery_screen_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Row(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (strings.isHindi) "बैटरी विश्लेषण और ग्राफ़" else "Battery Telemetry & Graphs",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
          onClick = onOpenLiveMonitor,
          modifier = Modifier.testTag("battery_open_live_monitor_button"),
        ) {
          Text(strings.liveServiceAction)
        }
      }
    }

    // Dedicated Battery Sentinel Pro Monitoring Dashboard View
    item {
      BatteryMonitoringDashboardView(
        telemetry = t,
        chargingDiagnosis = diag,
        historySnapshots = if (state.snapshots24h.isNotEmpty()) state.snapshots24h else state.hourlySnapshots,
        isHindi = strings.isHindi,
        animationsAllowed = state.powerBudget.activeTier != com.example.domain.model.AdaptivePowerTier.SURVIVAL,
      )
    }

    // Net Charging Power Classification Card
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "चार्जिंग गति और पावर बजट" else "Charging Speed & Power",
            style = MaterialTheme.typography.titleMedium,
          )
          WhyExplainChip(
            label = strings.whyButton,
            title = if (strings.isHindi) "चार्जिंग स्पीड वर्ग" else "Net Battery Power Formula",
            explanation = if (strings.isHindi) diag?.whyHi ?: "" else diag?.whyEn ?: "",
          )
        }
        if (diag != null) {
          StatusPill(
            text = "${strings.speedLabel(diag.speedClass)} • ${telemetryText(diag.netPowerWatts, " W")}",
            color = SemanticCoolMint,
          )
          Text(
            text = if (strings.isHindi) diag.explanationHi else diag.explanationEn,
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = listOfNotNull(t.plugType.name.takeIf { t.isPlugged }, telemetryText(diag.rollingAvgCurrentMa, " mA", 0).takeIf { it.isNotBlank() }, capacityText(t.estimatedCapacityMah).takeIf { it.isNotBlank() }).joinToString(" • ") +
              (t.chargeCycleCount?.let { " • Cycles: $it" } ?: ""),
            style = TelemetryMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Power Budget Tier & 24/7 Self-Healing Card
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        val pb = state.powerBudget
        val sh = state.selfHealingStatus
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "अल्ट्रा-लो पावर बजट और 24/7 विश्वसनीयता" else "24/7 Power Budget & Self-Healing",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          StatusPill(
            text = "Tier: ${pb.activeTier.name}",
            color = SemanticCoolMint,
          )
        }
        Text(
          text = "Idle target <= ${pb.activeTier.idleTargetDrainPer24hPercent}%/24h • Wakeups/hr: ${pb.wakeupsThisHour} (Cap <= ${pb.activeTier.maxWakeupsPerHour}) • Net/hr: 0 • DB Batch: ${pb.dbBatchIntervalSeconds}s",
          style = TelemetryMonoStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Inputs: ${batteryLevelText(t.levelPercent)} | Plug:${t.isPlugged} | OS Saver:${t.osBatterySaverOn} | Thermal:${t.thermalLevel.name} | Screen:${t.screenOn} | FG:${t.isAppInForeground} | Bucket:${t.standbyBucket.name} | Net:${t.networkType.name}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pb.suspendedFeatures.isNotEmpty()) {
          Text(
            text = "Throttled in ${pb.activeTier.name}: ${pb.suspendedFeatures.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = SemanticWarmAmber,
          )
        }
        Text(
          text = "Doze Mode: ${if (state.dozeState.isInDozeMode) "IN DOZE (Non-essential paused)" else "ACTIVE"} • Transitions In/Out: ${state.dozeState.enterDozeCount}/${state.dozeState.exitDozeCount}",
          style = TelemetryMonoStyle,
          color = if (state.dozeState.isInDozeMode) SemanticWarmAmber else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("doze_guard_status_pill"),
        )
        Text(
          text = "Self-Healing: ${sh.lastCheckSummary} (Retries ${sh.consecutiveFailures}/${sh.maxRetries}, Circuit ${if (sh.circuitOpen) "OPEN" else "CLOSED"}) • Log: ${sh.rotatedLogBytes / 1024} KB / 256 KB",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = { showGovernanceDialog = true },
          modifier = Modifier.testTag("power_governance_button"),
        ) {
          Text(if (strings.isHindi) "Tier-0 और फ़ीचर गेट देखें" else "View Tier-0 & Feature Gate Spec")
        }
      }
    }

    // Doze Mode Total Time & Energy-Saving Efficiency Card
    item {
      DozeEfficiencyCard(
        metrics = state.dozeEfficiency,
        isHindi = strings.isHindi,
        whyLabel = strings.whyButton,
      )
    }

    // Long-Term Battery Cycle Count & Capacity Degradation (Logged Daily While Charging)
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp).testTag("battery_cycle_health_card")) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "Android बैटरी माप" else "Android Battery Observations",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
        }
        com.example.ui.components.BatteryHealthObservationContent(t, strings.isHindi)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalButton(
            onClick = onExportCsv,
            enabled = t.isCharging,
            modifier = Modifier.testTag("battery_export_csv_button"),
          ) {
            Text(
              if (strings.isHindi) "CSV निर्यात (चार्जिंग पर)"
              else "Export Health & Temp CSV",
            )
          }
          if (!t.isCharging) {
            OutlinedButton(
              onClick = onQueueChargingCsvExport,
              modifier = Modifier.testTag("queue_charging_csv_button"),
            ) {
              Text(if (strings.isHindi) "चार्जिंग पर कतारबद्ध करें" else "Queue on Charge")
            }
          }
        }
      }
    }

    // Battery Health Optimizer (Actionable Low-Power Advice)
    item {
      val opt = state.optimizationReport
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp).testTag("battery_health_optimizer_card")) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "बैटरी स्वास्थ्य ऑप्टिमाइज़र" else "Battery Health Optimizer",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )

        }
        opt.recommendations.forEach { rec ->
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (strings.isHindi) rec.titleHi else rec.titleEn,
                style = MaterialTheme.typography.titleSmall,
                color = if (rec.priority == OptimizationPriority.URGENT || rec.priority == OptimizationPriority.HIGH) {
                  SemanticWarmAmber
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
              )
              WhyExplainChip(
                label = strings.whyButton,
                title = if (strings.isHindi) rec.titleHi else rec.titleEn,
                explanation = if (strings.isHindi) rec.whyHi else rec.whyEn,
              )
            }
            Text(
              text = if (strings.isHindi) rec.adviceHi else rec.adviceEn,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { onOptimizerAction(rec.actionTarget, rec.packageHint) },
              modifier = Modifier.testTag("optimizer_action_${rec.id}"),
            ) {
              Text(if (strings.isHindi) rec.actionLabelHi else rec.actionLabelEn)
            }
          }
        }
      }
    }

    // 24h vs 7d Battery Graph
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "बैटरी इतिहास (पुराना डेटा असत्यापित)" else "Battery History (legacy data unverified)",
            style = MaterialTheme.typography.titleMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !show7Days,
              onClick = { show7Days = false },
              label = { Text("24h") },
              modifier = Modifier.testTag("chart_24h_chip"),
            )
            FilterChip(
              selected = show7Days,
              onClick = { show7Days = true },
              label = { Text("7d") },
              modifier = Modifier.testTag("chart_7d_chip"),
            )
          }
        }
        TelemetryLineChart(
          values = snapshots.map { it.levelPercent.toFloat() },
          minRange = 0f,
          maxRange = 100f,
          lineColor = SemanticCoolMint,
          emptyMessage = if (strings.isHindi) "अभी पर्याप्त स्नैपशॉट उपलब्ध नहीं हैं।" else "Recording snapshots locally for battery timeline.",
        )

        Text(
          text = if (strings.isHindi) "तापमान टाइमलाइन (°C)" else "Temperature Timeline (°C)",
          style = MaterialTheme.typography.titleMedium,
        )
        TelemetryLineChart(
          values = snapshots.map { it.temperatureCelsius }.filter { it.isFinite() },
          minRange = 10f,
          maxRange = 55f,
          lineColor = SemanticWarmAmber,
          emptyMessage = if (strings.isHindi) "तापमान डेटा संकलित हो रहा है।" else "Collecting local thermal timeline.",
        )

        BatteryTemperatureTrendChart(
          snapshots24h = state.snapshots24h,
          snapshots7d = state.snapshots7d,
          isHindi = strings.isHindi,
          isLowPowerSurvival = state.powerBudget.activeTier == AdaptivePowerTier.SURVIVAL,
        )
      }
    }

    // Hourly Battery Temperature & Voltage Canvas History (Room Database)
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(
          text = if (strings.isHindi) "प्रति घंटा तापमान और वोल्टेज इतिहास (Room)" else "Hourly Temperature & Voltage History (Room)",
          style = MaterialTheme.typography.titleMedium,
        )
        HourlyTempVoltageChart(
          snapshots = state.hourlySnapshots,
          isHindi = strings.isHindi,
          isLowPowerSurvival = state.powerBudget.activeTier == AdaptivePowerTier.SURVIVAL,
        )
      }
    }

    // Hourly Battery Snapshots (BatteryDatabase)
    item {
      Text(
        text = if (strings.isHindi) "प्रति घंटा बैटरी स्नैपशॉट" else "Hourly Battery Snapshots",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      )
    }

    if (state.hourlySnapshots.isEmpty()) {
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Text(
            text = if (strings.isHindi) "प्रति घंटा वोल्टेज, तापमान और चार्जिंग स्थिति यहाँ दर्ज होगी।"
            else "Hourly voltage, temperature, and charging status checkpoints will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(state.hourlySnapshots.take(12), key = { "hourly_${it.id}" }) { snap ->
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text = "${dateFmt.format(Date(snap.timestampMillis))} • ${snap.levelPercent}%",
              style = MaterialTheme.typography.titleMedium,
            )
            StatusPill(
              text = snap.chargingStatus,
              color = if (snap.isCharging) SemanticCoolMint else SemanticIceCyan,
            )
          }
          Text(
            text = "Voltage: ${telemetryText(if (snap.voltageMv > 0) snap.voltageMv / 1000f else Float.NaN, " V", 2)} • Temp: ${telemetryText(snap.temperatureCelsius, "°C")} • ${snap.plugType}",
            style = TelemetryMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Charging Sessions
    item {
      Text(
        text = if (strings.isHindi) "हाल के चार्जिंग सत्र" else "Charging Sessions",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      )
    }

    if (state.chargingSessions.isEmpty()) {
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Text(
            text = if (strings.isHindi) "चार्जर जोड़ने पर चार्जिंग सत्र यहाँ दिखेंगे।" else "Connect your charger to log charging speed & thermal sessions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(state.chargingSessions.take(8)) { cs ->
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${batteryLevelText(cs.startLevel)} → ${batteryLevelText(cs.endLevel)} (${cs.plugType})", style = MaterialTheme.typography.titleMedium)
            StatusPill(cs.speedClass, SemanticIceCyan)
          }
          Text(
            text = "${dateFmt.format(Date(cs.startTimeMillis))} • Max ${telemetryText(cs.maxPowerWatts, " W")} • Peak ${telemetryText(cs.peakTempCelsius, "°C")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Discharge Sessions
    item {
      Text(
        text = if (strings.isHindi) "डिस्चार्ज सत्र" else "Discharge Sessions",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
      )
    }

    if (state.dischargeSessions.isEmpty()) {
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Text(
            text = if (strings.isHindi) "बैटरी पर चलने के सत्र यहाँ दर्ज होंगे।" else "Battery discharge sessions will appear here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(state.dischargeSessions.take(8)) { ds ->
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${batteryLevelText(ds.startLevel)} → ${batteryLevelText(ds.endLevel)}", style = MaterialTheme.typography.titleMedium)
            if (ds.abnormalDrainDetected) {
              StatusPill("Abnormal Drain", SemanticWarmAmber)
            }
          }
          Text(
            text = "${dateFmt.format(Date(ds.startTimeMillis))} • Screen On ${ds.screenOnMinutes}m / Off ${ds.screenOffMinutes}m • Temp ${telemetryText(ds.minTempCelsius, "°C")} to ${telemetryText(ds.maxTempCelsius, "°C")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (showGovernanceDialog) {
    AlertDialog(
      onDismissRequest = { showGovernanceDialog = false },
      title = { Text("Tier-0 & Feature Power Gate") },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            Text("Essential 24/7 Tier-0 Functions", style = MaterialTheme.typography.titleMedium)
          }
          items(PowerGovernanceRegistry.tierZeroFunctions) { t0 ->
            Text("• ${t0.name}: ${t0.description}", style = MaterialTheme.typography.bodyMedium)
          }
          item {
            Text("Feature Power Gates", style = MaterialTheme.typography.titleMedium)
          }
          items(PowerGovernanceRegistry.featureGates) { gate ->
            Text(
              text = "• ${gate.featureName} [${gate.runsInTier}] — Wakeups/hr: ${gate.maxWakeupsPerHour}, Net/hr: ${gate.networkCallsPerHour}, Impact: ${gate.measuredImpact}",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showGovernanceDialog = false }) { Text("Close") }
      },
    )
  }
}
