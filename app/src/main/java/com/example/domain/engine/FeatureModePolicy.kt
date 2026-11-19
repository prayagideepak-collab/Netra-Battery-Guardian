package com.example.domain.engine

import com.example.domain.model.AdaptivePowerTier
import com.example.domain.model.FeatureDecision
import com.example.domain.model.FeatureDecisionReason
import com.example.domain.model.FeatureDisposition
import com.example.domain.model.FeatureImportance
import com.example.domain.model.PowerFeature
import com.example.domain.model.PowerPolicyState

/** Pure mode gate. ALLOW never grants consent, permission, execution eligibility or budget. */
internal object FeatureModePolicy {
  fun evaluate(
    feature: PowerFeature,
    state: PowerPolicyState,
    nowElapsedMillis: Long,
    inDoze: Boolean?,
    stateFresh: Boolean,
  ): FeatureDecision {
    fun result(allow: Boolean, reason: FeatureDecisionReason, next: String) = FeatureDecision(
      if (allow) FeatureDisposition.ALLOW else FeatureDisposition.DEFER,
      reason, state.sequence, next,
    )
    if (feature.importance == FeatureImportance.ESSENTIAL) {
      return result(true, FeatureDecisionReason.ESSENTIAL_OPPORTUNITY, "Existing permitted execution opportunity; check consent and platform eligibility.")
    }
    if (state.observedAtElapsedMillis == null || !state.batteryLevelAvailable) {
      return result(false, FeatureDecisionReason.STATE_UNAVAILABLE, "Next valid OS observation during an existing permitted opportunity.")
    }
    if (!stateFresh || nowElapsedMillis < state.observedAtElapsedMillis) {
      return result(false, FeatureDecisionReason.STALE_STATE, "Next current observation; do not add a wakeup.")
    }
    if (state.activeTier.ordinal >= AdaptivePowerTier.CRITICAL.ordinal) {
      return result(false, FeatureDecisionReason.MODE_RESTRICTION, "Next permitted opportunity after restrictions clear.")
    }
    if (inDoze != false) {
      return result(false, FeatureDecisionReason.DOZE_OR_UNKNOWN, "Next non-Doze opportunity with observed system state.")
    }
    if (feature.importance == FeatureImportance.DECORATIVE && (!state.isAppInForeground || !state.screenOn)) {
      return result(false, FeatureDecisionReason.NOT_VISIBLE, "Next visible foreground opportunity.")
    }
    return result(true, FeatureDecisionReason.MODE_WINDOW, "Current opportunity, subject to separate finite-budget and consent checks.")
  }
}
