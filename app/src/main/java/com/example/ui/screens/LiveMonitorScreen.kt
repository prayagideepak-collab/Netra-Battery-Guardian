package com.example.ui.screens

import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.BatterySnapshot
import com.example.service.BatteryMonitoringService
import com.example.service.BatteryServiceState
import com.example.ui.components.AnimatedBatteryRing
import com.example.ui.components.GuardianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.WhyExplainChip
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 screen that observes [BatteryMonitoringService.serviceState] in real time
 * and displays live battery percentage, temperature, charging status, and idle Room persistence.
 */
@Composable
fun LiveMonitorScreen(
  latestHourlySnapshot: BatterySnapshot?,
  strings: AppStrings,
  onNavigateBack: () -> Unit,
  onOpenBatteryHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val serviceState by BatteryMonitoringService.serviceState.collectAsStateWithLifecycle()

  LiveMonitorContent(
    serviceState = serviceState,
    latestHourlySnapshot = latestHourlySnapshot,
    strings = strings,
    onToggleService = { shouldStart ->
      if (shouldStart) {
        BatteryMonitoringService.start(context)
      } else {
        BatteryMonitoringService.stop(context)
      }
    },
    onNavigateBack = onNavigateBack,
    onOpenBatteryHistory = onOpenBatteryHistory,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMonitorContent(
  serviceState: BatteryServiceState,
  latestHourlySnapshot: BatterySnapshot?,
  strings: AppStrings,
  onToggleService: (Boolean) -> Unit,
  onNavigateBack: () -> Unit,
  onOpenBatteryHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val timeFormat = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
  val tempColor =
    when {
      serviceState.temperatureCelsius >= 43f -> SemanticCriticalRed
      serviceState.temperatureCelsius >= 40f -> SemanticHotOrange
      serviceState.temperatureCelsius >= 36f -> SemanticWarmAmber
      else -> SemanticCoolMint
    }
  val thermalStatusLabel =
    when {
      !serviceState.temperatureCelsius.isFinite() -> "Unavailable"
      serviceState.temperatureCelsius >= 43f -> if (strings.isHindi) "अति गर्म" else "Critical Heat"
      serviceState.temperatureCelsius >= 40f -> if (strings.isHindi) "गर्म" else "Hot"
      serviceState.temperatureCelsius >= 36f -> if (strings.isHindi) "हल्का गर्म" else "Warm"
      else -> if (strings.isHindi) "सामान्य" else "Normal"
    }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = strings.liveMonitorTitle,
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("live_monitor_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Navigate back",
            )
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Foreground Service Status & Toggle Bar
      item {
        ElevatedCard(
          modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
          ),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Sensors,
                contentDescription = "Battery monitoring service indicator",
                tint = if (serviceState.isRunning) SemanticCoolMint else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Column {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "BatteryMonitoringService",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  StatusPill(
                    text = if (serviceState.isRunning) "LIVE" else "STANDBY",
                    color = if (serviceState.isRunning) SemanticCoolMint else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("monitoring_service_status_pill"),
                  )
                }
                Text(
                  text = if (serviceState.isRunning) {
                    if (strings.isHindi) "ACTION_BATTERY_CHANGED सक्रिय" else "Tracking ACTION_BATTERY_CHANGED"
                  } else {
                    if (strings.isHindi) "सर्विस शुरू करने के लिए टॉगल करें" else "Toggle to start background service"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = serviceState.isRunning,
              onCheckedChange = onToggleService,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("monitoring_service_switch")
                .semantics {
                  contentDescription =
                    if (serviceState.isRunning) "Stop Battery Monitoring Service"
                    else "Start Battery Monitoring Service"
                },
            )
          }
        }
      }

      // Real-Time Percentage, Temperature & Charging Status Hero Card
      item {
        GuardianCard(
          modifier = Modifier.widthIn(max = 720.dp),
          accentColor = tempColor,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (strings.isHindi) "रीयल-टाइम सर्विस टेलीमेट्री" else "Real-Time Service Telemetry",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (serviceState.lastUpdatedMillis > 0L) {
                timeFormat.format(Date(serviceState.lastUpdatedMillis))
              } else {
                if (strings.isHindi) "प्रतीक्षा में" else "Awaiting broadcast"
              },
              style = TelemetryMonoStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Primary metrics visible immediately above the fold on all screen sizes
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            MetricTileCard(
              icon = Icons.Rounded.BatteryChargingFull,
              label = if (strings.isHindi) "बैटरी %" else "Battery Level",
              value = "${batteryLevelText(serviceState.levelPercent)}",
              sublabel = "${telemetryText(if (serviceState.voltageMv > 0) serviceState.voltageMv / 1000f else Float.NaN, " V", 2)}",
              accent = SemanticCoolMint,
              modifier = Modifier
                .weight(1f)
                .testTag("service_battery_percent"),
            )
            MetricTileCard(
              icon = Icons.Rounded.Thermostat,
              label = strings.temperature,
              value = "${telemetryText(serviceState.temperatureCelsius, "°C", 1)}",
              sublabel = thermalStatusLabel,
              accent = tempColor,
              modifier = Modifier
                .weight(1f)
                .testTag("service_temperature"),
            )
          }

          OutlinedCard(
            modifier = Modifier
              .fillMaxWidth()
              .semantics(mergeDescendants = true) {}
              .testTag("service_charging_status"),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Bolt,
                  contentDescription = "Charging status",
                  tint = if (serviceState.isCharging) SemanticCoolMint else SemanticIceCyan,
                )
                Column {
                  Text(
                    text = if (strings.isHindi) "चार्जिंग स्थिति" else "Charging Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = serviceState.chargingStatus,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
              AssistChip(
                onClick = {},
                label = { Text("Plug: ${serviceState.plugType}") },
              )
            }
          }

          LinearProgressIndicator(
            progress = { (serviceState.levelPercent.coerceIn(0, 100) / 100f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp),
            color = if (serviceState.levelPercent <= 15) SemanticCriticalRed else SemanticCoolMint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )

          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            AnimatedBatteryRing(
              levelPercent = serviceState.levelPercent,
              isCharging = serviceState.isCharging,
              temperatureCelsius = serviceState.temperatureCelsius,
              animateRing = true,
              statusLabel = serviceState.chargingStatus,
              modifier = Modifier
                .size(164.dp)
                .testTag("service_battery_ring"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Current: ${telemetryText(serviceState.currentMa, " mA", 0)}",
                style = TelemetryMonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FilledTonalButton(
                onClick = { onToggleService(!serviceState.isRunning) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("toggle_monitoring_service_button"),
              ) {
                Icon(
                  imageVector = if (serviceState.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (serviceState.isRunning) {
                    if (strings.isHindi) "सर्विस रोकें" else "Stop Service"
                  } else {
                    if (strings.isHindi) "सर्विस शुरू करें" else "Start Service"
                  },
                )
              }
            }
          }
        }
      }

      // Periodic Idle Room Persistence Status Card
      item {
        OutlinedCard(
          modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Schedule,
                  contentDescription = null,
                  tint = SemanticIceCyan,
                )
                Text(
                  text = if (strings.isHindi) "आइडल-ओनली Room बैकअप" else "Idle-Only Periodic Room Persistence",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              WhyExplainChip(
                label = strings.whyButton,
                title = if (strings.isHindi) "केवल Idle मोड में डेटा सेव क्यों?" else "Why Device-Idle Constraints?",
                explanation = if (strings.isHindi) {
                  "WorkManager कार्य केवल तभी चलता है जब फ़ोन निष्क्रिय (Device Idle) हो और बैटरी कम न हो, ताकि 24/7 मॉनिटरिंग में न्यूनतम बैटरी खर्च हो।"
                } else {
                  "IdleBatteryPersistenceWorker enforces setRequiresDeviceIdle(true) and setRequiresBatteryNotLow(true) so Room database writes piggyback on natural OS idle windows with near-zero battery drain."
                },
              )
            }

            Text(
              text = if (strings.isHindi) {
                "शेड्यूल: हर 1 घंटे • शर्तें: Device Idle = TRUE • Battery Not Low = TRUE"
              } else {
                "Schedule: Hourly Periodic • Constraints: RequiresDeviceIdle = TRUE • BatteryNotLow = TRUE"
              },
              style = TelemetryMonoStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (latestHourlySnapshot != null) {
              Text(
                text = "Latest Room Snapshot: ${latestHourlySnapshot.levelPercent}% • ${
                  telemetryText(latestHourlySnapshot.temperatureCelsius)
                }°C • ${latestHourlySnapshot.chargingStatus} (${timeFormat.format(Date(latestHourlySnapshot.timestampMillis))})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            } else {
              Text(
                text = if (strings.isHindi) {
                  "पहला आइडल Room स्नैपशॉट प्रतीक्षारत है।"
                } else {
                  "Waiting for the next device-idle window to record a Room snapshot."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            FilledTonalButton(
              onClick = onOpenBatteryHistory,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("live_monitor_view_history_button"),
            ) {
              Text(if (strings.isHindi) "बैटरी इतिहास देखें" else "View Battery History")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricTileCard(
  icon: ImageVector,
  label: String,
  value: String,
  sublabel: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  OutlinedCard(
    modifier = modifier.semantics(mergeDescendants = true) {},
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = accent,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = sublabel,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
      )
    }
  }
}
