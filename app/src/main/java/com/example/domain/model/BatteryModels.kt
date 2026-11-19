package com.example.domain.model

enum class AppLanguage(val code: String) {
  ENGLISH("en"),
  HINDI("hi"),
  HINGLISH("en-hi"),
}

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

enum class CoolingSensitivity {
  RELAXED,
  BALANCED,
  AGGRESSIVE,
}

enum class LocationSaverMode {
  NORMAL,
  BATTERY_SAVER,
  SAMPLING,
  TRIP,
}

enum class GuardianProfile {
  NORMAL,
  BATTERY_SAVER,
  ULTRA_BATTERY_SAVER,
  SLEEP,
  GAMING,
  TRAVEL,
  COOLING,
  CHARGING,
}

enum class AdaptivePowerTier(
  val maxWakeupsPerHour: Int,
  val samplingIntervalSeconds: Int,
  val idleTargetDrainPer24hPercent: Double,
) {
  NORMAL(maxWakeupsPerHour = 4, samplingIntervalSeconds = 15, idleTargetDrainPer24hPercent = 2.0),
  SAVER(maxWakeupsPerHour = 2, samplingIntervalSeconds = 45, idleTargetDrainPer24hPercent = 1.2),
  CRITICAL(maxWakeupsPerHour = 1, samplingIntervalSeconds = 90, idleTargetDrainPer24hPercent = 0.8),
  SURVIVAL(maxWakeupsPerHour = 0, samplingIntervalSeconds = 180, idleTargetDrainPer24hPercent = 0.4),
}

enum class PlugType {
  NONE,
  AC,
  USB,
  WIRELESS,
  DOCK,
}

enum class ThermalLevel {
  NONE,
  LIGHT,
  MODERATE,
  SEVERE,
  CRITICAL,
  EMERGENCY,
  SHUTDOWN,
}

enum class BatteryHealthState {
  GOOD,
  OVERHEAT,
  DEAD,
  OVER_VOLTAGE,
  UNSPECIFIED_FAILURE,
  COLD,
  UNKNOWN,
}

enum class ChargingSpeedClass {
  UNAVAILABLE,
  NOT_CHARGING,
  DISCHARGING_WHILE_PLUGGED,
  SLOW,
  STANDARD,
  FAST,
  SUPER_FAST,
  ULTRA_FAST,
  EXTREME_FAST,
}

enum class TemperatureZone {
  UNAVAILABLE,
  TOO_COLD,
  COLD,
  NORMAL,
  WARM,
  HOT,
  VERY_HOT,
  CRITICAL,
  EMERGENCY,
}

enum class TrendStatus {
  UNAVAILABLE,
  HIGH_HEAT_RISK,
  RISING_FAST,
  WARM_STABLE,
  COOLING_DOWN,
  STABLE,
}

enum class ConfidenceLevel {
  LOW,
  MEDIUM,
  HIGH,
}

enum class LogCategory {
  BATTERY,
  HEAT,
  VOICE,
  PROFILE,
  STORAGE,
  LOCATION,
  POWER_BUDGET,
}

enum class LogSeverity {
  INFO,
  ADVISORY,
  WARNING,
  CRITICAL,
}

enum class NetworkType {
  NONE,
  UNMETERED_WIFI,
  METERED_CELLULAR,
  OTHER,
}

enum class AppStandbyBucket {
  ACTIVE,
  WORKING_SET,
  FREQUENT,
  RARE,
  RESTRICTED,
  UNKNOWN,
}

data class TierZeroItem(
  val id: String,
  val name: String,
  val description: String,
)

data class FeaturePowerGate(
  val featureName: String,
  val runsInTier: String,
  val maxWakeupsPerHour: Int,
  val networkCallsPerHour: Int,
  val measuredImpact: String,
)

data class SelfHealingStatus(
  val circuitOpen: Boolean = false,
  val consecutiveFailures: Int = 0,
  val maxRetries: Int = 3,
  val nextRetryAtMillis: Long = 0L,
  val lastCheckMillis: Long = 0L,
  val lastCheckSummary: String = "Healthy",
  val rotatedLogBytes: Long = 0L,
  val logCapBytes: Long = 256 * 1024L,
)

data class BatteryTelemetry(
  val timestampMillis: Long = System.currentTimeMillis(),
  val levelPercent: Int = -1,
  val batteryStatus: Int = 1, // Android BATTERY_STATUS_UNKNOWN
  val isCharging: Boolean = false,
  val isPlugged: Boolean = false,
  val plugType: PlugType = PlugType.NONE,
  val temperatureCelsius: Float = Float.NaN,
  val voltageMv: Int = -1,
  val currentMa: Float = Float.NaN,
  val chargeCounterUah: Long = -1L,
  val estimatedCapacityMah: Int = -1,
  val healthState: BatteryHealthState = BatteryHealthState.UNKNOWN,
  val technology: String = "Unavailable",
  val thermalLevel: ThermalLevel = ThermalLevel.NONE,
  val thermalStatusAvailable: Boolean = true,
  val osBatterySaverOn: Boolean = false,
  val screenOn: Boolean = true,
  val screenBrightnessPercent: Int = -1,
  val canWriteSettings: Boolean = false,
  val hotspotLikelyActive: Boolean = false,
  val poorCellularSignal: Boolean = false,
  val isNetworkMetered: Boolean = false,
  val networkType: NetworkType = NetworkType.NONE,
  val standbyBucket: AppStandbyBucket = AppStandbyBucket.ACTIVE,
  val isAppInForeground: Boolean = true,
  val chargeCycleCount: Int? = null,
) {
  val hasLevel: Boolean get() = levelPercent in 0..100
  val hasTemperature: Boolean get() = temperatureCelsius.isFinite()
  val hasPower: Boolean get() = voltageMv > 0 && currentMa.isFinite()
  val hasSnapshot: Boolean get() = hasLevel

  val voltageVolts: Float
    get() = if (voltageMv > 0) voltageMv / 1000f else Float.NaN

  val instantaneousPowerWatts: Float
    get() = if (hasPower) (voltageMv / 1000f) * (currentMa / 1000f) else Float.NaN
}

data class RollingPowerSample(
  val timestampMillis: Long,
  val voltageMv: Int,
  val currentMa: Float,
)

data class ChargingDiagnosis(
  val speedClass: ChargingSpeedClass,
  val netPowerWatts: Float,
  val rollingAvgCurrentMa: Float,
  val estimatedMinutesRemaining: Int?,
  val isTimeEstimateApproximate: Boolean,
  val limitingFactor: ChargingLimitingFactor,
  val explanationEn: String,
  val explanationHi: String,
  val whyEn: String,
  val whyHi: String,
)

enum class ChargingLimitingFactor {
  NONE,
  HIGH_PHONE_USAGE,
  THERMAL_THROTTLING,
  HIGH_BATTERY_LEVEL,
  WEAK_CHARGER_OR_CABLE,
  CHARGING_PAUSED,
}

data class TemperatureTrendResult(
  val deltaPerMinute: Float,
  val status: TrendStatus,
  val messageEn: String,
  val messageHi: String,
)

data class HeatCauseItem(
  val titleEn: String,
  val titleHi: String,
  val detailEn: String,
  val detailHi: String,
)

data class HeatDiagnosisReport(
  val zone: TemperatureZone,
  val trend: TemperatureTrendResult,
  val confidence: ConfidenceLevel,
  val causes: List<HeatCauseItem>,
  val recommendedActionsEn: List<String>,
  val recommendedActionsHi: List<String>,
  val coolingWorkflowActive: Boolean,
  val coolingStepsAppliedEn: List<String>,
  val coolingStepsAppliedHi: List<String>,
)

data class AppPowerInsight(
  val packageName: String,
  val appName: String,
  val foregroundMinutesToday: Long,
  val estimatedWakeups: Int,
  val batteryRank: Int,
  val estimatedDrainSharePercent: Float,
  val hasLocationPermission: Boolean,
  val isBatteryOptimized: Boolean,
  val isLikelyGame: Boolean,
  val recommendedActionEn: String,
  val recommendedActionHi: String,
  val whyEn: String,
  val whyHi: String,
)

enum class StorageActionTarget {
  APP_OWN_CACHE,
  ANDROID_STORAGE_SETTINGS,
  MANAGE_APPS_SETTINGS,
  BROWSER_SETTINGS,
  WHATSAPP_STORAGE,
  DOWNLOADS_REVIEW,
}

data class StorageRecommendation(
  val id: String,
  val titleEn: String,
  val titleHi: String,
  val descriptionEn: String,
  val descriptionHi: String,
  val whyEn: String,
  val whyHi: String,
  val estimatedReclaimableMb: Int,
  val target: StorageActionTarget,
  val packageHint: String? = null,
)

data class StorageInsight(
  val totalBytes: Long,
  val usedBytes: Long,
  val freeBytes: Long,
  val freePercent: Float,
  val appOwnCacheBytes: Long,
  val downloadsEstimatedBytes: Long,
  val heavyScanPostponedReasonEn: String? = null,
  val heavyScanPostponedReasonHi: String? = null,
  val recommendations: List<StorageRecommendation> = emptyList(),
)

data class PowerBudgetMetrics(
  val activeTier: AdaptivePowerTier,
  val wakeupsThisHour: Int,
  val networkCallsThisHour: Int = 0,
  val snapshotsRecordedToday: Int,
  val monitoringIntervalSeconds: Int,
  val isThermalThrottled: Boolean,
  val dbBatchIntervalSeconds: Int = 60,
  val suspendedFeatures: List<String> = emptyList(),
)

/** Enforced work gates, separate from user-facing descriptions. Safety alerts are never gated. */
fun PowerBudgetMetrics.allowsUsageInspection(): Boolean =
  activeTier == AdaptivePowerTier.NORMAL || activeTier == AdaptivePowerTier.SAVER

fun PowerBudgetMetrics.allowsRoutineVoice(): Boolean = allowsUsageInspection()
