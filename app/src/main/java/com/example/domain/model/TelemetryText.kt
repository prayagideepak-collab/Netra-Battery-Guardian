package com.example.domain.model

import java.util.Locale

/** Keep unknown sentinels out of cards, notifications and exports. */
fun telemetryText(value: Float, unit: String = "", decimals: Int = 1): String =
  if (value.isFinite()) String.format(Locale.US, "%.${decimals}f", value) + unit else "Unavailable"

fun batteryLevelText(value: Int): String = if (value in 0..100) "$value%" else "Unavailable"
fun capacityText(value: Int): String = if (value > 0) "Approx. $value mAh" else "Unavailable"
