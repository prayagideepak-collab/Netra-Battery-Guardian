package com.example.data.local.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.BatteryManager
import com.example.data.repository.BatteryHistoryRepository
import com.example.domain.model.batteryLevelText
import com.example.domain.model.telemetryText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CsvExportResult(
  val exported: Boolean,
  val file: File? = null,
  val skippedReason: String? = null,
  val healthRows: Int = 0,
  val snapshotRows: Int = 0,
  val activityRows: Int = 0,
)

@Singleton
class LocalReportExporter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val historyRepository: BatteryHistoryRepository,
) {
  private val reportMutex = Mutex()

  suspend fun clearPrivateReports() = reportMutex.withLock {
    File(context.filesDir, "NetraReports").deleteRecursively()
    Unit
  }

  private var logWriter: BoundedLogFileWriter? = null

  constructor(
    context: Context,
    historyRepository: BatteryHistoryRepository,
    logWriter: BoundedLogFileWriter,
  ) : this(context, historyRepository) {
    this.logWriter = logWriter
  }

  private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
  private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

  fun isDeviceCurrentlyCharging(intentOverride: Intent? = null): Boolean {
    val intent =
      intentOverride ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status =
      intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
      return true
    }
    if (intent != null && status != BatteryManager.BATTERY_STATUS_UNKNOWN) {
      return false
    }
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.isCharging == true
  }

  /**
   * Exports battery health (cycles, capacity, degradation) and temperature history logs
   * to a local CSV file inside app-private filesDir strictly when the device is currently charging.
   */
  suspend fun exportBatteryHealthAndTempCsvIfCharging(
    intentOverride: Intent? = null,
    lookbackDays: Int = 30,
  ): CsvExportResult =
    withContext(Dispatchers.IO) { reportMutex.withLock {
      if (!isDeviceCurrentlyCharging(intentOverride)) {
        logWriter?.append("CSV_EXPORT", "Skipped battery health & temperature CSV export: device is not charging.")
        return@withLock CsvExportResult(
          exported = false,
          skippedReason = "CSV export is only triggered while the device is charging.",
        )
      }

      val dir = getReportsDir()
      val file = File(dir, "netra_battery_health_history_${fileStampFormatter.format(Date())}.csv")
      val snapshots =
        historyRepository.getSnapshotsSince(
          System.currentTimeMillis() - lookbackDays.toLong() * 24L * 3600_000L,
        )
      val healthLogs = historyRepository.getBatteryHealthLogsForExport()
      val logs = historyRepository.getLogsForExport()

      val sb = StringBuilder()
      sb.appendLine("# Netra Battery Guardian - Offline Battery Health & Temperature History CSV (100% On-Device)")
      sb.appendLine("record_type,timestamp,level_percent,temp_c,voltage_mv,current_ma,power_watts,charging,thermal_level,cycle_count,design_capacity_mah,estimated_capacity_mah,degradation_percent,health_percent,source_or_profile,title,detail")

      healthLogs.forEach { h ->
        // Only current provenance-marked observations. Legacy guessed design/wear
        // records are never re-exported as measured battery health.
        if (h.sourceLabel.startsWith("OBSERVED_V2")) {
          val cycle = h.estimatedCycleCount.takeIf { it.isFinite() && it >= 0f }?.toString() ?: ""
          val capacity = h.estimatedCapacityMah.takeIf { it > 0 }?.toString() ?: ""
          sb.appendLine("HEALTH_LOG,${dateFormatter.format(Date(h.timestampMillis))},,,,,,,${cycle},,${capacity},,,${h.sourceLabel},,")
        }
      }

      snapshots.forEach { s ->
        sb.appendLine(
          "TEMP_SNAPSHOT,${dateFormatter.format(Date(s.timestampMillis))},${s.levelPercent.takeIf { it in 0..100 } ?: ""},${
            telemetryText(s.temperatureCelsius)
          },${s.voltageMv.takeIf { it > 0 } ?: ""},${telemetryText(s.currentMa, decimals = 0)},${
            telemetryText(s.powerWatts, decimals = 2)
          },${s.isCharging},${s.thermalLevel},,,,,,${s.activeProfile},,",
        )
      }

      logs.forEach { log ->
        val safeTitle = csvText(log.titleEn)
        val safeDetail = csvText(log.detailEn)
        sb.appendLine(
          "LOG_${log.category},${dateFormatter.format(Date(log.timestampMillis))},,,,,,,,,,,,,${log.severity},$safeTitle,$safeDetail",
        )
      }

      file.writeText(sb.toString())
      logWriter?.append(
        "CSV_EXPORT",
        "Exported ${file.name} while charging (${healthLogs.size} health rows, ${snapshots.size} temp rows).",
      )
      CsvExportResult(
        exported = true,
        file = file,
        healthRows = healthLogs.size,
        snapshotRows = snapshots.size,
        activityRows = logs.size,
      )
    } }

  suspend fun exportCsv(): File =
    withContext(Dispatchers.IO) { reportMutex.withLock {
      val dir = getReportsDir()
      val file = File(dir, "netra_battery_report_${fileStampFormatter.format(Date())}.csv")
      val snapshots = historyRepository.getSnapshotsSince(System.currentTimeMillis() - 7L * 24L * 3600_000L)
      val logs = historyRepository.getLogsForExport()

      val sb = StringBuilder()
      sb.appendLine("# Netra Battery Guardian - Offline Local Report (100% On-Device)")
      sb.appendLine("# Legacy records may contain old unverified defaults; they are history, not verified measurements.")
      sb.appendLine("type,timestamp,level_percent,temp_c,voltage_mv,current_ma,power_watts,charging,screen_on,profile,title,detail")

      snapshots.forEach { s ->
        sb.appendLine(
          "SNAPSHOT,${dateFormatter.format(Date(s.timestampMillis))},${s.levelPercent.takeIf { it in 0..100 } ?: ""},${
            telemetryText(s.temperatureCelsius)
          },${if (s.voltageMv > 0) s.voltageMv.toString() else ""},${telemetryText(s.currentMa, decimals = 0)},${
            telemetryText(s.powerWatts, decimals = 2)
          },${s.isCharging},${s.screenOn},${s.activeProfile},,"
        )
      }

      logs.forEach { log ->
        val safeTitle = csvText(log.titleEn)
        val safeDetail = csvText(log.detailEn)
        sb.appendLine(
          "LOG_${log.category},${dateFormatter.format(Date(log.timestampMillis))},,,,,,,,${log.severity},$safeTitle,$safeDetail"
        )
      }

      file.writeText(sb.toString())
      file
    } }

  suspend fun exportPdf(): File =
    withContext(Dispatchers.IO) { reportMutex.withLock {
      val dir = getReportsDir()
      val file = File(dir, "netra_battery_report_${fileStampFormatter.format(Date())}.pdf")
      val snapshots = historyRepository.getSnapshotsSince(System.currentTimeMillis() - 24L * 3600_000L)
      val chargingSessions = historyRepository.getChargingSessionsForExport()
      val logs = historyRepository.getLogsForExport().take(18)

      val document = PdfDocument()
      val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
      val page = document.startPage(pageInfo)
      val canvas = page.canvas

      val titlePaint = Paint().apply {
        color = Color.rgb(0, 121, 93)
        textSize = 18f
        isFakeBoldText = true
      }
      val headingPaint = Paint().apply {
        color = Color.BLACK
        textSize = 13f
        isFakeBoldText = true
      }
      val bodyPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 10.5f
      }

      var y = 44f
      canvas.drawText("Netra Battery Guardian — Offline Report", 36f, y, titlePaint)
      y += 20f
      canvas.drawText("Generated: ${dateFormatter.format(Date())} | Stored 100% locally", 36f, y, bodyPaint)
      y += 28f

      canvas.drawText("Last 24h Telemetry Summary (${snapshots.size} snapshots)", 36f, y, headingPaint)
      y += 18f
      if (snapshots.isNotEmpty()) {
        val avgTemp = snapshots.map { it.temperatureCelsius }.filter { it.isFinite() }.average().toFloat()
        val maxTemp = snapshots.map { it.temperatureCelsius }.filter { it.isFinite() }.maxOrNull() ?: Float.NaN
        val minLevel = snapshots.minOf { it.levelPercent }
        val maxLevel = snapshots.maxOf { it.levelPercent }
        canvas.drawText(
          "Level Range: $minLevel% - $maxLevel% | Avg Temp: ${
            telemetryText(avgTemp)
          }°C | Peak Temp: ${telemetryText(maxTemp)}°C",
          36f,
          y,
          bodyPaint,
        )
      } else {
        canvas.drawText("No snapshots recorded yet in the last 24 hours.", 36f, y, bodyPaint)
      }
      y += 26f

      canvas.drawText("Recent Charging Sessions (${chargingSessions.size})", 36f, y, headingPaint)
      y += 18f
      chargingSessions.take(6).forEach { cs ->
        canvas.drawText(
          "${dateFormatter.format(Date(cs.startTimeMillis))}: ${batteryLevelText(cs.startLevel)} -> ${batteryLevelText(cs.endLevel)} | ${cs.speedClass} (${
            telemetryText(cs.maxPowerWatts)
          } W max, ${telemetryText(cs.peakTempCelsius)}°C peak)",
          36f,
          y,
          bodyPaint,
        )
        y += 15f
      }
      y += 14f

      canvas.drawText("Activity (legacy entries are unverified)", 36f, y, headingPaint)
      y += 18f
      logs.forEach { log ->
        if (y < 800f) {
          val line = "[${log.severity}] ${log.titleEn}: ${log.detailEn}".take(92)
          canvas.drawText(line, 36f, y, bodyPaint)
          y += 15f
        }
      }

      document.finishPage(page)
      FileOutputStream(file).use { out ->
        document.writeTo(out)
      }
      document.close()
      file
    } }

  private fun csvText(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'" + value else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
  }

  private fun getReportsDir(): File {
    val base = context.filesDir
    val dir = File(base, "NetraReports")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }
}
