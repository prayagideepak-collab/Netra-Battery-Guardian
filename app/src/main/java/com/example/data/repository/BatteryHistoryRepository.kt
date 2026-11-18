package com.example.data.repository

import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.BatteryDao
import com.example.data.local.db.BatteryHealthLogEntity
import com.example.data.local.db.BatterySnapshot
import com.example.data.local.db.ChargingSessionEntity
import com.example.data.local.db.DischargeSessionEntity
import com.example.data.local.db.NetraDao
import com.example.domain.model.BatteryTelemetry
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.GuardianProfile
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryHistoryRepository @Inject constructor(
  private val dao: NetraDao,
  private val batteryDao: BatteryDao,
) {
  private fun knownMax(a: Float, b: Float) = listOf(a, b).filter { it.isFinite() }.maxOrNull() ?: Float.NEGATIVE_INFINITY
  private fun knownMin(a: Float, b: Float) = listOf(a, b).filter { it.isFinite() }.minOrNull() ?: Float.NEGATIVE_INFINITY

  fun observe24HourSnapshots(nowMillis: Long = System.currentTimeMillis()): Flow<List<BatterySnapshot>> =
    dao.observeSnapshotsSince(nowMillis - 24L * 3600_000L)

  fun observe7DaySnapshots(nowMillis: Long = System.currentTimeMillis()): Flow<List<BatterySnapshot>> =
    dao.observeSnapshotsSince(nowMillis - 7L * 24L * 3600_000L)

  fun observeHourlySnapshots(): Flow<List<BatterySnapshot>> =
    batteryDao.observeHourlySnapshots()

  fun observeSnapshotsTodayCount(startOfDayMillis: Long): Flow<Int> =
    dao.observeSnapshotCountSince(startOfDayMillis)

  fun observeRecentChargingSessions(): Flow<List<ChargingSessionEntity>> =
    dao.observeRecentChargingSessions()

  fun observeRecentDischargeSessions(): Flow<List<DischargeSessionEntity>> =
    dao.observeRecentDischargeSessions()

  fun observeActivityLogs(): Flow<List<ActivityLogEntity>> = dao.observeActivityLogs()

  fun observeBatteryHealthLogs(): Flow<List<BatteryHealthLogEntity>> = dao.observeBatteryHealthLogs()

  suspend fun recordSnapshot(
    telemetry: BatteryTelemetry,
    activeProfile: GuardianProfile,
    isHourlyCheckpoint: Boolean = false,
  ) {
    if (!telemetry.hasSnapshot) return
    val latestHourly = batteryDao.getLatestHourlySnapshot()
    val shouldMarkHourly =
      isHourlyCheckpoint ||
        latestHourly == null ||
        (telemetry.timestampMillis - latestHourly.timestampMillis >= 3_600_000L)
    val statusStr =
      when {
        telemetry.levelPercent >= 100 && telemetry.isPlugged -> "FULL"
        telemetry.isCharging -> "CHARGING"
        telemetry.isPlugged -> "NOT_CHARGING"
        else -> "DISCHARGING"
      }
    dao.insertSnapshot(
      BatterySnapshot(
        timestampMillis = telemetry.timestampMillis,
        levelPercent = telemetry.levelPercent,
        voltageMv = telemetry.voltageMv,
        temperatureCelsius = telemetry.temperatureCelsius.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        currentMa = telemetry.currentMa.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        powerWatts = telemetry.instantaneousPowerWatts.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
        isCharging = telemetry.isCharging,
        chargingStatus = statusStr,
        isPlugged = telemetry.isPlugged,
        plugType = telemetry.plugType.name,
        screenOn = telemetry.screenOn,
        thermalLevel = telemetry.thermalLevel.name,
        activeProfile = activeProfile.name,
        isHourlyCheckpoint = shouldMarkHourly,
      )
    )
  }

  suspend fun startOrUpdateChargingSession(
    telemetry: BatteryTelemetry,
    speedClass: ChargingSpeedClass,
    powerWatts: Float,
  ) {
    if (!telemetry.hasLevel) return
    val open = dao.getOpenChargingSession()
    if (open == null) {
      // Close any open discharge session
      closeOpenDischargeSession(telemetry)
      dao.insertChargingSession(
        ChargingSessionEntity(
          startTimeMillis = telemetry.timestampMillis,
          endTimeMillis = null,
          startLevel = telemetry.levelPercent,
          endLevel = telemetry.levelPercent,
          peakTempCelsius = telemetry.temperatureCelsius.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
          avgPowerWatts = powerWatts.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
          maxPowerWatts = powerWatts.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
          speedClass = speedClass.name,
          plugType = telemetry.plugType.name,
        )
      )
    } else {
      dao.updateChargingSession(
        open.copy(
          endLevel = telemetry.levelPercent,
          peakTempCelsius = knownMax(open.peakTempCelsius, telemetry.temperatureCelsius),
          avgPowerWatts = if (open.avgPowerWatts.isFinite() && powerWatts.isFinite()) (open.avgPowerWatts + powerWatts) / 2f else knownMax(open.avgPowerWatts, powerWatts),
          maxPowerWatts = knownMax(open.maxPowerWatts, powerWatts),
          speedClass = speedClass.name,
        )
      )
    }
  }

  suspend fun closeOpenChargingSession(telemetry: BatteryTelemetry) {
    val open = dao.getOpenChargingSession() ?: return
    dao.updateChargingSession(
      open.copy(
        endTimeMillis = telemetry.timestampMillis,
        endLevel = if (telemetry.hasLevel) telemetry.levelPercent else -1,
        peakTempCelsius = knownMax(open.peakTempCelsius, telemetry.temperatureCelsius),
      )
    )
  }

  suspend fun startOrUpdateDischargeSession(
    telemetry: BatteryTelemetry,
    screenOnDeltaMin: Int = 0,
    screenOffDeltaMin: Int = 0,
    abnormalDrain: Boolean = false,
  ) {
    if (!telemetry.hasLevel) return
    val open = dao.getOpenDischargeSession()
    if (open == null) {
      closeOpenChargingSession(telemetry)
      dao.insertDischargeSession(
        DischargeSessionEntity(
          startTimeMillis = telemetry.timestampMillis,
          endTimeMillis = null,
          startLevel = telemetry.levelPercent,
          endLevel = telemetry.levelPercent,
          screenOnMinutes = screenOnDeltaMin,
          screenOffMinutes = screenOffDeltaMin,
          minTempCelsius = telemetry.temperatureCelsius.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
          maxTempCelsius = telemetry.temperatureCelsius.takeIf { it.isFinite() } ?: Float.NEGATIVE_INFINITY,
          abnormalDrainDetected = abnormalDrain,
        )
      )
    } else {
      dao.updateDischargeSession(
        open.copy(
          endLevel = telemetry.levelPercent,
          screenOnMinutes = open.screenOnMinutes + screenOnDeltaMin,
          screenOffMinutes = open.screenOffMinutes + screenOffDeltaMin,
          minTempCelsius = knownMin(open.minTempCelsius, telemetry.temperatureCelsius),
          maxTempCelsius = knownMax(open.maxTempCelsius, telemetry.temperatureCelsius),
          abnormalDrainDetected = open.abnormalDrainDetected || abnormalDrain,
        )
      )
    }
  }

  suspend fun closeOpenDischargeSession(telemetry: BatteryTelemetry) {
    val open = dao.getOpenDischargeSession() ?: return
    dao.updateDischargeSession(
      open.copy(
        endTimeMillis = telemetry.timestampMillis,
        endLevel = if (telemetry.hasLevel) telemetry.levelPercent else -1,
        minTempCelsius = knownMin(open.minTempCelsius, telemetry.temperatureCelsius),
        maxTempCelsius = knownMax(open.maxTempCelsius, telemetry.temperatureCelsius),
      )
    )
  }

  suspend fun logActivity(
    category: LogCategory,
    severity: LogSeverity,
    titleEn: String,
    titleHi: String,
    detailEn: String,
    detailHi: String,
    whyEn: String,
    whyHi: String,
  ) {
    dao.insertActivityLog(
      ActivityLogEntity(
        timestampMillis = System.currentTimeMillis(),
        category = category.name,
        severity = severity.name,
        titleEn = titleEn,
        titleHi = titleHi,
        detailEn = detailEn,
        detailHi = detailHi,
        whyEn = whyEn,
        whyHi = whyHi,
      )
    )
  }

  suspend fun getSnapshotsSince(sinceMillis: Long): List<BatterySnapshot> =
    dao.getSnapshotsSince(sinceMillis)

  suspend fun getLogsForExport(): List<ActivityLogEntity> = dao.getAllLogsForExport()

  suspend fun getChargingSessionsForExport(): List<ChargingSessionEntity> =
    dao.getAllChargingSessionsForExport()

  suspend fun getDischargeSessionsForExport(): List<DischargeSessionEntity> =
    dao.getAllDischargeSessionsForExport()

  suspend fun pruneOldData(retentionDays: Int) {
    val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24L * 3600_000L
    dao.deleteSnapshotsOlderThan(cutoff)
    dao.deleteChargingSessionsOlderThan(cutoff)
    dao.deleteDischargeSessionsOlderThan(cutoff)
    dao.deleteLogsOlderThan(cutoff)
    dao.deleteBatteryHealthLogsOlderThan(cutoff)
  }

  suspend fun clearAllLocalData() {
    dao.clearSnapshots()
    dao.clearChargingSessions()
    dao.clearDischargeSessions()
    dao.clearActivityLogs()
    dao.clearBatteryHealthLogs()
  }
}
