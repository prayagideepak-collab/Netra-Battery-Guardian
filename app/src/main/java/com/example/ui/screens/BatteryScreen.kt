package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.NetraUiState
import com.example.ui.components.GuardianCard
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
  modifier: Modifier = Modifier,
) {
  var show7Days by remember { mutableStateOf(false) }
  val snapshots = if (show7Days) state.snapshots7d else state.snapshots24h
  val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
  val t = state.telemetry
  val diag = state.chargingDiagnosis

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Text(
          text = if (strings.isHindi) "बैटरी विश्लेषण और ग्राफ़" else "Battery Telemetry & Graphs",
          style = MaterialTheme.typography.headlineMedium,
        )
      }
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
            text = "${strings.speedLabel(diag.speedClass)} • ${String.format("%.1f", diag.netPowerWatts)} W",
            color = SemanticCoolMint,
          )
          Text(
            text = if (strings.isHindi) diag.explanationHi else diag.explanationEn,
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = "Plug: ${t.plugType.name} • Rolling Current: ${diag.rollingAvgCurrentMa.toInt()} mA • Est. Capacity: ${t.estimatedCapacityMah} mAh" +
              (t.chargeCycleCount?.let { " • Cycles: $it" } ?: ""),
            style = TelemetryMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Power Budget Tier Card
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        val pb = state.powerBudget
        Text(
          text = if (strings.isHindi) "अल्ट्रा-लो पावर बजट" else "Ultra-Low Power Budget",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Tier: ${pb.activeTier.name} | Wakeups/hr: ${pb.wakeupsThisHour} (Budget <= ${pb.activeTier.maxWakeupsPerHour}) | Network Calls/hr: ${pb.networkCallsThisHour} | Snapshots today: ${pb.snapshotsRecordedToday}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            text = if (strings.isHindi) "बैटरी स्तर इतिहास" else "Battery Level History",
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
          values = snapshots.map { it.temperatureCelsius },
          minRange = 10f,
          maxRange = 55f,
          lineColor = SemanticWarmAmber,
          emptyMessage = if (strings.isHindi) "तापमान डेटा संकलित हो रहा है।" else "Collecting local thermal timeline.",
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
            text = "Voltage: ${String.format("%.2f", snap.voltageMv / 1000f)} V • Temp: ${String.format("%.1f", snap.temperatureCelsius)}°C • ${snap.plugType}",
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
            Text("${cs.startLevel}% → ${cs.endLevel}% (${cs.plugType})", style = MaterialTheme.typography.titleMedium)
            StatusPill(cs.speedClass, SemanticIceCyan)
          }
          Text(
            text = "${dateFmt.format(Date(cs.startTimeMillis))} • Max ${String.format("%.1f", cs.maxPowerWatts)} W • Peak ${String.format("%.1f", cs.peakTempCelsius)}°C",
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
            Text("${ds.startLevel}% → ${ds.endLevel}%", style = MaterialTheme.typography.titleMedium)
            if (ds.abnormalDrainDetected) {
              StatusPill("Abnormal Drain", SemanticWarmAmber)
            }
          }
          Text(
            text = "${dateFmt.format(Date(ds.startTimeMillis))} • Screen On ${ds.screenOnMinutes}m / Off ${ds.screenOffMinutes}m • Temp ${String.format("%.1f", ds.minTempCelsius)}–${String.format("%.1f", ds.maxTempCelsius)}°C",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
