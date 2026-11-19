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
      name = "OS-reported Thermal Safety",
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
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Charging Speed & Net Wattage Classifier",
      runsInTier = "Normal / Charging (Throttled in Survival)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Screen-Off Abnormal Drain Check (WorkManager)",
      runsInTier = "Normal, Saver (Suspended in Critical/Survival)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Storage Audit & Self-Cache Cleanup (WorkManager)",
      runsInTier = "Normal only (Postponed <15% or >=40°C)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Live Foreground Dashboard & Charts",
      runsInTier = "Foreground Only (Paused immediately on ON_STOP)",
      maxWakeupsPerHour = 0, // Zero background timer wakeups
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Idle Battery Snapshot Persistence (WorkManager)",
      runsInTier = "Idle Maintenance Window Only (RequiresDeviceIdle + BatteryNotLow)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Thermal Threshold Broadcast Monitor (ACTION_BATTERY_CHANGED)",
      runsInTier = "OS thermal status and battery-health alerts remain essential",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Hourly Temp & Voltage Canvas Chart (Room)",
      runsInTier = "Foreground Visible Only (Stops Flow collection on ON_STOP)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "DiskUsageHelper (StorageStatsManager Idle Scan)",
      runsInTier = "Idle Maintenance Window Only (RequiresDeviceIdle + BatteryNotLow, >=15%, <40C)",
      maxWakeupsPerHour = 1,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Charging TTS Milestone Assistant",
      runsInTier = "Charging + Screen-On Only (Lazy TTS bind + 20s auto-release)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Location Power Saver (Trip Mode FGS)",
      runsInTier = "User-initiated Trip only (Normal/Saver; GPS off in Survival)",
      maxWakeupsPerHour = 4,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Daily Battery Cycle & Capacity Degradation Estimator (WorkManager)",
      runsInTier = "Charging Only (Once per day; RequiresCharging + BatteryNotLow)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Doze Mode Broadcast Guard (ACTION_DEVICE_IDLE_MODE_CHANGED)",
      runsInTier = "Tier-0 Event-Driven (Zero polling; pauses all non-essential work in Doze)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Battery Health Optimizer (In-Memory Usage Advisor)",
      runsInTier = "Foreground Visible Only (Throttled in Doze / Critical / Survival)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Battery Health & Temp CSV Exporter (WorkManager / On-Demand)",
      runsInTier = "Charging Only (RequiresCharging constraint; blocked when unplugged)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Historical Charging Session DAO (State-Change Only)",
      runsInTier = "Tier-0 Event-Driven (Plug/Unplug & Level/Speed Transitions Only)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Doze Efficiency Card (Transition-Based Analytics)",
      runsInTier = "Foreground Visible Only (Derived from existing Doze logs)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Background Thermal Safety Worker (BatteryManager -> Room)",
      runsInTier = "Tier-0 Safety (30m WorkManager; OS thermal/health warnings)",
      maxWakeupsPerHour = 2,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Battery Temperature Trend Chart (Room Native Canvas)",
      runsInTier = "Foreground Visible Only (Static snapshot in Survival tier)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Battery Threshold TTS Service (5% milestones)",
      runsInTier = "Event-Driven Threshold Crossing Only (Auto-stopSelf after utterance)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Storage Analysis Dashboard (Occupied vs Free & App Categories)",
      runsInTier = "Foreground Visible Only (Derived from StatFs & Idle StorageStatsManager)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
    FeaturePowerGate(
      featureName = "Critical Thermal Events History List View (Room)",
      runsInTier = "Foreground Visible Only (Reactive Room Flow paused on ON_STOP)",
      maxWakeupsPerHour = 0,
      networkCallsPerHour = 0,
      measuredImpact = "Unavailable: not measured on this device",
    ),
  )
}
