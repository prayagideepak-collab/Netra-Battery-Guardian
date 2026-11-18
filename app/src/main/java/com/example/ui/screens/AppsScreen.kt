package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.LocationSaverMode
import com.example.ui.NetraUiState
import com.example.ui.components.GuardianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WhyExplainChip
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
  state: NetraUiState,
  strings: AppStrings,
  onOpenUsageAccess: () -> Unit,
  onOpenAppInfo: (String) -> Unit,
  onOpenAppNotifications: (String) -> Unit,
  onOpenBatteryOptimizationSettings: () -> Unit,
  onOpenBatterySaverSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  onOpenDataSaverSettings: () -> Unit,
  onSetLocationMode: (LocationSaverMode, Int?) -> Unit,
  onSetEmergencyException: (Boolean) -> Unit,
  onToggleTripMode: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showLocationRationale by remember { mutableStateOf(false) }
  var pendingLocationMode by remember { mutableStateOf<LocationSaverMode?>(null) }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      if (granted) {
        if (pendingLocationMode == LocationSaverMode.TRIP) {
          onToggleTripMode(true)
        } else if (pendingLocationMode != null) {
          onSetLocationMode(pendingLocationMode!!, state.prefs.locationSamplingIntervalMinutes)
        }
      }
      pendingLocationMode = null
    }

  fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Text(
          text = if (strings.isHindi) "ऐप्स और लोकेशन पावर सेवर" else "App Standby & Location Saver",
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }

    // System Quick Deep Links
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(
          text = if (strings.isHindi) "Android सिस्टम पावर सेटिंग्स" else "Android System Power Controls",
          style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(onClick = onOpenBatteryOptimizationSettings, modifier = Modifier.testTag("deeplink_battery_opt")) {
            Text(if (strings.isHindi) "बैटरी ऑप्टिमाइज़ेशन" else "Battery Optimization")
          }
          OutlinedButton(onClick = onOpenBatterySaverSettings, modifier = Modifier.testTag("deeplink_battery_saver")) {
            Text(if (strings.isHindi) "बैटरी सेवर" else "Battery Saver")
          }
          OutlinedButton(onClick = onOpenDataSaverSettings, modifier = Modifier.testTag("deeplink_data_saver")) {
            Text(if (strings.isHindi) "डेटा सेवर" else "Data Saver")
          }
          OutlinedButton(onClick = onOpenLocationSettings, modifier = Modifier.testTag("deeplink_location")) {
            Text(if (strings.isHindi) "लोकेशन सेटिंग्स" else "Location Settings")
          }
        }
      }
    }

    // Location Power Saver
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (strings.isHindi) "लोकेशन पावर सेवर" else "Location Power Saver",
            style = MaterialTheme.typography.titleMedium,
          )
          WhyExplainChip(
            label = strings.whyButton,
            title = if (strings.isHindi) "लोकेशन पावर सेवर" else "How Location Power Saver Works",
            explanation = if (strings.isHindi) {
              "नेत्रा कभी चुपचाप लोकेशन नहीं मांगता। बैकग्राउंड सैंपलिंग को Android नियंत्रित करता है। ट्रिप मोड केवल आपके शुरू करने पर ही फॉरग्राउंड नोटिफ़िकेशन के साथ चलता है।"
            } else {
              "Netra never requests location silently. Android controls background location frequency. Trip Mode runs as a visible foreground service only when you manually start a trip."
            },
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          LocationSaverMode.entries.forEach { mode ->
            FilterChip(
              selected = state.prefs.locationSaverMode == mode,
              onClick = {
                if (mode == LocationSaverMode.SAMPLING || mode == LocationSaverMode.TRIP) {
                  if (hasLocationPermission()) {
                    if (mode == LocationSaverMode.TRIP) onToggleTripMode(!state.prefs.isTripActive)
                    else onSetLocationMode(mode, state.prefs.locationSamplingIntervalMinutes)
                  } else {
                    pendingLocationMode = mode
                    showLocationRationale = true
                  }
                } else {
                  onSetLocationMode(mode, null)
                }
              },
              label = { Text(strings.locationModeLabel(mode)) },
            )
          }
        }

        if (state.prefs.locationSaverMode == LocationSaverMode.SAMPLING) {
          Text(
            text = if (strings.isHindi) "सैंपलिंग अंतराल: ${state.prefs.locationSamplingIntervalMinutes} मिनट (Android द्वारा नियंत्रित)"
            else "Sampling interval: ${state.prefs.locationSamplingIntervalMinutes} min (OS controls background cadence)",
            style = MaterialTheme.typography.bodyMedium,
          )
          Slider(
            value = state.prefs.locationSamplingIntervalMinutes.toFloat(),
            onValueChange = { onSetLocationMode(LocationSaverMode.SAMPLING, it.toInt()) },
            valueRange = 2f..30f,
          )
        }

        if (state.prefs.locationSaverMode == LocationSaverMode.TRIP) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (state.prefs.isTripActive) "Trip Mode Active (Foreground Notice Shown)" else "Trip Mode Ready",
              style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { onToggleTripMode(!state.prefs.isTripActive) }) {
              Text(if (state.prefs.isTripActive) "End Trip" else "Start Trip")
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (strings.isHindi) "आपातकालीन / नेविगेशन अपवाद" else "Navigation / Safety Exception",
              style = MaterialTheme.typography.bodyLarge,
            )
            Text(
              text = if (strings.isHindi) "नेविगेशन चालू रहने पर लोकेशन न रोकें।" else "Never auto-pause location while safety tracking or navigation is active.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = state.prefs.emergencyLocationException,
            onCheckedChange = onSetEmergencyException,
          )
        }
      }
    }

    // UsageStats Permission or Ranked Apps
    if (!state.hasUsageStatsPermission) {
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp), accentColor = SemanticWarmAmber) {
          Text(
            text = if (strings.isHindi) "ऐप बैटरी उपयोग रैंकिंग अनलॉक करें" else "Enable Real App Battery Insights",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = if (strings.isHindi) {
              "नेत्रा नकली ऐप डेटा नहीं दिखाता। वास्तविक UsageStats देखने के लिए कृपया Android Usage Access अनुमति दें।"
            } else {
              "Netra never invents fake app drain numbers. Grant Android Usage Access so Netra can inspect real per-app usage, location permissions, and battery optimization state locally."
            },
            style = MaterialTheme.typography.bodyMedium,
          )
          Button(onClick = onOpenUsageAccess, modifier = Modifier.testTag("grant_usage_access_button")) {
            Text(if (strings.isHindi) "Usage Access सेटिंग खोलें" else "Open Usage Access Settings")
          }
        }
      }
    } else if (state.topApps.isEmpty()) {
      item {
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Text(
            text = if (strings.isHindi) "आज किसी ऐप में उच्च खपत दर्ज नहीं हुई।" else "No high-usage apps detected in the last 24 hours.",
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    } else {
      items(state.topApps, key = { it.packageName }) { app ->
        GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("#${app.batteryRank} ${app.appName}", style = MaterialTheme.typography.titleMedium)
              Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
              text = "${app.foregroundMinutesToday}m • ${app.estimatedDrainSharePercent.toInt()}% listed foreground time",
              style = TelemetryMonoStyle,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(
              text = if (app.isBatteryOptimized) "Optimized" else "Unrestricted",
              color = if (app.isBatteryOptimized) SemanticCoolMint else SemanticWarmAmber,
            )
            if (app.hasLocationPermission) {
              StatusPill(text = "Location Perm", color = SemanticWarmAmber)
            }
            if (app.isLikelyGame) {
              StatusPill(text = "Game", color = MaterialTheme.colorScheme.secondary)
            }
          }

          Text(
            text = if (strings.isHindi) app.recommendedActionHi else app.recommendedActionEn,
            style = MaterialTheme.typography.bodyMedium,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpenAppInfo(app.packageName) }) {
              Text(if (strings.isHindi) "ऐप जानकारी" else "App Info")
            }
            OutlinedButton(onClick = { onOpenAppNotifications(app.packageName) }) {
              Text(if (strings.isHindi) "नोटिफ़िकेशन" else "Notifications")
            }
            WhyExplainChip(
              label = strings.whyButton,
              title = app.appName,
              explanation = if (strings.isHindi) app.whyHi else app.whyEn,
            )
          }
        }
      }
    }
  }

  if (showLocationRationale) {
    AlertDialog(
      onDismissRequest = { showLocationRationale = false },
      title = { Text(if (strings.isHindi) "लोकेशन अनुमति क्यों?" else "Why Location Permission?") },
      text = {
        Text(
          if (strings.isHindi) {
            "ट्रिप या सैंपलिंग मोड चुनने पर ही लोकेशन अनुमति मांगी जाती है। लोकेशन डेटा कभी डिवाइस से बाहर नहीं भेजा जाता।"
          } else {
            "Netra only asks for foreground location when you explicitly enable Sampling or Trip Mode. No location data ever leaves your device."
          }
        )
      },
      confirmButton = {
        Button(onClick = {
          showLocationRationale = false
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_COARSE_LOCATION,
              Manifest.permission.ACCESS_FINE_LOCATION,
            )
          )
        }) {
          Text(if (strings.isHindi) "अनुमति दें" else "Continue")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showLocationRationale = false
          pendingLocationMode = null
        }) {
          Text(if (strings.isHindi) "रद्द करें" else "Cancel")
        }
      },
    )
  }
}
