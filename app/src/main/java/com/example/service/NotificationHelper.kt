package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    const val CHANNEL_STATUS = "netra_status"
    const val CHANNEL_ADVISORY = "netra_advisory"
    const val CHANNEL_CRITICAL = "netra_critical"

    const val ID_GUARDIAN_SERVICE = 1001
    const val ID_MONITORING_SERVICE = 1007
    const val ID_CHARGING_ADVISORY = 1002
    const val ID_THERMAL_ALERT = 1003
    const val ID_DRAIN_ALERT = 1004
    const val ID_STORAGE_ALERT = 1005
    const val ID_TTS_FALLBACK = 1006
  }

  fun createChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

    val statusChannel =
      NotificationChannel(
        CHANNEL_STATUS,
        context.getString(R.string.channel_status_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = context.getString(R.string.channel_status_desc)
        setShowBadge(false)
      }

    val advisoryChannel =
      NotificationChannel(
        CHANNEL_ADVISORY,
        context.getString(R.string.channel_advisory_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.channel_advisory_desc)
      }

    val criticalChannel =
      NotificationChannel(
        CHANNEL_CRITICAL,
        context.getString(R.string.channel_critical_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = context.getString(R.string.channel_critical_desc)
        enableVibration(true)
      }

    nm.createNotificationChannels(listOf(statusChannel, advisoryChannel, criticalChannel))
  }

  fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun buildGuardianForegroundNotification(title: String, body: String): Notification {
    return NotificationCompat.Builder(context, CHANNEL_STATUS)
      .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
      .setContentTitle(title)
      .setContentText(body)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(mainActivityPendingIntent())
      .build()
  }

  fun postAdvisory(
    id: Int,
    title: String,
    body: String,
    actionIntent: Intent? = null,
    actionLabel: String? = null,
  ) {
    if (!hasNotificationPermission()) return
    val builder =
      NotificationCompat.Builder(context, CHANNEL_ADVISORY)
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(mainActivityPendingIntent())

    if (actionIntent != null && actionLabel != null) {
      val pi =
        PendingIntent.getActivity(
          context,
          id + 100,
          actionIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      builder.addAction(0, actionLabel, pi)
    }

    try {
      NotificationManagerCompat.from(context).notify(id, builder.build())
    } catch (_: SecurityException) {
      // Permission can be revoked between the check and notification delivery.
    }
  }

  fun postCriticalAlert(id: Int, title: String, body: String) {
    if (!hasNotificationPermission()) return
    val notification =
      NotificationCompat.Builder(context, CHANNEL_CRITICAL)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(mainActivityPendingIntent())
        .build()
    try {
      NotificationManagerCompat.from(context).notify(id, notification)
    } catch (_: SecurityException) {
      // A denied/revoked notification grant must not crash local monitoring.
    }
  }

  private fun mainActivityPendingIntent(): PendingIntent {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
