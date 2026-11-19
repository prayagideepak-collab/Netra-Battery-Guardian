package com.example.domain.engine

import com.example.domain.model.FeatureImportance
import com.example.domain.model.PowerFeature

/** No finite measured budget has been approved. This is not a numeric placeholder. */
object MeasuredPowerBudget {
  const val DEFERRED_MESSAGE = "Optional work deferred: power budget not verified. No changes made."

  fun permits(feature: PowerFeature): Boolean = feature.importance == FeatureImportance.ESSENTIAL

  fun requireOptionalBudget(feature: PowerFeature) {
    check(permits(feature)) { DEFERRED_MESSAGE }
  }
}
