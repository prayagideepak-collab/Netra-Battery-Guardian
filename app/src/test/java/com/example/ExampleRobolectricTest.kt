package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Netra Battery`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Netra Battery", appName)
  }

  @Test
  fun `manifest declares strictly offline permissions without INTERNET`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val permissions = info.requestedPermissions?.toList() ?: emptyList()
    assertFalse("App must be 100% offline with no INTERNET permission", permissions.contains(Manifest.permission.INTERNET))
  }
  @Test
  fun `backup and cleartext stay disabled in merged manifest`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val flags = context.applicationInfo.flags
    assertFalse(flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    assertFalse(flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
  }
}
