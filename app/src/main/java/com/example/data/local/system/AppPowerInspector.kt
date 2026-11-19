package com.example.data.local.system

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.example.domain.model.AppPowerInsight
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPowerInspector @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val pm: PackageManager = context.packageManager
  private val powerManager: PowerManager? =
    context.getSystemService(Context.POWER_SERVICE) as? PowerManager

  fun hasUsageStatsPermission(): Boolean = runCatching { checkUsageStatsPermission() }.getOrDefault(false)

  private fun checkUsageStatsPermission(): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  private var cachedApps: List<AppPowerInsight> = emptyList()
  private var lastQueryElapsed = -1L
  internal var usageCooldownMillis = 5 * 60_000L

  @Synchronized
  fun collectTopApps(limit: Int = 12): List<AppPowerInsight> {
    if (!hasUsageStatsPermission()) {
      cachedApps = emptyList()
      lastQueryElapsed = -1L
      return emptyList()
    }
    val now = android.os.SystemClock.elapsedRealtime()
    if (lastQueryElapsed >= 0L && now - lastQueryElapsed in 0 until usageCooldownMillis) return cachedApps.take(limit.coerceAtLeast(0))
    lastQueryElapsed = now
    cachedApps = runCatching { queryTopApps() }.getOrDefault(emptyList())
    return cachedApps.take(limit.coerceAtLeast(0))
  }

  private fun queryTopApps(): List<AppPowerInsight> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      ?: return emptyList()

    val end = System.currentTimeMillis()
    val start = end - 24L * 3600_000L
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
      ?: return emptyList()

    val aggregated =
      stats
        .filter { it.packageName != context.packageName && it.totalTimeInForeground > 60_000L }
        .groupBy { it.packageName }
        .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
        .entries
        .sortedByDescending { it.value }
        .take(12)

    val totalForegroundMs = aggregated.sumOf { it.value }.coerceAtLeast(1L)

    return aggregated.mapIndexedNotNull { index, (pkg, fgMs) ->
      val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapIndexedNotNull null
      val appName = pm.getApplicationLabel(appInfo).toString()
      val fgMinutes = fgMs / 60_000L
      val sharePercent = ((fgMs * 100f) / totalForegroundMs).coerceIn(0f, 100f)

      val hasLoc =
        pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, pkg) == PackageManager.PERMISSION_GRANTED ||
          pm.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, pkg) == PackageManager.PERMISSION_GRANTED

      val isOpt = powerManager?.isIgnoringBatteryOptimizations(pkg)?.not() ?: true
      val isGame =
        (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
          (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME)

      val (recEn, recHi) =
        when {
          !isOpt ->
            "Enable Battery Optimization in Android App Info to curb background wake-ups." to
              "बैकग्राउंड खपत कम करने के लिए ऐप जानकारी में बैटरी ऑप्टिमाइज़ेशन चालू करें।"
          isGame && fgMinutes >= 30 ->
            "High gaming screen/GPU time. Take cooling breaks and lower brightness." to
              "गेमिंग में ज़्यादा उपयोग। तापमान कम रखने के लिए बीच-बीच में ब्रेक लें।"
          hasLoc && fgMinutes >= 15 ->
            "Has location permission; actual GPS activity is not measured. Set location access to 'While using the app' in App Info." to
              "इस ऐप को लोकेशन की अनुमति है; वास्तविक GPS उपयोग मापा नहीं गया। सेटिंग में इसे केवल 'ऐप उपयोग करते समय' पर रखें।"
          else ->
            "Review background activity or restrict notifications if unused." to
              "यदि आवश्यक न हो तो बैकग्राउंड गतिविधि या अतिरिक्त नोटिफ़िकेशन सीमित करें।"
        }

      AppPowerInsight(
        packageName = pkg,
        appName = appName,
        foregroundMinutesToday = fgMinutes,
        estimatedWakeups = -1,
        batteryRank = index + 1,
        estimatedDrainSharePercent = sharePercent,
        hasLocationPermission = hasLoc,
        isBatteryOptimized = isOpt,
        isLikelyGame = isGame,
        recommendedActionEn = recEn,
        recommendedActionHi = recHi,
        whyEn = "Ranked from Android UsageStats foreground duration and permission profile over the last 24h.",
        whyHi = "पिछले 24 घंटों में Android UsageStats उपयोग समय और लोकेशन/बैटरी सेटिंग्स के आधार पर रैंक किया गया।",
      )
    }
  }

  fun isSelfBatteryOptimized(): Boolean =
    powerManager?.isIgnoringBatteryOptimizations(context.packageName)?.not() ?: true

  fun buildUsageAccessIntent(): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun buildAppInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun buildAppNotificationSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      buildAppInfoIntent(packageName)
    }

  fun buildBatteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun buildBatterySaverSettingsIntent(): Intent =
    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun buildLocationSettingsIntent(): Intent =
    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun buildDataSaverSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

  fun buildWriteSettingsIntent(): Intent =
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
