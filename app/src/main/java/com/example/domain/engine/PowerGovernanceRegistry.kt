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
      featureName = "Location Power Saver (Trip Mode FGS)",
      runsInTier = "User-initiated Trip only (Normal/Saver; GPS off in Survival)",
      maxWakeupsPerHour = 4,
      networkCallsPerHour = 0,
      measuredImpact = "User-controlled foreground notification",
    ),
  )
}
