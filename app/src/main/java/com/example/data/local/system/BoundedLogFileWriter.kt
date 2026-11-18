package com.example.data.local.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, rotated local diagnostic log writer (max 2 files x 128 KB = 256 KB).
 * Strictly offline: never uploads logs over network.
 */
@Singleton
class BoundedLogFileWriter @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    const val MAX_FILE_BYTES = 128 * 1024L
    const val MAX_TOTAL_BYTES = MAX_FILE_BYTES * 2L
    private const val PRIMARY_LOG = "netra_health_0.log"
    private const val BACKUP_LOG = "netra_health_1.log"
  }

  private val lock = Any()
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

  private fun logDir(): File {
    val dir = File(context.filesDir, "logs")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun append(tag: String, message: String) {
    synchronized(lock) {
      runCatching {
        val dir = logDir()
        val primary = File(dir, PRIMARY_LOG)
        val backup = File(dir, BACKUP_LOG)
        if (primary.exists() && primary.length() >= MAX_FILE_BYTES) {
          if (backup.exists()) backup.delete()
          primary.renameTo(backup)
        }
        val line = "${dateFormat.format(Date())} [$tag] $message\n"
        primary.appendText(line)
      }
    }
  }

  fun totalSizeBytes(): Long =
    synchronized(lock) {
      val dir = logDir()
      val f0 = File(dir, PRIMARY_LOG)
      val f1 = File(dir, BACKUP_LOG)
      (if (f0.exists()) f0.length() else 0L) + (if (f1.exists()) f1.length() else 0L)
    }

  fun clearLogs() {
    synchronized(lock) {
      val dir = logDir()
      File(dir, PRIMARY_LOG).delete()
      File(dir, BACKUP_LOG).delete()
    }
  }
}
