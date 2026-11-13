package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Direct-boot-aware receiver that safely restores WorkManager periodic maintenance
 * after device reboot or app update without starting disallowed background services.
 */
class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action == Intent.ACTION_BOOT_COMPLETED ||
      action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
      action == Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
        NetraWorkScheduler.schedulePeriodicMaintenance(context.applicationContext)
      }
    }
  }
}
