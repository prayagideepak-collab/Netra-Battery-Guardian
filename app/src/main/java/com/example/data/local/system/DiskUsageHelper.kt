package com.example.data.local.system

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AppStorageBreakdown(
  val packageName: String,
  val appName: String,
  val appBytes: Long,
  val dataBytes: Long,
  val cacheBytes: Long,
) {
  val totalBytes: Long
    get() = appBytes + dataBytes + cacheBytes
}

data class HeavyAppFile(
  val name: String,
  val path: String,
  val sizeBytes: Long,
  val directoryKind: String,
)

data class DiskUsageReport(
  val scannedAtMillis: Long,
  val executedDuringIdleWindow: Boolean,
  val skippedReason: String? = null,
  val selfAppBytes: Long = 0L,
  val selfDataBytes: Long = 0L,
  val selfCacheBytes: Long = 0L,
  val heavyAppFiles: List<HeavyAppFile> = emptyList(),
  val heavyApps: List<AppStorageBreakdown> = emptyList(),
)

/**
 * Scans app-specific storage usage via [StorageStatsManager] and app-private directories
 * to identify memory/storage-heavy files and oversized app caches.
 *
 * Strictly enforces idle-maintenance-window execution so disk traversal and package stat
 * queries never run while the user is actively using the phone or when battery/thermals are stressed.
 */
@Singleton
class DiskUsageHelper @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {

  private val _latestReport = MutableStateFlow<DiskUsageReport?>(null)
  val latestReport: StateFlow<DiskUsageReport?> = _latestReport.asStateFlow()

  fun isDeviceIdleForMaintenance(): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isDeviceIdleMode || !pm.isInteractive
  }

  /**
   * Executes an app-specific storage scan using [StorageStatsManager] ONLY during an idle
   * maintenance window (either invoked from an idle-constrained WorkManager job or when
   * [PowerManager.isDeviceIdleMode] / screen-off idle is active) and while battery/temperature are safe.
   */
  fun scanAppStorageIfIdle(
    fromIdleWorker: Boolean = false,
    temperatureCelsius: Float = 30f,
    batteryPercent: Int = 50,
    heavyFileThresholdBytes: Long = DEFAULT_HEAVY_FILE_THRESHOLD_BYTES,
  ): DiskUsageReport {
    val now = System.currentTimeMillis()
    if (!fromIdleWorker && !isDeviceIdleForMaintenance()) {
      return DiskUsageReport(
        scannedAtMillis = now,
        executedDuringIdleWindow = false,
        skippedReason = "Skipped: DiskUsageHelper executes only during idle maintenance windows.",
      )
    }

    if (temperatureCelsius >= 40f || batteryPercent < 15) {
      return DiskUsageReport(
        scannedAtMillis = now,
        executedDuringIdleWindow = false,
        skippedReason = "Postponed: Battery <15% or temperature >=40°C safety threshold.",
      )
    }

    var selfAppBytes = 0L
    var selfDataBytes = 0L
    var selfCacheBytes = 0L
    val heavyApps = mutableListOf<AppStorageBreakdown>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
      if (ssm != null) {
        val user = Process.myUserHandle()
        val ownStats = runCatching {
          ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, user)
        }.getOrNull()

        if (ownStats != null) {
          selfAppBytes = ownStats.appBytes
          selfDataBytes = ownStats.dataBytes
          selfCacheBytes = ownStats.cacheBytes
        }

        if (hasUsageStatsPermission()) {
          val pm = context.packageManager
          val apps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
              @Suppress("DEPRECATION")
              pm.getInstalledApplications(0)
            }
          }.getOrDefault(emptyList())

          // Inspect non-system apps capped at 25 entries per idle window to keep CPU low
          apps.asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .take(25)
            .forEach { appInfo ->
              val pkgStats = runCatching {
                ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, appInfo.packageName, user)
              }.getOrNull()
              if (pkgStats != null && (pkgStats.dataBytes + pkgStats.cacheBytes) >= heavyFileThresholdBytes) {
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                  .getOrDefault(appInfo.packageName)
                heavyApps += AppStorageBreakdown(
                  packageName = appInfo.packageName,
                  appName = label,
                  appBytes = pkgStats.appBytes,
                  dataBytes = pkgStats.dataBytes,
                  cacheBytes = pkgStats.cacheBytes,
                )
              }
            }
        }
      }
    }

    val heavyFiles = mutableListOf<HeavyAppFile>()
    collectHeavyFiles(context.filesDir, "files", heavyFileThresholdBytes, heavyFiles)
    collectHeavyFiles(context.cacheDir, "cache", heavyFileThresholdBytes, heavyFiles)
    context.getExternalFilesDir(null)?.let {
      collectHeavyFiles(it, "external_files", heavyFileThresholdBytes, heavyFiles)
    }
    context.externalCacheDir?.let {
      collectHeavyFiles(it, "external_cache", heavyFileThresholdBytes, heavyFiles)
    }

    val report = DiskUsageReport(
      scannedAtMillis = now,
      executedDuringIdleWindow = true,
      skippedReason = null,
      selfAppBytes = selfAppBytes,
      selfDataBytes = selfDataBytes,
      selfCacheBytes = selfCacheBytes,
      heavyAppFiles = heavyFiles.sortedByDescending { it.sizeBytes }.take(20),
      heavyApps = heavyApps.sortedByDescending { it.totalBytes }.take(10),
    )
    _latestReport.value = report
    return report
  }

  private fun collectHeavyFiles(
    root: File?,
    kind: String,
    thresholdBytes: Long,
    out: MutableList<HeavyAppFile>,
    depth: Int = 0,
  ) {
    if (root == null || !root.exists() || depth > 4) return
    if (root.isFile) {
      val len = root.length()
      if (len >= thresholdBytes) {
        out += HeavyAppFile(
          name = root.name,
          path = root.absolutePath,
          sizeBytes = len,
          directoryKind = kind,
        )
      }
      return
    }
    root.listFiles()?.forEach { child ->
      collectHeavyFiles(child, kind, thresholdBytes, out, depth + 1)
    }
  }

  private fun hasUsageStatsPermission(): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
      } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
      }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  companion object {
    const val DEFAULT_HEAVY_FILE_THRESHOLD_BYTES: Long = 1024L * 1024L // 1 MB
  }
}
