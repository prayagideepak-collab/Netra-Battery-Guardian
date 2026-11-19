package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Credential storage stays closed before unlock. Never starts FGS or audio at boot. */
class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    if (!UnlockedStorageGate.isAvailable(context)) return
    NetraWorkScheduler.schedulePeriodicMaintenance(context.applicationContext)
  }
}
