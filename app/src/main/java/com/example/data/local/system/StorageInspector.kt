package com.example.data.local.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.domain.model.StorageActionTarget
import com.example.domain.model.StorageInsight
import com.example.domain.model.StorageRecommendation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageInspector @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun inspect(temperatureCelsius: Float, batteryPercent: Int): StorageInsight {
    val statFs = StatFs(Environment.getDataDirectory().path)
    val totalBytes = statFs.totalBytes.coerceAtLeast(1L)
    val freeBytes = statFs.availableBytes.coerceAtLeast(0L)
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val freePercent = ((freeBytes * 100.0) / totalBytes).toFloat().coerceIn(0f, 100f)

    // Safety guard: postpone heavy directory scans when hot (>=40°C) or battery <15%
    val postponeReason =
      when {
        temperatureCelsius >= 40f ->
          "Heavy storage scan postponed because battery temperature is ${String.format("%.1f", temperatureCelsius)}°C (>= 40°C)." to
            "तापमान ${String.format("%.1f", temperatureCelsius)}°C (>= 40°C) होने के कारण गहन स्टोरेज स्कैन रोका गया है।"
        batteryPercent < 15 ->
          "Heavy storage scan postponed because battery is below 15% ($batteryPercent%)." to
            "बैटरी 15% से कम ($batteryPercent%) होने के कारण गहन स्टोरेज स्कैन रोका गया है।"
        else -> null
      }

    val ownCacheBytes =
      directorySize(context.cacheDir) + (context.externalCacheDir?.let { directorySize(it) } ?: 0L)

    val recommendations = mutableListOf<StorageRecommendation>()

    val ownCacheMb = (ownCacheBytes / (1024 * 1024)).toInt()
    recommendations +=
      StorageRecommendation(
        id = "app_own_cache",
        titleEn = "Netra App Cache (${formatBytes(ownCacheBytes)})",
        titleHi = "नेत्रा ऐप कैश (${formatBytes(ownCacheBytes)})",
        descriptionEn = "Safe to clean immediately. Netra only touches its own temporary cache files.",
        descriptionHi = "तुरंत साफ़ करने के लिए सुरक्षित। नेत्रा केवल अपनी अस्थायी कैश फ़ाइलें ही हटाता है।",
        whyEn = "Auto-cleans when exceeding 100 MB; never touches your photos, videos, or downloads.",
        whyHi = "100 MB से अधिक होने पर स्वतः साफ़ होता है; आपकी फ़ोटो, वीडियो या डाउनलोड कभी नहीं छूता।",
        estimatedReclaimableMb = ownCacheMb.coerceAtLeast(0),
        target = StorageActionTarget.APP_OWN_CACHE,
      )

    if (freePercent < 15f) {
      recommendations +=
        StorageRecommendation(
          id = "android_storage_settings",
          titleEn = "System Storage Manager (Low Free Space: ${String.format("%.1f", freePercent)}%)",
          titleHi = "सिस्टम स्टोरेज मैनेजर (कम खाली स्थान: ${String.format("%.1f", freePercent)}%)",
          descriptionEn = "Open Android's built-in Storage settings to safely review unused apps and trash.",
          descriptionHi = "अनुपयोगी ऐप्स और ट्रैश को सुरक्षित रूप से हटाने के लिए Android स्टोरेज सेटिंग्स खोलें।",
          whyEn = "Keeping at least 10–15% storage free prevents system lag and flash wear.",
          whyHi = "कम से कम 10–15% जगह खाली रखने से फ़ोन धीमा नहीं होता और बैटरी भी बचती है।",
          estimatedReclaimableMb = 500,
          target = StorageActionTarget.ANDROID_STORAGE_SETTINGS,
        )
    }

    // Browser cache recommendation via safe App Info deep link
    val installedBrowser = findInstalledPackage(
      listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx", "com.brave.browser")
    )
    if (installedBrowser != null) {
      recommendations +=
        StorageRecommendation(
          id = "browser_cache",
          titleEn = "Browser Cache Review",
          titleHi = "ब्राउज़र कैश समीक्षा",
          descriptionEn = "Browsers often accumulate hundreds of MB in cached images. Open Storage & Cache to clear safely.",
          descriptionHi = "ब्राउज़र में सैकड़ों MB कैश जमा हो सकता है। इसे सुरक्षित साफ़ करने के लिए ऐप स्टोरेज खोलें।",
          whyEn = "Clearing browser cache frees space without deleting bookmarks or saved passwords.",
          whyHi = "ब्राउज़र कैश साफ़ करने से बुकमार्क या पासवर्ड हटे बिना जगह खाली होती है।",
          estimatedReclaimableMb = 250,
          target = StorageActionTarget.BROWSER_SETTINGS,
          packageHint = installedBrowser,
        )
    }

    val whatsappPkg = findInstalledPackage(listOf("com.whatsapp", "com.whatsapp.w4b"))
    if (whatsappPkg != null) {
      recommendations +=
        StorageRecommendation(
          id = "whatsapp_media",
          titleEn = "WhatsApp Media & Storage",
          titleHi = "व्हाट्सएप मीडिया और स्टोरेज",
          descriptionEn = "Review forwarded videos and large media directly inside Android Storage or WhatsApp Manage Storage.",
          descriptionHi = "फ़ॉरवर्ड किए गए वीडियो और बड़ी फ़ाइलें सीधे WhatsApp स्टोरेज मैनेजर में जांचें।",
          whyEn = "Netra never deletes personal chat media automatically; you stay in full control.",
          whyHi = "नेत्रा कभी भी आपकी निजी चैट या मीडिया अपने आप नहीं हटाता; पूरा नियंत्रण आपके पास है।",
          estimatedReclaimableMb = 400,
          target = StorageActionTarget.WHATSAPP_STORAGE,
          packageHint = whatsappPkg,
        )
    }

    recommendations +=
      StorageRecommendation(
        id = "downloads_review",
        titleEn = "Downloads & Large Files Review",
        titleHi = "डाउनलोड और बड़ी फ़ाइलों की समीक्षा",
        descriptionEn = "Open Android Files / Downloads to inspect old APKs, PDFs, or duplicate files manually.",
        descriptionHi = "पुरानी APK, PDF या डुप्लिकेट फ़ाइलें जांचने के लिए Android फ़ाइल्स / डाउनलोड खोलें।",
        whyEn = "Manual confirmation ensures important documents are never removed accidentally.",
        whyHi = "मैन्युअल पुष्टि यह सुनिश्चित करती है कि कोई ज़रूरी दस्तावेज़ गलती से न हटे।",
        estimatedReclaimableMb = 300,
        target = StorageActionTarget.DOWNLOADS_REVIEW,
      )

    recommendations +=
      StorageRecommendation(
        id = "obsolete_apps",
        titleEn = "App Caches & Unused App Data",
        titleHi = "ऐप कैश और अनुपयोगी ऐप डेटा",
        descriptionEn = "Sort installed apps by size in Android Settings to spot oversized caches.",
        descriptionHi = "बड़े कैश वाले ऐप्स देखने के लिए Android सेटिंग्स में ऐप्स को आकार के अनुसार देखें।",
        whyEn = "Android manages cross-app cache safely via system settings without root or intrusive permissions.",
        whyHi = "Android सिस्टम सेटिंग्स बिना रूट या अतिरिक्त अनुमति के सुरक्षित रूप से ऐप कैश संभालती है।",
        estimatedReclaimableMb = 350,
        target = StorageActionTarget.MANAGE_APPS_SETTINGS,
      )

    return StorageInsight(
      totalBytes = totalBytes,
      usedBytes = usedBytes,
      freeBytes = freeBytes,
      freePercent = freePercent,
      appOwnCacheBytes = ownCacheBytes,
      downloadsEstimatedBytes = 0L,
      heavyScanPostponedReasonEn = postponeReason?.first,
      heavyScanPostponedReasonHi = postponeReason?.second,
      recommendations = recommendations,
    )
  }

  /**
   * Safely cleans ONLY Netra's own internal and external cache directories.
   */
  fun cleanOwnCache(): Long {
    val before =
      directorySize(context.cacheDir) + (context.externalCacheDir?.let { directorySize(it) } ?: 0L)
    deleteChildren(context.cacheDir)
    context.externalCacheDir?.let { deleteChildren(it) }
    val after =
      directorySize(context.cacheDir) + (context.externalCacheDir?.let { directorySize(it) } ?: 0L)
    return (before - after).coerceAtLeast(0L)
  }

  fun buildSettingsIntentForTarget(target: StorageActionTarget, packageHint: String?): Intent {
    return when (target) {
      StorageActionTarget.ANDROID_STORAGE_SETTINGS ->
        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      StorageActionTarget.MANAGE_APPS_SETTINGS ->
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      StorageActionTarget.BROWSER_SETTINGS,
      StorageActionTarget.WHATSAPP_STORAGE -> {
        if (packageHint != null) {
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageHint, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        } else {
          Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
      StorageActionTarget.DOWNLOADS_REVIEW ->
        Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      StorageActionTarget.APP_OWN_CACHE ->
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
  }

  private fun findInstalledPackage(candidates: List<String>): String? {
    val pm = context.packageManager
    for (pkg in candidates) {
      val exists = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(pkg, 0)
        }
      }.isSuccess
      if (exists) return pkg
    }
    return null
  }

  private fun directorySize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var sum = 0L
    dir.listFiles()?.forEach { child ->
      sum += if (child.isDirectory) directorySize(child) else child.length()
    }
    return sum
  }

  private fun deleteChildren(dir: File?) {
    if (dir == null || !dir.exists()) return
    dir.listFiles()?.forEach { child ->
      if (child.isDirectory) child.deleteRecursively()
      else child.delete()
    }
  }

  companion object {
    fun formatBytes(bytes: Long): String {
      val kb = 1024.0
      val mb = kb * 1024
      val gb = mb * 1024
      return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.0f KB", bytes / kb)
        else -> "$bytes B"
      }
    }
  }
}
