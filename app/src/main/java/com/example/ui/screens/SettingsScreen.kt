package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppLanguage
import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.ThemeMode
import com.example.ui.NetraUiState
import com.example.ui.components.GuardianCard
import com.example.ui.components.StatusPill
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  state: NetraUiState,
  strings: AppStrings,
  onSetAppLanguage: (AppLanguage) -> Unit,
  onSetVoiceLanguage: (AppLanguage) -> Unit,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSetVoiceEnabled: (Boolean) -> Unit,
  onSetVoiceVolume: (Float) -> Unit,
  onSetAnnounceCharging: (Boolean) -> Unit,
  onSetAnnounceDischarging: (Boolean) -> Unit,
  onSetAnnounceScreenOff: (Boolean) -> Unit,
  onSetAnnouncementStep: (Int) -> Unit,
  onSetNightSilent: (Boolean, Int, Int) -> Unit,
  onSetCoolingSensitivity: (CoolingSensitivity) -> Unit,
  onSetStorageNotifications: (Boolean) -> Unit,
  onSetBatterySaverThreshold: (Int) -> Unit,
  onSetLowBatteryAlerts: (Boolean) -> Unit,
  onSetDataRetentionDays: (Int) -> Unit,
  onSetLiveGuardianService: (Boolean) -> Unit,
  onTestVoice: () -> Unit,
  onExportCsv: () -> Unit,
  onExportPdf: () -> Unit,
  onClearAllData: () -> Unit,
  onQueueChargingCsvExport: () -> Unit = {},
  onSetAnnounceChargingSpeed: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val p = state.prefs
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showActivityLogDialog by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  val logDateFmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Text(
          text = if (strings.isHindi) "सेटिंग्स, वॉइस और गोपनीयता" else "Settings, Voice & Privacy",
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }

    // Language & Theme
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(if (strings.isHindi) "भाषा और थीम" else "Language & Appearance", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = p.appLanguage == AppLanguage.ENGLISH,
            onClick = { onSetAppLanguage(AppLanguage.ENGLISH) },
            label = { Text("English") },
            modifier = Modifier.testTag("lang_english"),
          )
          FilterChip(
            selected = p.appLanguage == AppLanguage.HINDI,
            onClick = { onSetAppLanguage(AppLanguage.HINDI) },
            label = { Text("हिन्दी") },
            modifier = Modifier.testTag("lang_hindi"),
          )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = p.themeMode == mode,
              onClick = { onSetThemeMode(mode) },
              label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
          }
        }
      }
    }

    // Charging Voice Assistant
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text(if (strings.isHindi) "चार्जिंग वॉइस असिस्टेंट (TTS)" else "Charging Voice Assistant (Offline TTS)", style = MaterialTheme.typography.titleMedium)
          Switch(checked = p.voiceEnabled, onCheckedChange = onSetVoiceEnabled)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AppLanguage.entries.forEach { lang ->
            FilterChip(
              selected = p.voiceLanguage == lang,
              onClick = { onSetVoiceLanguage(lang) },
              label = { Text(lang.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
          }
        }
        Text(if (strings.isHindi) "वॉइस वॉल्यूम: ${(p.voiceVolume * 100).toInt()}%" else "Voice Volume: ${(p.voiceVolume * 100).toInt()}%")
        Slider(value = p.voiceVolume, onValueChange = onSetVoiceVolume, valueRange = 0.1f..1f)

        SettingToggleRow(
          label = if (strings.isHindi) "केवल चार्जिंग प्रकार बोलें" else "Charging type announcements",
          checked = p.announceChargingSpeed,
          onCheckedChange = onSetAnnounceChargingSpeed,
        )
        Text(if (strings.isHindi) "पावर बजट सत्यापित होने तक प्रकार की आवाज़ स्थगित है।" else "Type speech deferred until its power budget is verified.", style = MaterialTheme.typography.bodySmall)
        SettingToggleRow(
          label = if (strings.isHindi) "चार्जिंग घोषणाएँ (C5–C100)" else "Charging Milestones (C5–C100)",
          checked = p.announceCharging,
          onCheckedChange = onSetAnnounceCharging,
        )
        SettingToggleRow(
          label = if (strings.isHindi) "डिस्चार्जिंग घोषणाएँ (D100–D1)" else "Discharging Milestones (D100–D1)",
          checked = p.announceDischarging,
          onCheckedChange = onSetAnnounceDischarging,
        )
        SettingToggleRow(
          label = if (strings.isHindi) "स्क्रीन बंद होने पर बोलें" else "Speak While Screen Off",
          checked = p.announceWhileScreenOff,
          onCheckedChange = onSetAnnounceScreenOff,
        )

        Text(if (strings.isHindi) "घोषणा अंतराल: हर ${p.announcementStepPercent}%" else "Milestone Interval: Every ${p.announcementStepPercent}%")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(5, 10, 20).forEach { step ->
            FilterChip(
              selected = p.announcementStepPercent == step,
              onClick = { onSetAnnouncementStep(step) },
              label = { Text("$step%") },
            )
          }
        }

        OutlinedButton(onClick = onTestVoice, modifier = Modifier.testTag("test_voice_button")) {
          Text(if (strings.isHindi) "वॉइस टेस्ट करें" else "Test Offline Voice")
        }
      }
    }

    // Night Silent Mode
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        SettingToggleRow(
          label = if (strings.isHindi) "नाइट साइलेंट मोड" else "Night Silent Mode",
          checked = p.nightSilentEnabled,
          onCheckedChange = { onSetNightSilent(it, p.nightSilentStartMinutes, p.nightSilentEndMinutes) },
        )
        Text(
          text = "Silent Window: ${formatMinutes(p.nightSilentStartMinutes)} – ${formatMinutes(p.nightSilentEndMinutes)}. Suppresses C5–C100 / D100–D1; keeps charger connect/disconnect & critical heat alerts.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = p.nightSilentStartMinutes == 23 * 60 && p.nightSilentEndMinutes == 6 * 60,
            onClick = { onSetNightSilent(p.nightSilentEnabled, 23 * 60, 6 * 60) },
            label = { Text("11 PM – 6 AM") },
          )
          FilterChip(
            selected = p.nightSilentStartMinutes == 22 * 60 && p.nightSilentEndMinutes == 7 * 60,
            onClick = { onSetNightSilent(p.nightSilentEnabled, 22 * 60, 7 * 60) },
            label = { Text("10 PM – 7 AM") },
          )
          FilterChip(
            selected = p.nightSilentStartMinutes == 0 && p.nightSilentEndMinutes == 6 * 60,
            onClick = { onSetNightSilent(p.nightSilentEnabled, 0, 6 * 60) },
            label = { Text("12 AM – 6 AM") },
          )
        }
      }
    }

    // Guardian Automation & Monitoring
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(if (strings.isHindi) "स्वचालित सुरक्षा और सीमाएँ" else "Guardian Automation & Thresholds", style = MaterialTheme.typography.titleMedium)
        SettingToggleRow(
          label = if (strings.isHindi) "24/7 लाइव गार्जियन मॉनिटर (नोटिफ़िकेशन)" else "24/7 Live Guardian Service",
          checked = p.liveGuardianServiceEnabled,
          onCheckedChange = onSetLiveGuardianService,
        )
        SettingToggleRow(
          label = if (strings.isHindi) "कम बैटरी चेतावनी (20%, 10%, 5%, 1%)" else "Low Battery Alerts (20%, 10%, 5%, 1%)",
          checked = p.lowBatteryAlertsEnabled,
          onCheckedChange = onSetLowBatteryAlerts,
        )
        SettingToggleRow(
          label = if (strings.isHindi) "स्टोरेज क्लीनअप अलर्ट" else "Storage Cleanup Notifications",
          checked = p.storageNotificationsEnabled,
          onCheckedChange = onSetStorageNotifications,
        )
        Text("Battery Saver Auto Threshold: ${p.batterySaverThreshold}%")
        Slider(
          value = p.batterySaverThreshold.toFloat(),
          onValueChange = { onSetBatterySaverThreshold(it.toInt()) },
          valueRange = 10f..35f,
        )
        Text(if (strings.isHindi) "थर्मल चेतावनियाँ Android की स्थिति पर आधारित हैं।" else "Thermal alerts follow Android's reported state, not a guessed temperature limit.")
        Text("Local Data Retention: ${p.dataRetentionDays} days")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(7, 30, 90).forEach { days ->
            FilterChip(
              selected = p.dataRetentionDays == days,
              onClick = { onSetDataRetentionDays(days) },
              label = { Text("${days}d") },
            )
          }
        }
      }
    }

    // Activity Log, Local Reports & Privacy
    item {
      GuardianCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text(if (strings.isHindi) "ऑफ़लाइन रिपोर्ट और गोपनीयता" else "Offline Reports, Logs & Privacy", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { showActivityLogDialog = true }, modifier = Modifier.testTag("open_activity_log_button")) {
            Text(if (strings.isHindi) "गतिविधि लॉग (${state.activityLogs.size})" else "Activity Log (${state.activityLogs.size})")
          }
          OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier.testTag("export_csv_button"),
          ) {
            Text(if (state.telemetry.isCharging) "Export Health & Temp CSV" else "Export CSV (Charging Only)")
          }
          if (!state.telemetry.isCharging) {
            OutlinedButton(
              onClick = onQueueChargingCsvExport,
              modifier = Modifier.testTag("settings_queue_csv_button"),
            ) {
              Text("Queue CSV for Charge")
            }
          }
          OutlinedButton(onClick = onExportPdf, modifier = Modifier.testTag("export_pdf_button")) {
            Text("Export PDF")
          }
          Button(onClick = { showPrivacyDialog = true }, modifier = Modifier.testTag("open_privacy_button")) {
            Text(if (strings.isHindi) "गोपनीयता नीति" else "Privacy Guarantee")
          }
        }
        OutlinedButton(
          onClick = { showClearConfirm = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticCriticalRed),
          modifier = Modifier.testTag("clear_local_data_button"),
        ) {
          Text(if (strings.isHindi) "सारा स्थानीय डेटा मिटाएँ" else "Clear All Local Data")
        }
      }
    }
  }

  if (showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyDialog = false },
      title = { Text(if (strings.isHindi) "100% ऑफ़लाइन गोपनीयता" else "Netra 100% Offline Privacy") },
      text = {
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(strings.privacyPoints) { (title, desc) ->
            Column {
              StatusPill(title, SemanticCoolMint)
              Text(desc, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
      },
    )
  }

  if (showActivityLogDialog) {
    AlertDialog(
      onDismissRequest = { showActivityLogDialog = false },
      title = { Text(if (strings.isHindi) "ऑफ़लाइन गतिविधि लॉग" else "Offline Activity Log") },
      text = {
        if (state.activityLogs.isEmpty()) {
          Text(if (strings.isHindi) "अभी कोई गतिविधि लॉग नहीं है।" else "No local activity events recorded yet.")
        } else {
          LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.activityLogs.take(40)) { log ->
              Column {
                Text(
                  text = "${logDateFmt.format(Date(log.timestampMillis))} • ${if (strings.isHindi) log.titleHi else log.titleEn}",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = if (strings.isHindi) log.detailHi else log.detailEn,
                  style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                  text = "${strings.whyButton}: ${if (strings.isHindi) log.whyHi else log.whyEn}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showActivityLogDialog = false }) { Text("Close") }
      },
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text(if (strings.isHindi) "स्थानीय डेटा मिटाएँ?" else "Clear All Local Data?") },
      text = {
        Text(
          if (strings.isHindi) "यह सभी स्थानीय बैटरी स्नैपशॉट, चार्जिंग सत्र, लॉग और सेटिंग्स हटा देगा।"
          else "This will delete all locally stored battery snapshots, sessions, activity logs, and preferences."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showClearConfirm = false
            onClearAllData()
          },
          colors = ButtonDefaults.buttonColors(containerColor = SemanticCriticalRed),
        ) {
          Text(if (strings.isHindi) "मिटाएँ" else "Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text(if (strings.isHindi) "रद्द करें" else "Cancel")
        }
      },
    )
  }
}

@Composable
private fun SettingToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

private fun formatMinutes(minutes: Int): String {
  val h24 = (minutes / 60) % 24
  val m = minutes % 60
  val amPm = if (h24 < 12) "AM" else "PM"
  val h12 = if (h24 % 12 == 0) 12 else h24 % 12
  return String.format(Locale.US, "%d:%02d %s", h12, m, amPm)
}
