package com.example.data.local.system

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.repository.BatteryHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReportExporter @Inject constructor(
  @ApplicationContext private val context: Context,
  private val historyRepository: BatteryHistoryRepository,
) {
  private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
  private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

  suspend fun exportCsv(): File =
    withContext(Dispatchers.IO) {
      val dir = getReportsDir()
      val file = File(dir, "netra_battery_report_${fileStampFormatter.format(Date())}.csv")
      val snapshots = historyRepository.getSnapshotsSince(System.currentTimeMillis() - 7L * 24L * 3600_000L)
      val logs = historyRepository.getLogsForExport()

      val sb = StringBuilder()
      sb.appendLine("# Netra Battery Guardian - Offline Local Report (100% On-Device)")
      sb.appendLine("type,timestamp,level_percent,temp_c,voltage_mv,current_ma,power_watts,charging,screen_on,profile,title,detail")

      snapshots.forEach { s ->
        sb.appendLine(
          "SNAPSHOT,${dateFormatter.format(Date(s.timestampMillis))},${s.levelPercent},${
            String.format(Locale.US, "%.1f", s.temperatureCelsius)
          },${s.voltageMv},${String.format(Locale.US, "%.0f", s.currentMa)},${
            String.format(Locale.US, "%.2f", s.powerWatts)
          },${s.isCharging},${s.screenOn},${s.activeProfile},,"
        )
      }

      logs.forEach { log ->
        val safeTitle = log.titleEn.replace(",", ";")
        val safeDetail = log.detailEn.replace(",", ";")
        sb.appendLine(
          "LOG_${log.category},${dateFormatter.format(Date(log.timestampMillis))},,,,,,,,${log.severity},$safeTitle,$safeDetail"
        )
      }

      file.writeText(sb.toString())
      file
    }

  suspend fun exportPdf(): File =
    withContext(Dispatchers.IO) {
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
        val avgTemp = snapshots.map { it.temperatureCelsius }.average()
        val maxTemp = snapshots.maxOf { it.temperatureCelsius }
        val minLevel = snapshots.minOf { it.levelPercent }
        val maxLevel = snapshots.maxOf { it.levelPercent }
        canvas.drawText(
          "Level Range: $minLevel% - $maxLevel% | Avg Temp: ${
            String.format(Locale.US, "%.1f", avgTemp)
          }°C | Peak Temp: ${String.format(Locale.US, "%.1f", maxTemp)}°C",
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
          "${dateFormatter.format(Date(cs.startTimeMillis))}: ${cs.startLevel}% -> ${cs.endLevel}% | ${cs.speedClass} (${
            String.format(Locale.US, "%.1f", cs.maxPowerWatts)
          } W max, ${String.format(Locale.US, "%.1f", cs.peakTempCelsius)}°C peak)",
          36f,
          y,
          bodyPaint,
        )
        y += 15f
      }
      y += 14f

      canvas.drawText("Recent Guardian Activity Log", 36f, y, headingPaint)
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
    }

  private fun getReportsDir(): File {
    val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val dir = File(base, "NetraReports")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }
}
