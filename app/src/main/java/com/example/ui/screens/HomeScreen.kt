package com.example.ui.screens

import com.example.domain.model.telemetryText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.system.StorageInspector
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.GuardianProfile
import com.example.domain.model.LocationSaverMode
import com.example.ui.NetraUiState
import com.example.ui.components.AnimatedBatteryRing
import com.example.ui.components.DozeEfficiencyCard
import com.example.ui.components.GuardianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryLineChart
import com.example.ui.components.WhyExplainChip
import com.example.ui.components.zoneColor
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  state: NetraUiState,
  strings: AppStrings,
  onRefresh: () -> Unit,
  onToggleBatterySaver: () -> Unit,
  onToggleCooling: () -> Unit,
  onToggleLocationSaver: () -> Unit,
  onToggleNightSilent: () -> Unit,
  onOpenStorageTab: () -> Unit,
  onOpenBatteryTab: () -> Unit,
  onOpenHeatTab: () -> Unit,
  onOpenAppsTab: () -> Unit,
  onOpenLiveMonitor: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val t = state.telemetry
  val diag = state.chargingDiagnosis
  val zone = state.heatReport?.zone
  val animateRing = state.powerBudget.activeTier != AdaptivePowerTier.SURVIVAL

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.appTitle, style = MaterialTheme.typography.headlineMedium)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = strings.offlineBadge,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          IconButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("refresh_telemetry_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh telemetry")
          }
        }
      }
    }

    // Hero Ring + Live Telemetry Card
    item {
      GuardianCard(
        modifier = Modifier.widthIn(max = 720.dp),
        accentColor = zone?.let { zoneColor(it) },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(
            text = "${strings.activeProfile}: ${strings.profileLabel(state.activeProfile)}",
            color = MaterialTheme.colorScheme.primary,
          )
          StatusPill(
            text = "Tier: ${state.powerBudget.activeTier.name}",
            color = SemanticIceCyan,
          )
        }

        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AnimatedBatteryRing(
            levelPercent = t.levelPercent,
            isCharging = t.isCharging,
            temperatureCelsius = t.temperatureCelsius,
            animateRing = animateRing,
            statusLabel = if (!t.hasLevel) "" else if (t.isCharging) strings.charging else strings.discharging,
          )

          if (diag != null) {
            Spacer(modifier = Modifier.height(6.dp))
            if (diag.speedClass != com.example.domain.model.ChargingSpeedClass.UNAVAILABLE) StatusPill(
              text = strings.speedLabel(diag.speedClass),
              color = if (t.isPlugged) SemanticCoolMint else MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val timeText =
              diag.estimatedMinutesRemaining?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                val duration = if (h > 0) "${h}h ${m}m" else "${m}m"
                val label = if (t.isPlugged) strings.timeToFull else strings.estBackup
                val approx = if (diag.isTimeEstimateApproximate) " ${strings.approximateLabel}" else ""
                "$label: $duration$approx"
              } ?: ""
            Text(
              text = timeText,
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (strings.isHindi) diag.explanationHi else diag.explanationEn,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // 4 Telemetry tiles
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TelemetryMiniStat(
            label = strings.temperature,
            value = "${telemetryText(t.temperatureCelsius, "°C", 1)}",
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniStat(
            label = strings.voltage,
            value = telemetryText(t.voltageVolts, " V", 2),
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniStat(
            label = strings.current,
            value = "${telemetryText(t.currentMa, " mA", 0)}",
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniStat(
            label = if (strings.isHindi) "ऊर्जा दर (अनुमान)" else "Stored rate (est.)",
            value = "${telemetryText(diag?.estimatedStoredEnergyRateWatts ?: Float.NaN, " W")}",
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${strings.health}: ${t.healthState.name} • ${strings.thermalStatus}: ${strings.thermalLabel(t.thermalLevel)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          WhyExplainChip(
            label = strings.whyButton,
            title = if (strings.isHindi) "नेट बैटरी पावर कैसे निकलती है" else "How Net Power Works",
            explanation = if (strings.isHindi) diag?.whyHi ?: "" else diag?.whyEn ?: "",
          )
        }
      }
    }

    // Quick Actions
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(strings.quickActions, style = MaterialTheme.typography.titleMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = state.activeProfile == GuardianProfile.BATTERY_SAVER ||
              state.activeProfile == GuardianProfile.ULTRA_BATTERY_SAVER,
            onClick = onToggleBatterySaver,
            label = { Text(strings.quickBatterySaver) },
            leadingIcon = { Icon(Icons.Rounded.BatterySaver, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("quick_battery_saver"),
          )
          FilterChip(
            selected = state.activeProfile == GuardianProfile.COOLING,
            onClick = onToggleCooling,
            label = { Text(strings.quickCooling) },
            leadingIcon = { Icon(Icons.Rounded.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("quick_cooling_mode"),
          )
          FilterChip(
            selected = state.prefs.locationSaverMode != LocationSaverMode.NORMAL,
            onClick = onToggleLocationSaver,
            label = { Text(strings.quickLocationSaver) },
            leadingIcon = { Icon(Icons.Rounded.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("quick_location_saver"),
          )
          FilterChip(
            selected = state.prefs.nightSilentEnabled,
            onClick = onToggleNightSilent,
            label = { Text(strings.quickNightSilent) },
            leadingIcon = { Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("quick_night_silent"),
          )
          FilterChip(
            selected = false,
            onClick = onOpenStorageTab,
            label = { Text(strings.quickStorageClean) },
            leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("quick_storage_clean"),
          )
          FilterChip(
            selected = false,
            onClick = onOpenLiveMonitor,
            label = { Text(strings.liveServiceAction) },
            leadingIcon = { Icon(Icons.Rounded.Sensors, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("open_live_monitor_button"),
          )
        }
      }
    }

    // Today's Drain Graph + Screen-on vs Screen-off
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.todaysDrainGraph, style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onOpenBatteryTab) {
            Text(if (strings.isHindi) "विस्तार देखें" else "Details")
          }
        }
        TelemetryLineChart(
          values = state.snapshots24h.map { it.levelPercent.toFloat() },
          minRange = 0f,
          maxRange = 100f,
          lineColor = SemanticCoolMint,
          emptyMessage = if (strings.isHindi) "24 घंटे का ग्राफ़ डेटा इकट्ठा हो रहा है..." else "Collecting live battery snapshots for today's chart...",
        )

        val screenOnPts = state.snapshots24h.count { it.screenOn }
        val screenOffPts = state.snapshots24h.count { !it.screenOn }
        val totalPts = (screenOnPts + screenOffPts).coerceAtLeast(1)
        val screenOnFrac = screenOnPts.toFloat() / totalPts
        Text(
          text = "${strings.screenOnVsOff}: ${(screenOnFrac * 100).toInt()}% On / ${100 - (screenOnFrac * 100).toInt()}% Off",
          style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
          progress = { screenOnFrac },
          modifier = Modifier.fillMaxWidth().height(8.dp),
          color = SemanticWarmAmber,
          trackColor = SemanticIceCyan.copy(alpha = 0.35f),
        )
      }
    }

    // Doze Mode Efficiency Card
    item {
      DozeEfficiencyCard(
        metrics = state.dozeEfficiency,
        isHindi = state.prefs.appLanguage == com.example.domain.model.AppLanguage.HINDI,
        whyLabel = strings.whyButton,
      )
    }

    // Thermal & Top Apps & Storage Quick Summaries
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.topAppsTitle, style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onOpenAppsTab) { Text(if (strings.isHindi) "सभी ऐप्स" else "View Apps") }
        }
        if (state.topApps.isEmpty()) {
          Text(
            text = if (state.hasUsageStatsPermission) {
              if (strings.isHindi) "पिछले 24 घंटों में कोई भारी ऐप उपयोग नहीं मिला।" else "No heavy foreground app usage recorded in the last 24h."
            } else {
              if (strings.isHindi) "शीर्ष बैटरी उपयोग वाले ऐप्स देखने के लिए Apps टैब में Usage Access अनुमति दें।"
              else "Grant Usage Access in the Apps tab to inspect real per-app battery rank."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          state.topApps.take(3).forEach { app ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("#${app.batteryRank} ${app.appName}", style = MaterialTheme.typography.bodyLarge)
              Text(
                "${app.foregroundMinutesToday}m (${app.estimatedDrainSharePercent.toInt()}% foreground)",
                style = TelemetryMonoStyle,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        val st = state.storageInsight
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.storageSummary, style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onOpenStorageTab) { Text(if (strings.isHindi) "साफ़ करें" else "Manage") }
        }
        val usedFraction = if (st.totalBytes > 0L) (st.usedBytes.toFloat() / st.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
          progress = { usedFraction },
          modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
          text = if (strings.isHindi) {
            "खाली जगह: ${StorageInspector.formatBytes(st.freeBytes)} (${String.format("%.1f", st.freePercent)}%) • नेत्रा कैश: ${StorageInspector.formatBytes(st.appOwnCacheBytes)}"
          } else {
            "Free: ${StorageInspector.formatBytes(st.freeBytes)} (${String.format("%.1f", st.freePercent)}%) • Netra cache: ${StorageInspector.formatBytes(st.appOwnCacheBytes)}"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.heatReport?.let { hr ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${strings.zoneLabel(hr.zone)} • ${if (strings.isHindi) hr.trend.messageHi else hr.trend.messageEn}",
              style = MaterialTheme.typography.bodyMedium,
              color = zoneColor(hr.zone),
            )
            TextButton(onClick = onOpenHeatTab) { Text(if (strings.isHindi) "गर्मी जांच" else "Heat Lab") }
          }
        }
      }
    }
    item {
      com.example.ui.components.ConnectedBluetoothCard(
        isHindi = state.prefs.appLanguage == com.example.domain.model.AppLanguage.HINDI,
        measurementsAllowed = state.powerBudget.activeTier == AdaptivePowerTier.NORMAL || state.powerBudget.activeTier == AdaptivePowerTier.SAVER,
      )
    }
  }
}

@Composable
private fun TelemetryMiniStat(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(vertical = 4.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = if (value == "Unavailable") MaterialTheme.typography.labelSmall else TelemetryMonoStyle,
      maxLines = 1,
      softWrap = false,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
