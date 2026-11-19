package com.example

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.BootCompletedReceiver
import com.example.service.NetraWorkScheduler
import com.example.service.UnlockedStorageGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UnlockedStartupTest {
  @Test fun lockedUserDefersBeforeWorkManagerInitialization() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val manager = app.getSystemService(UserManager::class.java)
    Shadows.shadowOf(manager).setUserUnlocked(false)
    assertFalse(UnlockedStorageGate.isAvailable(app))
    // WorkManager is intentionally not initialized by this Application fixture.
    NetraWorkScheduler.schedulePeriodicMaintenance(app)
    BootCompletedReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
    BootCompletedReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    BootCompletedReceiver().onReceive(app, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
  }
  @Test fun availableOnlyAfterUserUnlockAndNoDirectBootOptIn() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val manager = app.getSystemService(UserManager::class.java)
    Shadows.shadowOf(manager).setUserUnlocked(true)
    assertTrue(UnlockedStorageGate.isAvailable(app))
    val receiver = app.packageManager.getReceiverInfo(ComponentName(app, BootCompletedReceiver::class.java), PackageManager.GET_META_DATA)
    assertFalse(receiver.directBootAware)
  }
}
