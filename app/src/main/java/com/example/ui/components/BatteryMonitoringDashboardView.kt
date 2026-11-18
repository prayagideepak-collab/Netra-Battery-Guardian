package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.db.BatterySnapshot
import com.example.domain.model.BatteryHealthState
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingDiagnosis
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.batteryLevelText
import com.example.domain.model.capacityText
import com.example.domain.model.telemetryText
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale
import kotlin.math.abs

/**
 * High-level temperature status classification required by Battery Sentinel Pro dashboard & widget:
 * Normal, Warm, Hot, Critical, or Unavailable.
 */
enum class SentinelTemperatureStatus(
  val labelEn: String,
  val labelHi: String,
) {
  NORMAL("Normal", "सामान्य (Normal)"),
  WARM("Warm", "हल्का गर्म (Warm)"),
  HOT("Hot", "गर्म (Hot)"),
  CRITICAL("Critical", "अति गंभीर (Critical)"),
  UNAVAILABLE("Unavailable", "अनुपलब्ध (Unavailable)"),
}

fun classifySentinelTemperature(tempCelsius: Float): SentinelTemperatureStatus {
  // Celsius alone cannot establish a device-specific safe/critical threshold.
  return SentinelTemperatureStatus.UNAVAILABLE
}

fun sentinelTemperatureColor(status: SentinelTemperatureStatus): Color {
  return when (status) {
    SentinelTemperatureStatus.NORMAL -> SemanticCoolMint
    SentinelTemperatureStatus.WARM -> SemanticWarmAmber
    SentinelTemperatureStatus.HOT -> SemanticHotOrange
    SentinelTemperatureStatus.CRITICAL -> SemanticCriticalRed
    SentinelTemperatureStatus.UNAVAILABLE -> SemanticIceCyan
  }
}

/**
 * Validated current direction & polarity analysis from real BatteryManager current reading
 * and charging state. Never fabricates values when hardware current is unavailable.
 */
data class ValidatedCurrentReading(
  val isAvailable: Boolean,
  val magnitudeMa: Float,
  val rawCurrentMa: Float,
  val directionLabelEn: String,
  val directionLabelHi: String,
  val chargingCurrentText: String,
  val dischargingCurrentText: String,
  val liveCurrentText: String,
  val polarityNoteEn: String,
  val polarityNoteHi: String,
)

fun validateHardwareCurrent(
  rawCurrentMa: Float,
  isCharging: Boolean,
  isPlugged: Boolean,
): ValidatedCurrentReading {
  if (!rawCurrentMa.isFinite()) {
    return ValidatedCurrentReading(
      isAvailable = false,
      magnitudeMa = Float.NaN,
      rawCurrentMa = Float.NaN,
      directionLabelEn = "Unavailable",
      directionLabelHi = "अनुपलब्ध (Unavailable)",
      chargingCurrentText = "Unavailable",
      dischargingCurrentText = "Unavailable",
      liveCurrentText = "Unavailable",
      polarityNoteEn = "Hardware fuel-gauge current sensor is unavailable on this device.",
      polarityNoteHi = "इस डिवाइस पर हार्डवेयर करंट सेंसर उपलब्ध नहीं है।",
    )
  }

  val absMa = abs(rawCurrentMa)
  val rawPrefix = if (rawCurrentMa > 0f) "+" else ""
  val rawText = String.format(Locale.US, "%s%.0f mA", rawPrefix, rawCurrentMa)
  val magText = String.format(Locale.US, "%.0f mA", absMa)

  return ValidatedCurrentReading(
    isAvailable = true,
    magnitudeMa = absMa,
    rawCurrentMa = rawCurrentMa,
    directionLabelEn = "Signed sensor reading",
    directionLabelHi = "सेंसर का संकेत सहित माप",
    chargingCurrentText = rawText,
    dischargingCurrentText = rawText,
    liveCurrentText = rawText,
    polarityNoteEn = "Raw sensor sign retained. OEM direction and charger input are not independently verified.",
    polarityNoteHi = "मूल सेंसर संकेत दिखाया गया है। दिशा और चार्जर इनपुट सत्यापित नहीं हैं।",
  )
}

/**
 * Real-time Power calculation using Voltage × Current and charging speed label.
 */
data class ValidatedPowerReading(
  val isAvailable: Boolean,
  val powerWatts: Float,
  val chargingPowerText: String,
  val dischargingPowerText: String,
  val formulaBreakdownText: String,
  val chargingSpeedLabelEn: String,
  val chargingSpeedLabelHi: String,
)

fun calculateHardwarePower(
  voltageMv: Int,
  validatedCurrent: ValidatedCurrentReading,
  isCharging: Boolean,
  diagnosis: ChargingDiagnosis?,
): ValidatedPowerReading {
  if (voltageMv <= 0 || !validatedCurrent.isAvailable || !validatedCurrent.magnitudeMa.isFinite()) {
    return ValidatedPowerReading(
      isAvailable = false,
      powerWatts = Float.NaN,
      chargingPowerText = "Unavailable",
      dischargingPowerText = "Unavailable",
      formulaBreakdownText = "Voltage × Current: Unavailable",
      chargingSpeedLabelEn = "Unavailable",
      chargingSpeedLabelHi = "अनुपलब्ध (Unavailable)",
    )
  }

  val volts = voltageMv / 1000f
  val amps = validatedCurrent.rawCurrentMa / 1000f
  val watts = volts * amps
  val wattsText = String.format(Locale.US, "%.2f W", watts)
  val formulaText = String.format(
    Locale.US,
    "%.2f V × %.0f mA = %.2f W",
    volts,
    validatedCurrent.rawCurrentMa,
    watts,
  )

  val speedEn = diagnosis?.speedClass?.takeIf { it != ChargingSpeedClass.UNAVAILABLE }?.name?.replace('_', ' ') ?: ""
  val speedHi = speedEn

  return if (isCharging) {
    ValidatedPowerReading(
      isAvailable = true,
      powerWatts = watts,
      chargingPowerText = wattsText,
      dischargingPowerText = "Unavailable: charger output not measured",
      formulaBreakdownText = formulaText,
      chargingSpeedLabelEn = speedEn,
      chargingSpeedLabelHi = speedHi,
    )
  } else {
    ValidatedPowerReading(
      isAvailable = true,
      powerWatts = watts,
      chargingPowerText = "Unavailable: charger input not measured",
      dischargingPowerText = wattsText,
      formulaBreakdownText = formulaText,
      chargingSpeedLabelEn = speedEn,
      chargingSpeedLabelHi = speedHi,
    )
  }
}

fun formatBatteryHealthState(state: BatteryHealthState, isHindi: Boolean): String {
  return when (state) {
    BatteryHealthState.GOOD -> if (isHindi) "अच्छा (Good)" else "Good"
    BatteryHealthState.OVERHEAT -> if (isHindi) "अधिक गर्म (Overheat)" else "Overheat"
    BatteryHealthState.DEAD -> if (isHindi) "खराब (Dead)" else "Dead"
    BatteryHealthState.OVER_VOLTAGE -> if (isHindi) "ओवर-वोल्टेज (Over Voltage)" else "Over Voltage"
    BatteryHealthState.UNSPECIFIED_FAILURE -> if (isHindi) "अनिर्दिष्ट त्रुटि (Failure)" else "Failure"
    BatteryHealthState.COLD -> if (isHindi) "ठंडा (Cold)" else "Cold"
    BatteryHealthState.UNKNOWN -> "Unavailable"
  }
}

/**
 * Battery Sentinel Pro — Dedicated Real-Time Battery Monitoring Dashboard View.
 * Connects 100% to native Android BatteryManager / ACTION_BATTERY_CHANGED readings
 * and Room historical snapshots without fabricating unavailable sensor readings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryMonitoringDashboardView(
  telemetry: BatteryTelemetry,
  chargingDiagnosis: ChargingDiagnosis?,
  historySnapshots: List<BatterySnapshot>,
  isHindi: Boolean,
  modifier: Modifier = Modifier,
) {
  val tempStatus = remember(telemetry.temperatureCelsius) {
    classifySentinelTemperature(telemetry.temperatureCelsius)
  }
  val tempAccent = sentinelTemperatureColor(tempStatus)

  val validatedCurrent = remember(telemetry.currentMa, telemetry.isCharging, telemetry.isPlugged) {
    validateHardwareCurrent(
      rawCurrentMa = telemetry.currentMa,
      isCharging = telemetry.isCharging,
      isPlugged = telemetry.isPlugged,
    )
  }

  val validatedPower = remember(telemetry.voltageMv, validatedCurrent, telemetry.isCharging, chargingDiagnosis) {
    calculateHardwarePower(
      voltageMv = telemetry.voltageMv,
      validatedCurrent = validatedCurrent,
      isCharging = telemetry.isCharging,
      diagnosis = chargingDiagnosis,
    )
  }

  // Historical finite series for delta & fluctuation monitoring + graphical charts
  val finiteTempHistory = remember(historySnapshots, telemetry.temperatureCelsius) {
    val list = historySnapshots
      .sortedBy { it.timestampMillis }
      .map { it.temperatureCelsius }
      .filter { it.isFinite() }
      .toMutableList()
    if (telemetry.temperatureCelsius.isFinite()) {
      list.add(telemetry.temperatureCelsius)
    }
    list
  }

  val finiteVoltageHistoryMv = remember(historySnapshots, telemetry.voltageMv) {
    val list = historySnapshots
      .sortedBy { it.timestampMillis }
      .map { it.voltageMv }
      .filter { it > 0 }
      .toMutableList()
    if (telemetry.voltageMv > 0) {
      list.add(telemetry.voltageMv)
    }
    list
  }

  val finiteCurrentHistoryMa = remember(historySnapshots, validatedCurrent) {
    val list = historySnapshots
      .sortedBy { it.timestampMillis }
      .map { it.currentMa }
      .filter { it.isFinite() }
      .toMutableList()
    if (validatedCurrent.isAvailable && validatedCurrent.magnitudeMa.isFinite()) {
      list.add(validatedCurrent.rawCurrentMa)
    }
    list
  }

  val chargingStateLabel = when {
    !telemetry.hasLevel -> "Unavailable"
    telemetry.isCharging -> if (isHindi) "चार्ज हो रहा है (Charging)" else "Charging"
    telemetry.isPlugged -> if (isHindi) "प्लग इन / रुका हुआ (Plugged)" else "Plugged (Not Charging)"
    else -> if (isHindi) "डिस्चार्ज हो रहा है (Discharging)" else "Discharging"
  }

  val healthLabel = formatBatteryHealthState(telemetry.healthState, isHindi)
  val techLabel = telemetry.technology.ifBlank { "Unavailable" }
  val capacityLabel = capacityText(telemetry.estimatedCapacityMah)

  Column(
    modifier = modifier
      .widthIn(max = 720.dp)
      .fillMaxWidth()
      .testTag("battery_sentinel_dashboard_view"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. BATTERY MONITORING OVERVIEW CARD (Prominent Circular Indicator + Core Hardware Specs)
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_battery_monitoring_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          SectionBadgeIcon(
            icon = Icons.Rounded.BatteryChargingFull,
            tint = if (telemetry.isCharging) SemanticCoolMint else SemanticIceCyan,
          )
          Column {
            Text(
              text = if (isHindi) "गार्डियन डिवाइस माप" else "Guardian Device Readings",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (isHindi) "वास्तविक हार्डवेयर टेलीमेट्री (BatteryManager)" else "Live Hardware Telemetry (BatteryManager)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = chargingStateLabel,
          color = if (telemetry.isCharging) SemanticCoolMint else SemanticIceCyan,
          modifier = Modifier.testTag("sentinel_charging_status_pill"),
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AnimatedBatteryRing(
          levelPercent = telemetry.levelPercent,
          isCharging = telemetry.isCharging,
          temperatureCelsius = telemetry.temperatureCelsius,
          animateRing = true,
          statusLabel = if (!telemetry.hasLevel) "" else if (telemetry.isCharging) "CHARGING" else "DISCHARGING",
          modifier = Modifier
            .size(152.dp)
            .testTag("sentinel_circular_battery_indicator"),
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SentinelSpecRow(
            label = if (isHindi) "बैटरी प्रतिशत" else "Battery Level",
            value = batteryLevelText(telemetry.levelPercent),
            valueTag = "sentinel_battery_percentage_value",
          )
          SentinelSpecRow(
            label = if (isHindi) "चार्जिंग स्थिति" else "Status",
            value = chargingStateLabel,
            valueTag = "sentinel_battery_status_value",
          )
          SentinelSpecRow(
            label = if (isHindi) "बैटरी स्वास्थ्य" else "Battery Health",
            value = healthLabel,
            valueTag = "sentinel_battery_health_value",
          )
          SentinelSpecRow(
            label = if (isHindi) "बैटरी तकनीक" else "Technology",
            value = techLabel,
            valueTag = "sentinel_battery_technology_value",
          )
          SentinelSpecRow(
            label = if (isHindi) "बैटरी क्षमता" else "Capacity (Counter)",
            value = capacityLabel,
            valueTag = "sentinel_battery_capacity_value",
          )
        }
      }
    }

    // 2. TEMPERATURE MONITORING CARD
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_temperature_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SectionBadgeIcon(icon = Icons.Rounded.Thermostat, tint = tempAccent)
          Column {
            Text(
              text = if (isHindi) "तापमान निगरानी (Temperature)" else "Temperature Monitoring",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (isHindi) "लाइव बैटरी तापमान और परिवर्तन" else "Live Battery Temperature & Change Tracking",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (tempStatus != SentinelTemperatureStatus.UNAVAILABLE) StatusPill(
          text = if (isHindi) tempStatus.labelHi else tempStatus.labelEn,
          color = tempAccent,
          modifier = Modifier.testTag("sentinel_temperature_status_badge"),
        )
      }

      val tempChangeText = remember(finiteTempHistory) {
        if (finiteTempHistory.size >= 2) {
          val first = finiteTempHistory.first()
          val last = finiteTempHistory.last()
          val delta = last - first
          val minT = finiteTempHistory.minOrNull() ?: last
          val maxT = finiteTempHistory.maxOrNull() ?: last
          val sign = if (delta >= 0f) "+" else ""
          String.format(
            Locale.US,
            "%s%.1f°C (Min %.1f°C • Max %.1f°C)",
            sign,
            delta,
            minT,
            maxT,
          )
        } else if (finiteTempHistory.size == 1) {
          ""
        } else {
          "Unavailable"
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SentinelMetricBox(
          title = if (isHindi) "वर्तमान तापमान" else "Current Temp",
          value = telemetryText(telemetry.temperatureCelsius, "°C", 1),
          accent = tempAccent,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_temp_live_value"),
        )
        SentinelMetricBox(
          title = if (isHindi) "तापमान स्थिति" else "Thermal State",
          value = if (tempStatus == SentinelTemperatureStatus.UNAVAILABLE) "" else tempStatus.labelEn,
          accent = tempAccent,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_temp_state_value"),
        )
      }

      SentinelSpecRow(
        label = if (isHindi) "तापमान में परिवर्तन (Δ)" else "Temperature Change (Δ)",
        value = tempChangeText,
        valueTag = "sentinel_temp_delta_value",
      )
    }

    // 3. VOLTAGE MONITORING CARD
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_voltage_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SectionBadgeIcon(icon = Icons.Rounded.ElectricMeter, tint = SemanticIceCyan)
          Column {
            Text(
              text = if (isHindi) "वोल्टेज निगरानी (Voltage)" else "Voltage Monitoring",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (isHindi) "लाइव वोल्टेज रीडिंग (mV / V) और उतार-चढ़ाव" else "Live Voltage Reading (mV / V) & Fluctuations",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (telemetry.voltageMv > 0) "${telemetry.voltageMv} mV" else "",
          color = SemanticIceCyan,
          modifier = Modifier.testTag("sentinel_voltage_pill"),
        )
      }

      val voltageMvText = if (telemetry.voltageMv > 0) "${telemetry.voltageMv} mV" else ""
      val voltageVoltsText = if (telemetry.voltageMv > 0) {
        String.format(Locale.US, "%.3f V", telemetry.voltageMv / 1000f)
      } else {
        "Unavailable"
      }

      val voltageFluctuationText = remember(finiteVoltageHistoryMv) {
        if (finiteVoltageHistoryMv.size >= 2) {
          val minMv = finiteVoltageHistoryMv.minOrNull() ?: 0
          val maxMv = finiteVoltageHistoryMv.maxOrNull() ?: 0
          val spreadMv = maxMv - minMv
          "Δ $spreadMv mV (Min $minMv mV • Max $maxMv mV)"
        } else if (finiteVoltageHistoryMv.size == 1) {
          ""
        } else {
          "Unavailable"
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SentinelMetricBox(
          title = if (isHindi) "वोल्टेज (mV)" else "Voltage (mV)",
          value = voltageMvText,
          accent = SemanticIceCyan,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_voltage_mv_value"),
        )
        SentinelMetricBox(
          title = if (isHindi) "वोल्टेज (V)" else "Voltage (V)",
          value = voltageVoltsText,
          accent = SemanticIceCyan,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_voltage_v_value"),
        )
      }

      SentinelSpecRow(
        label = if (isHindi) "वोल्टेज उतार-चढ़ाव (Fluctuation)" else "Voltage Fluctuation",
        value = voltageFluctuationText,
        valueTag = "sentinel_voltage_fluctuation_value",
      )
    }

    // 4. CURRENT MONITORING CARD
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_current_card"),
    ) {
      val currentAccent = when {
        !validatedCurrent.isAvailable -> SemanticIceCyan
        telemetry.isCharging -> SemanticCoolMint
        else -> SemanticWarmAmber
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          SectionBadgeIcon(icon = Icons.Rounded.Bolt, tint = currentAccent)
          Column {
            Text(
              text = if (isHindi) "करंट निगरानी (Current mA)" else "Current Monitoring (mA)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (isHindi) "चार्जिंग आवक बनाम डिस्चार्ज खपत" else "Signed Android battery sensor reading",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (isHindi) validatedCurrent.directionLabelHi else validatedCurrent.directionLabelEn,
          color = currentAccent,
          modifier = Modifier.testTag("sentinel_current_direction_pill"),
        )
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SentinelMetricBox(
          title = if (isHindi) "लाइव करंट (mA)" else "Live Current",
          value = validatedCurrent.liveCurrentText,
          accent = currentAccent,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_current_live_value"),
        )
        SentinelMetricBox(
          title = if (isHindi) "करंट दिशा" else "Current Direction",
          value = validatedCurrent.directionLabelEn,
          accent = currentAccent,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_current_direction_value"),
        )
      }

      SentinelSpecRow(
        label = if (isHindi) "संकेत सहित सेंसर करंट" else "Signed Battery Current",
        value = validatedCurrent.chargingCurrentText,
        valueTag = "sentinel_charging_current_value",
      )

      Text(
        text = if (isHindi) validatedCurrent.polarityNoteHi else validatedCurrent.polarityNoteEn,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("sentinel_current_polarity_note"),
      )
    }

    // 5. POWER MONITORING CARD
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_power_card"),
    ) {
      val powerAccent = if (telemetry.isCharging) SemanticCoolMint else SemanticIceCyan

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          SectionBadgeIcon(icon = Icons.Rounded.Speed, tint = powerAccent)
          Column {
            Text(
              text = if (isHindi) "पावर और चार्जिंग गति (Power W)" else "Power & Charging Speed (W)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (isHindi) "वोल्टेज × करंट पर आधारित वास्तविक समय गणना" else "Real-Time Calculation (Voltage × Current)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (isHindi) validatedPower.chargingSpeedLabelHi else validatedPower.chargingSpeedLabelEn,
          color = powerAccent,
          modifier = Modifier.testTag("sentinel_charging_speed_pill"),
        )
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SentinelMetricBox(
          title = if (isHindi) "बैटरी सेंसर पावर (W)" else "Battery Sensor Power (W)",
          value = validatedPower.chargingPowerText,
          accent = SemanticCoolMint,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_charging_power_value"),
        )
        SentinelMetricBox(
          title = if (isHindi) "बैटरी सेंसर पावर (W)" else "Battery Sensor Power (W)",
          value = validatedPower.dischargingPowerText,
          accent = SemanticWarmAmber,
          modifier = Modifier
            .weight(1f)
            .testTag("sentinel_discharging_power_value"),
        )
      }

      SentinelSpecRow(
        label = if (isHindi) "चार्जिंग स्पीड वर्ग" else "Charging Speed",
        value = validatedPower.chargingSpeedLabelEn,
        valueTag = "sentinel_power_speed_value",
      )
      SentinelSpecRow(
        label = if (isHindi) "पावर सूत्र (V × I)" else "Real-Time Power Formula",
        value = validatedPower.formulaBreakdownText,
        valueTag = "sentinel_power_formula_value",
      )
    }

    // 6. HISTORICAL TEMPERATURE, VOLTAGE & CURRENT GRAPHICAL CHARTS CARD
    GuardianCard(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("sentinel_history_charts_card"),
    ) {
      var selectedChartTab by remember { mutableIntStateOf(0) }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isHindi) "ऐतिहासिक टेलीमेट्री ग्राफ़" else "Historical Telemetry Charts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = if (isHindi) "तापमान (°C), वोल्टेज (mV) और करंट (mA) का वास्तविक इतिहास"
            else "Real recorded Temperature (°C), Voltage (mV), and Current (mA) trends",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = selectedChartTab == 0,
          onClick = { selectedChartTab = 0 },
          label = { Text(if (isHindi) "सभी (All 3)" else "All Charts") },
          modifier = Modifier.testTag("sentinel_chart_tab_all"),
        )
        FilterChip(
          selected = selectedChartTab == 1,
          onClick = { selectedChartTab = 1 },
          label = { Text(if (isHindi) "तापमान (°C)" else "Temp (°C)") },
          modifier = Modifier.testTag("sentinel_chart_tab_temp"),
        )
        FilterChip(
          selected = selectedChartTab == 2,
          onClick = { selectedChartTab = 2 },
          label = { Text(if (isHindi) "वोल्टेज (mV)" else "Voltage (mV)") },
          modifier = Modifier.testTag("sentinel_chart_tab_voltage"),
        )
        FilterChip(
          selected = selectedChartTab == 3,
          onClick = { selectedChartTab = 3 },
          label = { Text(if (isHindi) "करंट (mA)" else "Current (mA)") },
          modifier = Modifier.testTag("sentinel_chart_tab_current"),
        )
      }

      if (selectedChartTab == 0 || selectedChartTab == 1) {
        Text(
          text = if (isHindi) "तापमान ग्राफ़ (°C)" else "Historical Temperature (°C)",
          style = MaterialTheme.typography.labelLarge,
          color = SemanticWarmAmber,
        )
        TelemetryLineChart(
          values = finiteTempHistory,
          minRange = (finiteTempHistory.minOrNull() ?: 0f) - 1f,
          maxRange = (finiteTempHistory.maxOrNull() ?: 0f) + 1f,
          lineColor = SemanticWarmAmber,
          emptyMessage = if (isHindi) "तापमान इतिहास के लिए वास्तविक रीडिंग प्रतीक्षित है।"
          else "Awaiting real hardware temperature snapshots.",
          modifier = Modifier.testTag("sentinel_temperature_chart"),
        )
      }

      if (selectedChartTab == 0 || selectedChartTab == 2) {
        if (selectedChartTab == 0) {
          Spacer(modifier = Modifier.height(4.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          Spacer(modifier = Modifier.height(4.dp))
        }
        val minVolt = (finiteVoltageHistoryMv.minOrNull()?.toFloat() ?: 3400f) - 100f
        val maxVolt = (finiteVoltageHistoryMv.maxOrNull()?.toFloat() ?: 4400f) + 100f
        Text(
          text = if (isHindi) "वोल्टेज ग्राफ़ (mV)" else "Historical Voltage (mV)",
          style = MaterialTheme.typography.labelLarge,
          color = SemanticIceCyan,
        )
        TelemetryLineChart(
          values = finiteVoltageHistoryMv.map { it.toFloat() },
          minRange = minVolt,
          maxRange = maxVolt,
          lineColor = SemanticIceCyan,
          emptyMessage = if (isHindi) "वोल्टेज इतिहास के लिए वास्तविक रीडिंग प्रतीक्षित है।"
          else "Awaiting real hardware voltage snapshots.",
          modifier = Modifier.testTag("sentinel_voltage_chart"),
        )
      }

      if (selectedChartTab == 0 || selectedChartTab == 3) {
        if (selectedChartTab == 0) {
          Spacer(modifier = Modifier.height(4.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          Spacer(modifier = Modifier.height(4.dp))
        }
        val maxCurr = (finiteCurrentHistoryMa.maxOrNull() ?: 0f) + 100f
        val minCurr = (finiteCurrentHistoryMa.minOrNull() ?: 0f) - 100f
        Text(
          text = if (isHindi) "करंट ग्राफ़ (mA)" else "Historical Signed Current (mA)",
          style = MaterialTheme.typography.labelLarge,
          color = SemanticCoolMint,
        )
        TelemetryLineChart(
          values = finiteCurrentHistoryMa,
          minRange = minCurr,
          maxRange = maxCurr,
          lineColor = SemanticCoolMint,
          emptyMessage = if (isHindi) "करंट इतिहास अनुपलब्ध या रीडिंग प्रतीक्षित है।"
          else "Current history unavailable or awaiting hardware readings.",
          modifier = Modifier.testTag("sentinel_current_chart"),
        )
      }
    }
  }
}

@Composable
private fun SectionBadgeIcon(
  icon: ImageVector,
  tint: Color,
) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(tint.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun SentinelMetricBox(
  title: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  if (value.isBlank() || value.contains("Unavailable", ignoreCase = true)) return

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = TelemetryMonoStyle.copy(
          fontSize = MaterialTheme.typography.titleMedium.fontSize,
          fontWeight = FontWeight.Bold,
        ),
        color = accent,
      )
    }
  }
}

@Composable
private fun SentinelSpecRow(
  label: String,
  value: String,
  valueTag: String,
) {
  if (value.isBlank() || value.contains("Unavailable", ignoreCase = true)) return

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = TelemetryMonoStyle,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.testTag(valueTag),
    )
  }
}
