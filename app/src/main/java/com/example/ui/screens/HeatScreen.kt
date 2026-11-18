package com.example.ui.screens

import com.example.domain.model.telemetryText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.GuardianProfile
import com.example.ui.NetraUiState
import com.example.ui.components.GuardianCard
import com.example.ui.components.HourlyTempVoltageChart
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryLineChart
import com.example.ui.components.WhyExplainChip
import com.example.ui.components.zoneColor
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle

@Composable
fun HeatScreen(
  state: NetraUiState,
  strings: AppStrings,
  onToggleCoolingMode: () -> Unit,
  onOpenWriteSettings: () -> Unit,
  onSetCoolingBrightnessAllowed: (Boolean) -> Unit = {},
  onRetryCoolingBrightnessRestore: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val t = state.telemetry
  val report = state.heatReport

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Text(
          text = if (strings.isHindi) "तापमान नियंत्रण और गर्मी निदान" else "Heat Diagnosis & Cooling",
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }

    if (report != null) {
      item {
        val zColor = zoneColor(report.zone)
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp), accentColor = zColor) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${telemetryText(t.temperatureCelsius, "°C", 1)}",
              style = MaterialTheme.typography.displayLarge,
              color = zColor,
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
              StatusPill(strings.zoneLabel(report.zone), zColor)
              StatusPill("OS Thermal: ${strings.thermalLabel(t.thermalLevel)}", MaterialTheme.colorScheme.secondary)
            }
          }

          Text(
            text = if (strings.isHindi) report.trend.messageHi else report.trend.messageEn,
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "5-min Trend: ${telemetryText(report.trend.deltaPerMinute, decimals = 2)} °C/min",
            style = TelemetryMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(
              onClick = onToggleCoolingMode,
              modifier = Modifier.testTag("toggle_cooling_button"),
            ) {
              val isCooling = state.activeProfile == GuardianProfile.COOLING
              Text(
                if (isCooling) {
                  if (strings.isHindi) "कूलिंग मोड सक्रिय (बंद करें)" else "Cooling Active (Tap to Clear)"
                } else {
                  if (strings.isHindi) "कूलिंग मोड शुरू करें" else "Activate Cooling Mode"
                }
              )
            }
            WhyExplainChip(
              label = strings.whyButton,
              title = if (strings.isHindi) "कूलिंग मोड कैसे मदद करता है" else "How Safe Cooling Works",
              explanation = if (strings.isHindi) {
                "नेत्रा नकली कूलिंग का दावा नहीं करता। यह ब्राइटनेस कम करने, लोकेशन सैंपलिंग रोकने और गर्मी के असली कारण पहचानकर फ़ोन को सुरक्षित ठंडा होने देता है।"
              } else {
                "Netra never fakes CPU cooling or kills apps. It lowers screen load (with your permission), pauses non-essential sampling, and guides you to remove heat sources."
              },
            )
          }

          Text(if (strings.isHindi) "वैकल्पिक ब्राइटनेस नियंत्रण" else "Optional brightness control", style = MaterialTheme.typography.titleMedium)
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (strings.isHindi) "कूलिंग को स्क्रीन की रोशनी कम करने दें" else "Allow cooling to dim the screen",
              modifier = Modifier.weight(1f),
            )
            Switch(
              checked = state.coolingBrightness.enabled,
              onCheckedChange = onSetCoolingBrightnessAllowed,
              modifier = Modifier.testTag("cooling_brightness_consent"),
            )
          }
          Text(
            if (strings.isHindi) "शुरू में OFF, अपडेट पर भी। सक्षम करने पर ब्राइटनेस कम और ऑटो-ब्राइटनेस मैनुअल हो सकती है। पहले मूल सेटिंग सहेजी जाएगी। कूलिंग समाप्त या OFF पर बहाली की कोशिश होगी। आपके बाद के बदलाव नहीं बदले जाएंगे। Android अनुमति भी ज़रूरी है।"
            else "OFF by default, including upgrades. When enabled, cooling may lower brightness and switch adaptive brightness to manual. Original settings are saved first. Restore is attempted when cooling ends or you turn this off. If you change brightness yourself, Netra leaves your newer settings alone. Android permission is also required.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(state.coolingBrightness.status, style = MaterialTheme.typography.bodyMedium)
          if (state.coolingBrightness.restorePending) {
            OutlinedButton(onClick = onRetryCoolingBrightnessRestore, modifier = Modifier.testTag("cooling_restore_retry")) {
              Text(if (strings.isHindi) "बहाली लंबित: फिर कोशिश करें" else "Restore pending: retry")
            }
          }

          if (!t.canWriteSettings) {
            OutlinedButton(
              onClick = onOpenWriteSettings,
              modifier = Modifier.testTag("grant_write_settings_button"),
            ) {
              Text(
                if (strings.isHindi) "ऑटो ब्राइटनेस कमी के लिए Write Settings अनुमति दें"
                else "Optional: Allow Brightness Control for Cooling Mode"
              )
            }
          }
        }
      }

      // Heat Diagnosis Output
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (strings.isHindi) "संभावित गर्मी के कारण" else "Heat Diagnosis",
              style = MaterialTheme.typography.titleMedium,
            )
            StatusPill(
              text = "Confidence: ${report.confidence.name}",
              color = SemanticWarmAmber,
            )
          }

          if (report.causes.isEmpty()) {
            Text(
              text = if (strings.isHindi) "अभी कोई असामान्य गर्मी स्रोत नहीं मिला। तापमान सामान्य सीमा में है।"
              else "No active overheating causes detected. Device temperature is within safe bounds.",
              style = MaterialTheme.typography.bodyLarge,
              color = SemanticCoolMint,
            )
          } else {
            report.causes.forEach { cause ->
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "• ${if (strings.isHindi) cause.titleHi else cause.titleEn}",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = if (strings.isHindi) cause.detailHi else cause.detailEn,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          val actions = if (strings.isHindi) report.recommendedActionsHi else report.recommendedActionsEn
          if (actions.isNotEmpty()) {
            Text(
              text = if (strings.isHindi) "सुझाए गए कदम:" else "Recommended Actions:",
              style = MaterialTheme.typography.titleMedium,
            )
            actions.forEach { act ->
              Text("✓ $act", style = MaterialTheme.typography.bodyLarge)
            }
          }

          val steps = if (strings.isHindi) report.coolingStepsAppliedHi else report.coolingStepsAppliedEn
          if (steps.isNotEmpty()) {
            Text(
              text = if (strings.isHindi) "लागू किए गए कूलिंग कदम:" else "Active Cooling Workflow:",
              style = MaterialTheme.typography.titleMedium,
            )
            steps.forEach { step ->
              Text("→ $step", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }

    // Live ThermalMonitor Receiver & Power Tier Guard Card
    item {
      val tm = state.thermalState
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "थर्मल मॉनिटर (ACTION_BATTERY_CHANGED)" else "Thermal Broadcast Monitor",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          StatusPill(
            text = if (tm.isRegistered) "ACTIVE • ${tm.activeTier.name}" else "STANDBY",
            color = if (tm.isRegistered) SemanticCoolMint else SemanticIceCyan,
            modifier = Modifier.testTag("heat_thermal_monitor_pill"),
          )
        }
        Text(
          text = if (strings.isHindi) {
            "उच्च तापमान अलर्ट (${state.prefs.coolingSensitivity.name}) • पावर टियर: ${tm.activeTier.name} • अलर्ट: ${tm.alertCount}"
          } else {
            "Event-driven threshold alerts (${state.prefs.coolingSensitivity.name}) • Power Tier: ${tm.activeTier.name} • Alerts sent: ${tm.alertCount}"
          },
          style = TelemetryMonoStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tm.lastNotificationTitle?.let { lastTitle ->
          Text(
            text = "Last warning: $lastTitle",
            style = MaterialTheme.typography.bodyMedium,
            color = SemanticWarmAmber,
          )
        }
      }
    }

    // Hourly Room Temperature & Voltage Canvas Chart
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

    // Heat Timeline Chart
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(
          text = if (strings.isHindi) "गर्मी टाइमलाइन (पिछले 24 घंटे)" else "Heat Timeline (24h)",
          style = MaterialTheme.typography.titleMedium,
        )
        TelemetryLineChart(
          values = state.snapshots24h.map { it.temperatureCelsius }.filter { it.isFinite() },
          minRange = 10f,
          maxRange = 55f,
          lineColor = SemanticWarmAmber,
          emptyMessage = if (strings.isHindi) "गर्मी टाइमलाइन डेटा जमा हो रहा है..." else "Recording temperature timeline locally...",
        )
      }
    }
  }
}
