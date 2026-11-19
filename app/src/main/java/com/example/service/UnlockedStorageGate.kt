package com.example.service

import android.content.Context
import android.os.UserManager

/** Unknown unlock state defers credential-backed initialization. No polling or external restart. */
internal object UnlockedStorageGate {
  fun isAvailable(context: Context): Boolean =
    runCatching { context.getSystemService(UserManager::class.java)?.isUserUnlocked == true }.getOrDefault(false)
}
