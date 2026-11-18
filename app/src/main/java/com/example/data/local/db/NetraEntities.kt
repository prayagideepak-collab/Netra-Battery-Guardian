package com.example.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "charging_sessions",
  indices = [Index(value = ["startTimeMillis"])],
)
data class ChargingSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startTimeMillis: Long,
  val endTimeMillis: Long? = null,
  val startLevel: Int,
  val endLevel: Int,
  val peakTempCelsius: Float,
  val avgPowerWatts: Float,
  val maxPowerWatts: Float,
  val speedClass: String,
  val plugType: String,
)

@Entity(
  tableName = "discharge_sessions",
  indices = [Index(value = ["startTimeMillis"])],
)
data class DischargeSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startTimeMillis: Long,
  val endTimeMillis: Long? = null,
  val startLevel: Int,
  val endLevel: Int,
  val screenOnMinutes: Int,
  val screenOffMinutes: Int,
  val minTempCelsius: Float,
  val maxTempCelsius: Float,
  val abnormalDrainDetected: Boolean = false,
)

@Entity(
  tableName = "activity_logs",
  indices = [Index(value = ["timestampMillis"])],
)
data class ActivityLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMillis: Long,
  val category: String,
  val severity: String,
  val titleEn: String,
  val titleHi: String,
  val detailEn: String,
  val detailHi: String,
  val whyEn: String,
  val whyHi: String,
)

@Entity(
  tableName = "battery_health_logs",
  indices = [Index(value = ["dayBucket"], unique = true), Index(value = ["timestampMillis"])],
)
data class BatteryHealthLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dayBucket: Long,
  val timestampMillis: Long,
  val estimatedCycleCount: Float,
  val designCapacityMah: Int,
  val estimatedCapacityMah: Int,
  val degradationPercent: Float,
  val healthPercent: Float,
  val sourceLabel: String,
)

@Entity(
  tableName = "battery_health_logs",
  indices = [Index(value = ["dayBucket"], unique = true), Index(value = ["timestampMillis"])],
)
data class BatteryHealthLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dayBucket: Long,
  val timestampMillis: Long,
  val estimatedCycleCount: Float,
  val designCapacityMah: Int,
  val estimatedCapacityMah: Int,
  val degradationPercent: Float,
  val healthPercent: Float,
  val sourceLabel: String,
)
