package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import com.example.data.local.db.ActivityLogEntity
import com.example.data.local.db.AppDatabase
import com.example.data.local.system.BoundedLogFileWriter
import com.example.domain.model.LogCategory
import com.example.domain.model.LogSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class DozeMonitorState(
  val isRegistered: Boolean = false,
  val isInDozeMode: Boolean = false,
  val lastTransitionMillis: Long = 0L,
  val enterDozeCount: Int = 0,
  val exitDozeCount: Int = 0,
  val nonEssentialWorkSuspended: Boolean = false,
)

/**
 * Listens for [PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED] to track and log transitions
 * into and out of OS Doze mode, ensuring Netra pauses all non-essential background work
 * while the device is idling in Doze.
 */
@Singleton
class DozeModeMonitor @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val logWriter: BoundedLogFileWriter,
  private val appDatabase: AppDatabase,
) {
  companion object {
    const val OWNER_APP = "application"
    const val OWNER_MONITORING_SERVICE = "monitoring_service"
    const val OWNER_DEFAULT = "default"
  }

  private val powerManager: PowerManager? =
    context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _state = MutableStateFlow(DozeMonitorState())
  val state: StateFlow<DozeMonitorState> = _state.asStateFlow()

  private val activeOwners = mutableSetOf<String>()
  private var receiverRegistered = false

  private val dozeReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
          handleDeviceIdleChanged()
        }
      }
    }

  @Synchronized
  fun register(owner: String = OWNER_DEFAULT) {
    activeOwners.add(owner)
    if (receiverRegistered) return

    val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(dozeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(dozeReceiver, filter)
    }
    receiverRegistered = true

    val initialIdle = powerManager?.isDeviceIdleMode ?: false
    _state.update {
      it.copy(
        isRegistered = true,
        isInDozeMode = initialIdle,
        nonEssentialWorkSuspended = initialIdle,
      )
    }
  }

  @Synchronized
  fun unregister(owner: String = OWNER_DEFAULT) {
    activeOwners.remove(owner)
    if (activeOwners.isEmpty() && receiverRegistered) {
      runCatching { context.unregisterReceiver(dozeReceiver) }
      receiverRegistered = false
      _state.update { it.copy(isRegistered = false) }
    }
  }

  /**
   * Returns true whenever the device is in Doze mode and Netra must avoid
   * non-essential background work to maximize battery savings.
   */
  fun shouldSkipNonEssentialWork(): Boolean {
    val systemIdle = powerManager?.isDeviceIdleMode ?: false
    return systemIdle || _state.value.isInDozeMode
  }

  @Synchronized
  fun handleDeviceIdleChanged(nowMillis: Long = System.currentTimeMillis()) {
    val isIdleNow = powerManager?.isDeviceIdleMode ?: false
    val prev = _state.value
    val transitioned = isIdleNow != prev.isInDozeMode

    val nextEnterCount = if (transitioned && isIdleNow) prev.enterDozeCount + 1 else prev.enterDozeCount
    val nextExitCount = if (transitioned && !isIdleNow) prev.exitDozeCount + 1 else prev.exitDozeCount

    _state.value = prev.copy(
      isRegistered = receiverRegistered,
      isInDozeMode = isIdleNow,
      lastTransitionMillis = if (transitioned) nowMillis else prev.lastTransitionMillis,
      enterDozeCount = nextEnterCount,
      exitDozeCount = nextExitCount,
      nonEssentialWorkSuspended = isIdleNow,
    )

    if (transitioned) {
      val msg =
        if (isIdleNow) {
          "Entered OS Doze mode (#$nextEnterCount) — suspending all non-essential work."
        } else {
          "Exited OS Doze mode (#$nextExitCount) — resuming scheduled maintenance windows."
        }
      logWriter.append("DOZE", msg)
      ioScope.launch {
        persistDozeTransitionLog(isIdleNow, nowMillis)
      }
    }
  }

  suspend fun persistDozeTransitionLog(isIdleNow: Boolean, nowMillis: Long = System.currentTimeMillis()) {
    runCatching {
      appDatabase.netraDao().insertActivityLog(
        ActivityLogEntity(
          timestampMillis = nowMillis,
          category = LogCategory.POWER_BUDGET.name,
          severity = LogSeverity.INFO.name,
          titleEn = if (isIdleNow) "Entered Doze Mode" else "Exited Doze Mode",
          titleHi = if (isIdleNow) "डोज़ मोड सक्रिय हुआ" else "डोज़ मोड समाप्त हुआ",
          detailEn =
            if (isIdleNow) "All non-essential scans, app polling, and cache cleanup paused for ultra-low power."
            else "Device exited deep Doze; scheduled maintenance can proceed when constraints allow.",
          detailHi =
            if (isIdleNow) "पावर बचाने के लिए सभी गैर-जरूरी स्कैन और बैकग्राउंड कार्य रोके गए।"
            else "डिवाइस डोज़ से बाहर आया; निर्धारित रखरखाव कार्य पुनः सक्षम हैं।",
          whyEn = "Tracked event-driven via PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED with zero polling.",
          whyHi = "PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED ब्रॉडकास्ट द्वारा बिना पोलिंग के ट्रैक किया गया।",
        )
      )
    }
  }
}
