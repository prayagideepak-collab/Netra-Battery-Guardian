package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.system.DiskUsageReport
import com.example.data.local.system.StorageInspector
import com.example.domain.model.StorageActionTarget
import com.example.domain.model.StorageInsight
import com.example.ui.theme.SemanticCoolMint
import com.example.ui.theme.SemanticCriticalRed
import com.example.ui.theme.SemanticHotOrange
import com.example.ui.theme.SemanticIceCyan
import com.example.ui.theme.SemanticWarmAmber
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale

data class StorageAppCategoryItem(
  val id: String,
  val titleEn: String,
  val titleHi: String,
  val subtitleEn: String,
  val subtitleHi: String,
  val bytes: Long,
  val shareOfOccupiedPercent: Float,
  val shareOfTotalPercent: Float,
  val color: Color,
  val icon: ImageVector,
  val actionTarget: StorageActionTarget?,
  val actionLabelEn: String,
  val actionLabelHi: String,
)

object StorageCategoryBreakdownCalculator {
  /**
   * Derives a deterministic, zero-wakelock breakdown of occupied storage into top
   * storage-consuming app categories from [StorageInsight] and [DiskUsageReport].
   */
  fun computeCategories(
    insight: StorageInsight,
    diskReport: DiskUsageReport?,
  ): List<StorageAppCategoryItem> {
    val totalBytes = insight.totalBytes.coerceAtLeast(1L)
    val usedBytes = insight.usedBytes.coerceIn(0L, totalBytes)
    if (usedBytes <= 0L) return emptyList()

    val cacheBytes = insight.appOwnCacheBytes.coerceIn(0L, usedBytes)
    val appsAndCodeBytes = 0L
    val appDataBytes = 0L
    val mediaAndSocialBytes = 0L
    val downloadsBytes = 0L
    val systemOsBytes = (usedBytes - cacheBytes).coerceAtLeast(0L)
    val topHeavyAppNames: String? = null

    val rawItems = listOf(
      StorageAppCategoryItem(
        id = "apps_binaries",
        titleEn = "Installed Apps & Binaries",
        titleHi = "इंस्टॉल किए गए ऐप्स और बाइनरी",
        subtitleEn = if (topHeavyAppNames != null) {
          "APK packages & compiled DEX ($topHeavyAppNames)"
        } else {
          "Installed APK packages, libraries & compiled app code"
        },
        subtitleHi = "इंस्टॉल किए गए APK पैकेज, लाइब्रेरी और ऐप कोड",
        bytes = appsAndCodeBytes,
        shareOfOccupiedPercent = (appsAndCodeBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (appsAndCodeBytes * 100f) / totalBytes.toFloat(),
        color = SemanticIceCyan,
        icon = Icons.Rounded.Apps,
        actionTarget = StorageActionTarget.MANAGE_APPS_SETTINGS,
        actionLabelEn = "Manage Apps",
        actionLabelHi = "ऐप्स प्रबंधित करें",
      ),
      StorageAppCategoryItem(
        id = "app_data",
        titleEn = "App Data & Local Databases",
        titleHi = "ऐप डेटा और स्थानीय डेटाबेस",
        subtitleEn = "User accounts, Room SQLite databases & offline app state",
        subtitleHi = "उपयोगकर्ता डेटा, Room डेटाबेस और ऑफ़लाइन ऐप स्थिति",
        bytes = appDataBytes,
        shareOfOccupiedPercent = (appDataBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (appDataBytes * 100f) / totalBytes.toFloat(),
        color = Color(0xFF7C4DFF),
        icon = Icons.Rounded.Storage,
        actionTarget = StorageActionTarget.MANAGE_APPS_SETTINGS,
        actionLabelEn = "Inspect Data",
        actionLabelHi = "डेटा देखें",
      ),
      StorageAppCategoryItem(
        id = "media_social",
        titleEn = "Media, Messaging & Social Apps",
        titleHi = "मीडिया, चैट और सोशल ऐप्स",
        subtitleEn = "Cached videos, voice notes, images & chat attachments",
        subtitleHi = "कैश किए गए वीडियो, वॉइस नोट्स, फ़ोटो और चैट मीडिया",
        bytes = mediaAndSocialBytes,
        shareOfOccupiedPercent = (mediaAndSocialBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (mediaAndSocialBytes * 100f) / totalBytes.toFloat(),
        color = SemanticWarmAmber,
        icon = Icons.Rounded.PermMedia,
        actionTarget = StorageActionTarget.ANDROID_STORAGE_SETTINGS,
        actionLabelEn = "Storage Settings",
        actionLabelHi = "स्टोरेज सेटिंग्स",
      ),
      StorageAppCategoryItem(
        id = "downloads_docs",
        titleEn = "Downloads, Archives & Documents",
        titleHi = "डाउनलोड और दस्तावेज़",
        subtitleEn = "PDFs, offline reports, installers & downloaded archives",
        subtitleHi = "PDF, ऑफ़लाइन रिपोर्ट, APK और डाउनलोड फ़ाइलें",
        bytes = downloadsBytes,
        shareOfOccupiedPercent = (downloadsBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (downloadsBytes * 100f) / totalBytes.toFloat(),
        color = SemanticHotOrange,
        icon = Icons.Rounded.Folder,
        actionTarget = StorageActionTarget.DOWNLOADS_REVIEW,
        actionLabelEn = "Open Downloads",
        actionLabelHi = "डाउनलोड खोलें",
      ),
      StorageAppCategoryItem(
        id = "app_caches",
        titleEn = "Netra App Cache (Measured)",
        titleHi = "नेत्रा ऐप कैश (मापा गया)",
        subtitleEn = "Netra own cache: ${StorageInspector.formatBytes(insight.appOwnCacheBytes)} • Temporary buffers",
        subtitleHi = "नेत्रा कैश: ${StorageInspector.formatBytes(insight.appOwnCacheBytes)} • अस्थायी कैश",
        bytes = cacheBytes,
        shareOfOccupiedPercent = (cacheBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (cacheBytes * 100f) / totalBytes.toFloat(),
        color = SemanticCoolMint,
        icon = Icons.Rounded.Cached,
        actionTarget = StorageActionTarget.APP_OWN_CACHE,
        actionLabelEn = "Clean Cache",
        actionLabelHi = "कैश साफ़ करें",
      ),
      StorageAppCategoryItem(
        id = "system_os",
        titleEn = "Other Occupied Space (Unattributed)",
        titleHi = "अन्य भरा स्थान (वर्ग अज्ञात)",
        subtitleEn = "Filesystem total minus measured Netra cache; content type is not known",
        subtitleHi = "कुल भरे स्थान में से मापा नेत्रा कैश घटाया गया; सामग्री का वर्ग ज्ञात नहीं है",
        bytes = systemOsBytes,
        shareOfOccupiedPercent = (systemOsBytes * 100f) / usedBytes.toFloat(),
        shareOfTotalPercent = (systemOsBytes * 100f) / totalBytes.toFloat(),
        color = Color(0xFF90A4AE),
        icon = Icons.Rounded.Memory,
        actionTarget = null,
        actionLabelEn = "",
        actionLabelHi = "",
      ),
    )

    return rawItems.filter { it.bytes > 0L }.sortedByDescending { it.bytes }
  }
}

/**
 * Native Jetpack Compose Canvas Storage Analysis Dashboard visualizing Occupied vs. Free Space
 * (Donut Ring + Stacked Bar mode) and listing Top Storage-Consuming App Categories.
 * Replaces web-based chart libraries (like Recharts) to stay 100% offline, native Kotlin, and ultra-low power.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageAnalysisDashboard(
  storageInsight: StorageInsight,
  diskUsageReport: DiskUsageReport?,
  isHindi: Boolean = false,
  onCategoryAction: (StorageActionTarget, String?) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  var showStackedBarMode by remember { mutableStateOf(false) }

  if (storageInsight.totalBytes <= 0L) return
  val totalBytes = storageInsight.totalBytes
  val usedBytes = storageInsight.usedBytes.coerceIn(0L, totalBytes)
  val freeBytes = storageInsight.freeBytes.coerceIn(0L, totalBytes)
  val occupiedPercent = ((usedBytes * 100.0) / totalBytes.toDouble()).toFloat().coerceIn(0f, 100f)
  val freePercent = storageInsight.freePercent.coerceIn(0f, 100f)

  val categories = remember(storageInsight, diskUsageReport) {
    StorageCategoryBreakdownCalculator.computeCategories(storageInsight, diskUsageReport)
  }

  val occupiedAccentColor = when {
    freePercent < 5f -> SemanticCriticalRed
    freePercent < 10f -> SemanticWarmAmber
    else -> SemanticIceCyan
  }
  val freeColor = SemanticCoolMint

  GuardianCard(
    modifier = modifier
      .fillMaxWidth()
      .testTag("storage_analysis_dashboard_card"),
    accentColor = occupiedAccentColor,
  ) {
    // Header & Chart Mode Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (isHindi) "स्टोरेज विश्लेषण डैशबोर्ड" else "Storage Analysis Dashboard",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = if (isHindi) {
            "फ़ाइलसिस्टम का उपयोग और नेत्रा कैश। अन्य उपयोग अवर्गीकृत है।"
          } else {
            "Filesystem totals and Netra cache. Other usage is unattributed."
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
          selected = !showStackedBarMode,
          onClick = { showStackedBarMode = false },
          label = { Text(if (isHindi) "रिंग" else "Donut") },
          modifier = Modifier.testTag("storage_chart_donut_chip"),
        )
        FilterChip(
          selected = showStackedBarMode,
          onClick = { showStackedBarMode = true },
          label = { Text(if (isHindi) "बार" else "Bar") },
          modifier = Modifier.testTag("storage_chart_bar_chip"),
        )
      }
    }

    // Occupied vs Free Summary Pills
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StatusPill(
        text = "Occupied: ${StorageInspector.formatBytes(usedBytes)} (${
          String.format(Locale.US, "%.1f", occupiedPercent)
        }%)",
        color = occupiedAccentColor,
        modifier = Modifier.testTag("storage_occupied_pill"),
      )
      StatusPill(
        text = "Free: ${StorageInspector.formatBytes(freeBytes)} (${
          String.format(Locale.US, "%.1f", freePercent)
        }%)",
        color = freeColor,
        modifier = Modifier.testTag("storage_free_pill"),
      )
      StatusPill(
        text = "Total: ${StorageInspector.formatBytes(totalBytes)}",
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.testTag("storage_total_pill"),
      )
    }

    // Native Canvas Visualization (Donut Ring or Stacked Distribution Bar)
    val a11yDescription =
      "Storage occupancy chart: ${String.format(Locale.US, "%.1f", occupiedPercent)}% occupied " +
        "(${StorageInspector.formatBytes(usedBytes)}) and " +
        "${String.format(Locale.US, "%.1f", freePercent)}% free (${StorageInspector.formatBytes(freeBytes)})."

    if (!showStackedBarMode) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Box(
          modifier = Modifier.size(152.dp),
          contentAlignment = Alignment.Center,
        ) {
          val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          Canvas(
            modifier = Modifier
              .size(144.dp)
              .testTag("storage_analysis_chart_canvas")
              .semantics { contentDescription = a11yDescription },
          ) {
            val strokeWidth = 20.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background ring
            drawArc(
              color = trackColor,
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth),
            )

            // Draw each occupied category arc segment, then the Free Space segment
            var currentStartAngle = -90f
            categories.forEach { cat ->
              val sweep = ((cat.bytes.toDouble() / totalBytes.toDouble()) * 360.0).toFloat()
              if (sweep >= 0.8f) {
                drawArc(
                  color = cat.color,
                  startAngle = currentStartAngle,
                  sweepAngle = (sweep - 1.2f).coerceAtLeast(0.6f),
                  useCenter = false,
                  topLeft = topLeft,
                  size = arcSize,
                  style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                currentStartAngle += sweep
              }
            }

            val freeSweep = (360f - (currentStartAngle + 90f)).coerceAtLeast(0f)
            if (freeSweep > 0.5f) {
              drawArc(
                color = freeColor,
                startAngle = currentStartAngle,
                sweepAngle = freeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
              )
            }
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "${String.format(Locale.US, "%.0f", occupiedPercent)}%",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = if (isHindi) "भरा हुआ" else "Occupied",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Compact Legend for Occupied Categories vs Free Space
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.take(4).forEach { cat ->
            LegendRow(
              color = cat.color,
              label = if (isHindi) cat.titleHi else cat.titleEn,
              valueText = "${String.format(Locale.US, "%.1f", cat.shareOfTotalPercent)}%",
            )
          }
          LegendRow(
            color = freeColor,
            label = if (isHindi) "खाली स्थान (Free)" else "Free Available Space",
            valueText = "${String.format(Locale.US, "%.1f", freePercent)}%",
          )
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .testTag("storage_analysis_chart_canvas")
            .semantics { contentDescription = a11yDescription },
        ) {
          var currentX = 0f
          categories.forEach { cat ->
            val segWidth = ((cat.bytes.toDouble() / totalBytes.toDouble()) * size.width).toFloat()
            if (segWidth > 0.5f) {
              drawRect(
                color = cat.color,
                topLeft = Offset(currentX, 0f),
                size = Size(segWidth, size.height),
              )
              currentX += segWidth
            }
          }
          val remainingWidth = (size.width - currentX).coerceAtLeast(0f)
          if (remainingWidth > 0.5f) {
            drawRoundRect(
              color = freeColor,
              topLeft = Offset(currentX, 0f),
              size = Size(remainingWidth, size.height),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Occupied: ${StorageInspector.formatBytes(usedBytes)} (${String.format(Locale.US, "%.1f", occupiedPercent)}%)",
            style = TelemetryMonoStyle,
            color = occupiedAccentColor,
          )
          Text(
            text = "Free: ${StorageInspector.formatBytes(freeBytes)} (${String.format(Locale.US, "%.1f", freePercent)}%)",
            style = TelemetryMonoStyle,
            color = freeColor,
          )
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    // Top Storage-Consuming App Categories List
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = if (isHindi) "शीर्ष स्टोरेज खपत करने वाली ऐप श्रेणियाँ" else "Top Storage-Consuming App Categories",
        style = MaterialTheme.typography.titleSmall,
      )
      Text(
        text = "${categories.size} categories",
        style = TelemetryMonoStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("storage_categories_list"),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      categories.forEachIndexed { index, cat ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_category_row_${cat.id}"),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = cat.icon,
                contentDescription = null,
                tint = cat.color,
                modifier = Modifier.size(20.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "#${index + 1} ${if (isHindi) cat.titleHi else cat.titleEn}",
                  style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                  text = if (isHindi) cat.subtitleHi else cat.subtitleEn,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = StorageInspector.formatBytes(cat.bytes),
                style = TelemetryMonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "${String.format(Locale.US, "%.1f", cat.shareOfOccupiedPercent)}% of used",
                style = MaterialTheme.typography.labelSmall,
                color = cat.color,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LinearProgressIndicator(
              progress = { (cat.shareOfOccupiedPercent / 100f).coerceIn(0.02f, 1f) },
              modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = cat.color,
              trackColor = cat.color.copy(alpha = 0.16f),
            )
            if (cat.actionTarget != null) {
              TextButton(
                onClick = { onCategoryAction(cat.actionTarget, null) },
                modifier = Modifier.testTag("storage_category_action_${cat.id}"),
              ) {
                Text(
                  text = if (isHindi) cat.actionLabelHi else cat.actionLabelEn,
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegendRow(
  color: Color,
  label: String,
  valueText: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(color),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
    Text(
      text = valueText,
      style = TelemetryMonoStyle,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
