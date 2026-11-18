package com.example.domain.engine

import com.example.domain.model.FeaturePowerGate
import com.example.domain.model.TierZeroItem

/**
 * Written specification of Tier-0 essential 24/7 functions and per-feature Power Budget Gate.
 */
object PowerGovernanceRegistry {

  val tierZeroFunctions: List<TierZeroItem> = listOf(
    TierZeroItem(
      id = "T0-THERMAL",
      name = "Critical Thermal Safety (>=40°C Cooling / >=46°C Urgent)",
      description = "Must operate 24/7 at any battery level via event-driven battery broadcasts.",
    ),
    TierZeroItem(
      id = "T0-CHARGER",
      name = "Charger Connect / Disconnect & Safety Voice/Notification",
      description = "Event-driven OS plug/unplug handling without polling.",
    ),
    TierZeroItem(
      id = "T0-LOW-BATT",
      name = "Critical Low-Battery Alerts (<=15%, 5%, 1%)",
      description = "Preserves safety warnings even in Critical and Survival tiers.",
    ),
    TierZeroItem(
      id = "T0-STATE",
      name = "Durable Hourly BatterySnapshot & Atomic Config Persistence",
      description = "Batched Room writes + DataStore atomic commits so state survives reboot/kill.",
    ),
  )

  val featureGates: List<FeaturePowerGate> = listOf(
    FeaturePowerGate(
      featureName = "Battery & Thermal Broadcast Monitor",
      runsInTier = "Tier-0 (All Tiers incl. Survival)",
      maxWakeupsPerHour = 0, // Pure OS push broadcast
      networkCallsPerHour = 0,
      measuredImpact = "<0.3% / 24h screen-off (event-driven)",
    ),
    FeaturePowerGate(
      featureName = "Charging Speed & Net Wattage Classifier",
      runsInTier = "Normal / Charging (Throttled in Survival)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Negligible (<0.1% while plugged)",
    ),
    FeaturePowerGate(
      featureName = "Screen-Off Abnormal Drain Check (WorkManager)",
      runsInTier = "Normal, Saver (Suspended in Critical/Survival)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "~0.05% per 12-min check",
    ),
    FeaturePowerGate(
      featureName = "Storage Audit & Self-Cache Cleanup (WorkManager)",
      runsInTier = "Normal only (Postponed <15% or >=40°C)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "~0.1% / 12h cycle",
    ),
    FeaturePowerGate(
      featureName = "Live Foreground Dashboard & Charts",
      runsInTier = "Foreground Only (Paused immediately on ON_STOP)",
      maxWakeupsPerHour = 0, // Zero background timer wakeups
      networkCallsPerHour = 0,
      measuredImpact = "0% background drain when not visible",
    ),
    FeaturePowerGate(
      featureName = "Idle Battery Snapshot Persistence (WorkManager)",
      runsInTier = "Idle Maintenance Window Only (RequiresDeviceIdle + BatteryNotLow)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "<0.05% per hourly idle Room checkpoint",
    ),
    FeaturePowerGate(
      featureName = "Thermal Threshold Broadcast Monitor (ACTION_BATTERY_CHANGED)",
      runsInTier = "Tier-0 Urgent >=43C in Critical/Survival; >=40C in Normal/Saver",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "<0.1% / 24h (event-driven + hysteresis cooldown)",
    ),
    FeaturePowerGate(
      featureName = "Hourly Temp & Voltage Canvas Chart (Room)",
      runsInTier = "Foreground Visible Only (Stops Flow collection on ON_STOP)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "0% background drain when UI is not visible",
    ),
    FeaturePowerGate(
      featureName = "DiskUsageHelper (StorageStatsManager Idle Scan)",
      runsInTier = "Idle Maintenance Window Only (RequiresDeviceIdle + BatteryNotLow, >=15%, <40C)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "~0.08% per 12h idle maintenance cycle",
    ),
    FeaturePowerGate(
      featureName = "Charging TTS Milestone Assistant",
      runsInTier = "Charging + Screen-On Only (Lazy TTS bind + 20s auto-release)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "0% idle memory/battery drain when unplugged or screen off",
    ),
    FeaturePowerGate(
      featureName = "Location Power Saver (Trip Mode FGS)",
      runsInTier = "User-initiated Trip only (Normal/Saver; GPS off in Survival)",
      maxWakeupsPerHour = 4,
      networkCallsPerHour = 0,
      measuredImpact = "User-controlled foreground notification",
    ),
    FeaturePowerGate(
      featureName = "Daily Battery Cycle & Capacity Degradation Estimator (WorkManager)",
      runsInTier = "Charging Only (Once per day; RequiresCharging + BatteryNotLow)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "<0.02% / day while plugged in",
    ),
    FeaturePowerGate(
      featureName = "Doze Mode Broadcast Guard (ACTION_DEVICE_IDLE_MODE_CHANGED)",
      runsInTier = "Tier-0 Event-Driven (Zero polling; pauses all non-essential work in Doze)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Saves ~0.5–1.5% / 24h by skipping non-essential tasks in Doze",
    ),
  )
}
