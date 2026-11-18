package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.data.local.prefs.PreferencesRepository
import com.example.voice.ChargingVoiceAssistant
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThresholdTtsServiceState(
  val lastThresholdTriggered: Int? = null,
  val lastIsCharging: Boolean = false,
  val lastTriggeredAtMillis: Long = 0L,
  val triggerCount: Int = 0,
)

/**
 * Lightweight, event-driven Android [Service] responsible for triggering offline
 * Text-to-Speech voice notifications when the battery reaches key thresholds such as
 * optimal charge targets (80%, 90%, 100%) or low battery levels (20%, 15%, 10%, 5%, 1%).
 *
 * Uses zero polling and automatically calls [stopSelf] after dispatching speech to keep
 * memory and battery footprint minimal on low-end devices.
 */
@AndroidEntryPoint
class BatteryThresholdTtsService : Service() {

  @Inject lateinit var voiceAssistant: ChargingVoiceAssistant
  @Inject lateinit var preferencesRepository: PreferencesRepository

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action != ACTION_TRIGGER_THRESHOLD) {
      stopSelfResult(startId)
      return START_NOT_STICKY
    }

    val level = intent.getIntExtra(EXTRA_LEVEL_PERCENT, -1)
    val isCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, false)
    val screenOn = intent.getBooleanExtra(EXTRA_SCREEN_ON, true)

    if (level !in 0..100 || !isThresholdEligible(level, isCharging)) {
      stopSelfResult(startId)
      return START_NOT_STICKY
    }

    serviceScope.launch {
      val prefs = preferencesRepository.preferences.first()
      dispatchThresholdVoice(
        voiceAssistant = voiceAssistant,
        prefs = prefs,
        level = level,
        isCharging = isCharging,
        screenOn = screenOn,
      )
      _serviceState.update {
        it.copy(
          lastThresholdTriggered = level,
          lastIsCharging = isCharging,
          lastTriggeredAtMillis = System.currentTimeMillis(),
          triggerCount = it.triggerCount + 1,
        )
      }
      stopSelfResult(startId)
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_TRIGGER_THRESHOLD = "com.example.action.TRIGGER_BATTERY_THRESHOLD_TTS"
    const val EXTRA_LEVEL_PERCENT = "extra_level_percent"
    const val EXTRA_IS_CHARGING = "extra_is_charging"
    const val EXTRA_SCREEN_ON = "extra_screen_on"

    val CHARGE_THRESHOLDS = setOf(80, 90, 100)
    val LOW_BATTERY_THRESHOLDS = setOf(20, 15, 10, 5, 1)

    private val _serviceState = MutableStateFlow(ThresholdTtsServiceState())
    val serviceState: StateFlow<ThresholdTtsServiceState> = _serviceState.asStateFlow()

    fun isThresholdEligible(levelPercent: Int, isCharging: Boolean): Boolean =
      if (isCharging) levelPercent in CHARGE_THRESHOLDS
      else levelPercent in LOW_BATTERY_THRESHOLDS

    fun buildTriggerIntent(
      context: Context,
      levelPercent: Int,
      isCharging: Boolean,
      screenOn: Boolean,
    ): Intent =
      Intent(context, BatteryThresholdTtsService::class.java).apply {
        action = ACTION_TRIGGER_THRESHOLD
        putExtra(EXTRA_LEVEL_PERCENT, levelPercent)
        putExtra(EXTRA_IS_CHARGING, isCharging)
        putExtra(EXTRA_SCREEN_ON, screenOn)
      }

    fun dispatchThresholdVoice(
      voiceAssistant: ChargingVoiceAssistant,
      prefs: com.example.data.local.prefs.UserPreferences,
      level: Int,
      isCharging: Boolean,
      screenOn: Boolean,
    ) {
      if (isCharging) {
        voiceAssistant.announceChargingMilestone(
          level = level,
          prefs = prefs,
          screenOn = screenOn,
          isCharging = true,
        )
      } else {
        voiceAssistant.announceDischargingMilestone(
          level = level,
          prefs = prefs,
          screenOn = screenOn,
        )
      }
    }

    /**
     * Safely launches [BatteryThresholdTtsService] when OS background restrictions allow,
     * falling back directly to [ChargingVoiceAssistant] if background execution limits apply.
     */
    fun triggerSafely(
      context: Context,
      voiceAssistant: ChargingVoiceAssistant,
      prefs: com.example.data.local.prefs.UserPreferences,
      levelPercent: Int,
      isCharging: Boolean,
      screenOn: Boolean,
      allowServiceStart: Boolean,
    ) {
      if (!isThresholdEligible(levelPercent, isCharging)) return
      if (allowServiceStart) {
        val started = runCatching {
          context.startService(buildTriggerIntent(context, levelPercent, isCharging, screenOn))
        }.isSuccess
        if (started) return
      }
      dispatchThresholdVoice(voiceAssistant, prefs, levelPercent, isCharging, screenOn)
    }
  }
}
