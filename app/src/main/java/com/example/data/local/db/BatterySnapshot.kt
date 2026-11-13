package com.example.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "battery_snapshots",
  indices = [Index(value = ["timestampMillis"])],
)
data class BatterySnapshot(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMillis: Long,
  val levelPercent: Int,
  val voltageMv: Int,
  val temperatureCelsius: Float,
  val currentMa: Float = 0f,
  val powerWatts: Float = 0f,
  val isCharging: Boolean,
  val chargingStatus: String = "UNKNOWN",
  val isPlugged: Boolean = false,
  val plugType: String = "NONE",
  val screenOn: Boolean = true,
  val thermalLevel: String = "NONE",
  val activeProfile: String = "NORMAL",
  val isHourlyCheckpoint: Boolean = false,
)
