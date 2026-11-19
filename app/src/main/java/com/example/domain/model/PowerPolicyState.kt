package com.example.domain.model

/** Process-local policy evidence. This is not a device measurement or a durable checkpoint. */
data class PowerPolicyState(
  val sequence: Long = 0L,
  val observedAtElapsedMillis: Long? = null,
  val activeTier: AdaptivePowerTier = AdaptivePowerTier.CRITICAL,
  val batteryLevelAvailable: Boolean = false,
  val thermalStatusAvailable: Boolean = false,
  val isAppInForeground: Boolean = false,
  val screenOn: Boolean = false,
  val restrictions: Set<PowerRestriction> = setOf(PowerRestriction.NOT_OBSERVED),
  val metrics: PowerBudgetMetrics? = null,
)

enum class PowerRestriction {
  NOT_OBSERVED, BATTERY_UNAVAILABLE, BATTERY_LOW, BATTERY_SAVER, STANDBY, THERMAL,
}

enum class FeatureImportance { ESSENTIAL, ROUTINE, OPTIONAL, DECORATIVE }

enum class PowerFeature(val importance: FeatureImportance) {
  OS_WARNING_RESPONSE(FeatureImportance.ESSENTIAL),
  CONSENTED_URGENT_ALERT(FeatureImportance.ESSENTIAL),
  ESSENTIAL_STATE(FeatureImportance.ESSENTIAL),
  CONTROLLED_RESTORATION(FeatureImportance.ESSENTIAL),
  ROUTINE_HISTORY(FeatureImportance.ROUTINE),
  ORDINARY_MILESTONE(FeatureImportance.ROUTINE),
  USAGE_ANALYSIS(FeatureImportance.OPTIONAL),
  STORAGE_SCAN(FeatureImportance.OPTIONAL),
  REPORT_EXPORT(FeatureImportance.OPTIONAL),
  CYCLE_OBSERVATION(FeatureImportance.OPTIONAL),
  ANIMATION(FeatureImportance.DECORATIVE),
  OPTIONAL_DIAGNOSTIC(FeatureImportance.DECORATIVE),
}

enum class FeatureDisposition { ALLOW, DEFER }
enum class FeatureDecisionReason {
  ESSENTIAL_OPPORTUNITY, STATE_UNAVAILABLE, STALE_STATE, MODE_RESTRICTION,
  DOZE_OR_UNKNOWN, NOT_VISIBLE, BUDGET_NOT_VERIFIED, MODE_WINDOW,
}

data class FeatureDecision(
  val disposition: FeatureDisposition,
  val reason: FeatureDecisionReason,
  val stateSequence: Long,
  val nextOpportunity: String,
) {
  val allowed: Boolean get() = disposition == FeatureDisposition.ALLOW
}
