package com.example.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.local.prefs.UserPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChargingSpeedClass
import com.example.service.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ChargingAssistantState(
  val isTtsBound: Boolean = false,
  val lastMilestoneLevel: Int? = null,
  val lastSpokenMessage: String? = null,
  val lastSkippedReason: String? = null,
  val announcementCount: Int = 0,
)

/**
 * Resource-minimal offline Text-to-Speech charging assistant.
 *
 * - Lazily initializes [TextToSpeech] only when an eligible announcement triggers.
 * - Releases/shuts down the TTS engine automatically after a brief idle window so no
 *   persistent TTS service binder consumes memory or battery.
 * - Battery milestone announcements strictly trigger ONLY while charging AND screen is on.
 */
@Singleton
class ChargingVoiceAssistant @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val notificationHelper: NotificationHelper,
) : TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  @Volatile private var isReady: Boolean = false
  @Volatile private var initFailed: Boolean = false

  private val lastSpokenByKey = mutableMapOf<String, Long>()
  private var currentLocaleLang: AppLanguage? = null
  private var pendingUtterance: PendingUtterance? = null

  private val mainHandler = Handler(Looper.getMainLooper())
  private val autoShutdownRunnable = Runnable { shutdown() }

  private val _assistantState = MutableStateFlow(ChargingAssistantState())
  val assistantState: StateFlow<ChargingAssistantState> = _assistantState.asStateFlow()

  private data class PendingUtterance(
    val key: String,
    val message: String,
    val prefs: UserPreferences,
    val urgent: Boolean,
    val fallbackTitle: String,
  )

  fun ensureInitialized() {
    if (tts == null && !initFailed) {
      runCatching {
        val engine = TextToSpeech(context, this)
        engine.setOnUtteranceProgressListener(
          object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              mainHandler.removeCallbacks(autoShutdownRunnable)
            }

            override fun onDone(utteranceId: String?) {
              scheduleIdleShutdown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
              scheduleIdleShutdown()
            }
          }
        )
        tts = engine
        _assistantState.update { it.copy(isTtsBound = true) }
      }.onFailure {
        initFailed = true
      }
    }
  }

  private fun scheduleIdleShutdown() {
    mainHandler.removeCallbacks(autoShutdownRunnable)
    mainHandler.postDelayed(autoShutdownRunnable, IDLE_SHUTDOWN_DELAY_MS)
  }

  @Synchronized
  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isReady = true
      applyLocale(currentLocaleLang ?: AppLanguage.ENGLISH)
      pendingUtterance?.let { pending ->
        pendingUtterance = null
        dispatchToTtsEngine(pending.key, pending.message, pending.prefs, pending.urgent, pending.fallbackTitle)
      } ?: scheduleIdleShutdown()
    } else {
      isReady = false
      initFailed = true
      pendingUtterance?.let { pending ->
        pendingUtterance = null
        if (pending.urgent) {
          notificationHelper.postCriticalAlert(NotificationHelper.ID_TTS_FALLBACK, pending.fallbackTitle, pending.message)
        } else {
          notificationHelper.postAdvisory(NotificationHelper.ID_TTS_FALLBACK, pending.fallbackTitle, pending.message)
        }
      }
      shutdown()
    }
  }

  private fun applyLocale(language: AppLanguage) {
    val engine = tts ?: return
    val locale =
      when (language) {
        AppLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
        AppLanguage.HINGLISH -> Locale.forLanguageTag("en-IN")
        AppLanguage.ENGLISH -> Locale.US
      }
    val res = engine.setLanguage(locale)
    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
      engine.setLanguage(Locale.US)
    }
    currentLocaleLang = language
  }

  fun isNightSilentActive(prefs: UserPreferences, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!prefs.nightSilentEnabled) return false
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val start = prefs.nightSilentStartMinutes
    val end = prefs.nightSilentEndMinutes
    return if (start <= end) {
      minuteOfDay in start until end
    } else {
      minuteOfDay >= start || minuteOfDay < end
    }
  }

  fun announceChargerConnected(
    speedClass: ChargingSpeedClass,
    prefs: UserPreferences,
    screenOn: Boolean,
  ) {
    val text =
      when (prefs.voiceLanguage) {
        AppLanguage.ENGLISH -> {
          val mode =
            when (speedClass) {
              ChargingSpeedClass.SLOW -> "Slow charging started."
              ChargingSpeedClass.STANDARD -> "Normal charging started."
              ChargingSpeedClass.FAST -> "Fast charging started."
              ChargingSpeedClass.SUPER_FAST -> "Super fast charging started."
              ChargingSpeedClass.ULTRA_FAST -> "Ultra fast charging started."
              ChargingSpeedClass.EXTREME_FAST -> "Extreme fast charging started."
              ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED -> "High phone usage is reducing effective charging speed."
              ChargingSpeedClass.NOT_CHARGING -> "Charging detected."
            }
          "Charger connected. $mode"
        }
        AppLanguage.HINDI -> {
          val mode =
            when (speedClass) {
              ChargingSpeedClass.SLOW -> "धीमी चार्जिंग शुरू हुई।"
              ChargingSpeedClass.STANDARD -> "सामान्य चार्जिंग शुरू हुई।"
              ChargingSpeedClass.FAST -> "तेज़ चार्जिंग शुरू हुई।"
              ChargingSpeedClass.SUPER_FAST -> "सुपर फ़ास्ट चार्जिंग शुरू हुई।"
              ChargingSpeedClass.ULTRA_FAST -> "अल्ट्रा फ़ास्ट चार्जिंग शुरू हुई।"
              ChargingSpeedClass.EXTREME_FAST -> "एक्सट्रीम फ़ास्ट चार्जिंग शुरू हुई।"
              ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED -> "ज़्यादा उपयोग के कारण चार्जिंग धीमी है।"
              ChargingSpeedClass.NOT_CHARGING -> "चार्जिंग शुरू हुई।"
            }
          "चार्जर जुड़ गया है। $mode"
        }
        AppLanguage.HINGLISH -> {
          val mode =
            when (speedClass) {
              ChargingSpeedClass.SLOW -> "Slow charging shuru ho gayi hai."
              ChargingSpeedClass.STANDARD -> "Normal charging shuru ho gayi hai."
              ChargingSpeedClass.FAST -> "Fast charging shuru ho gayi hai."
              ChargingSpeedClass.SUPER_FAST -> "Super fast charging shuru ho gayi hai."
              ChargingSpeedClass.ULTRA_FAST -> "Ultra fast charging shuru ho gayi hai."
              ChargingSpeedClass.EXTREME_FAST -> "Extreme fast charging shuru ho gayi hai."
              ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED -> "Heavy use ki wajah se charging slow hai."
              ChargingSpeedClass.NOT_CHARGING -> "Charging shuru ho gayi hai."
            }
          "Charger connect ho gaya hai. $mode"
        }
      }
    speak(
      key = "charger_connected",
      message = text,
      prefs = prefs,
      urgent = true,
      bypassNightSilent = true,
      bypassScreenOffRule = false,
      screenOn = screenOn,
      fallbackTitle = "Netra Charging",
    )
  }

  fun announceChargerDisconnected(prefs: UserPreferences, screenOn: Boolean) {
    val text =
      when (prefs.voiceLanguage) {
        AppLanguage.ENGLISH -> "Charger disconnected. Battery mode active."
        AppLanguage.HINDI -> "चार्जर हटा दिया गया है। बैटरी मोड सक्रिय है।"
        AppLanguage.HINGLISH -> "Charger disconnect ho gaya hai. Battery mode active hai."
      }
    speak(
      key = "charger_disconnected",
      message = text,
      prefs = prefs,
      urgent = true,
      bypassNightSilent = true,
      bypassScreenOffRule = true,
      screenOn = screenOn,
      fallbackTitle = "Netra Battery Mode",
    )
  }

  /**
   * Announces a charging battery milestone (e.g., reached 80% charge) using minimal TTS resources.
   * Strictly only triggers when [isCharging] is true AND [screenOn] is true.
   */
  fun announceChargingMilestone(
    level: Int,
    prefs: UserPreferences,
    screenOn: Boolean,
    isCharging: Boolean = true,
  ) {
    if (!isCharging || !screenOn) {
      _assistantState.update {
        it.copy(
          lastSkippedReason = if (!isCharging) "Skipped: not charging" else "Skipped: screen off",
        )
      }
      return
    }
    if (!prefs.announceCharging) return
    val isFull = level >= 100
    if (isNightSilentActive(prefs) && !isFull) return

    val code = "C$level"
    val text =
      when (prefs.voiceLanguage) {
        AppLanguage.ENGLISH ->
          if (isFull) "Reached 100 percent charge. Battery is fully charged."
          else "Reached $level percent charge."
        AppLanguage.HINDI ->
          if (isFull) "100 प्रतिशत चार्ज पूरा हो गया है। बैटरी फुल है।"
          else "बैटरी $level प्रतिशत चार्ज हो गई है।"
        AppLanguage.HINGLISH ->
          if (isFull) "100 percent charge ho gaya hai. Battery full hai."
          else "Battery $level percent charge ho gayi hai."
      }

    speak(
      key = "milestone_$code",
      message = text,
      prefs = prefs,
      urgent = isFull,
      bypassNightSilent = isFull,
      bypassScreenOffRule = false,
      screenOn = screenOn,
      fallbackTitle = "Netra Charge $code",
      milestoneLevel = level,
    )
  }

  fun announceDischargingMilestone(level: Int, prefs: UserPreferences, screenOn: Boolean) {
    if (!prefs.announceDischarging) return
    if (isNightSilentActive(prefs)) return

    val code = "D$level"
    val text =
      if (level <= 5) {
        when (prefs.voiceLanguage) {
          AppLanguage.ENGLISH -> "$code. Battery critically low. Please connect charger."
          AppLanguage.HINDI -> "$code. बैटरी बहुत कम है। कृपया चार्जर लगाएँ।"
          AppLanguage.HINGLISH -> "$code. Battery bahut kam hai. Charger lagayein."
        }
      } else {
        code
      }
    speak(
      key = "milestone_$code",
      message = text,
      prefs = prefs,
      urgent = level <= 5,
      bypassNightSilent = false,
      bypassScreenOffRule = level <= 5,
      screenOn = screenOn,
      fallbackTitle = "Netra Battery $code",
    )
  }

  fun announceThermalSafety(
    key: String,
    messageEn: String,
    messageHi: String,
    messageHinglish: String,
    prefs: UserPreferences,
    urgent: Boolean,
    screenOn: Boolean,
  ) {
    val msg =
      when (prefs.voiceLanguage) {
        AppLanguage.ENGLISH -> messageEn
        AppLanguage.HINDI -> messageHi
        AppLanguage.HINGLISH -> messageHinglish
      }
    speak(
      key = key,
      message = msg,
      prefs = prefs,
      urgent = urgent,
      bypassNightSilent = urgent,
      bypassScreenOffRule = urgent,
      screenOn = screenOn,
      fallbackTitle = "Netra Thermal Alert",
    )
  }

  fun testVoice(prefs: UserPreferences) {
    val msg =
      when (prefs.voiceLanguage) {
        AppLanguage.ENGLISH -> "Netra Battery Guardian offline voice assistant is ready."
        AppLanguage.HINDI -> "नेत्रा बैटरी गार्जियन का ऑफ़लाइन वॉइस असिस्टेंट तैयार है।"
        AppLanguage.HINGLISH -> "Netra Battery Guardian ka offline voice assistant ready hai."
      }
    speak(
      key = "test_voice_${System.currentTimeMillis() / 1000}",
      message = msg,
      prefs = prefs,
      urgent = true,
      bypassNightSilent = true,
      bypassScreenOffRule = true,
      screenOn = true,
      fallbackTitle = "Netra Voice Test",
    )
  }

  @Synchronized
  fun speak(
    key: String,
    message: String,
    prefs: UserPreferences,
    urgent: Boolean,
    bypassNightSilent: Boolean,
    bypassScreenOffRule: Boolean,
    screenOn: Boolean,
    fallbackTitle: String,
    milestoneLevel: Int? = null,
  ) {
    if (!prefs.voiceEnabled) {
      if (urgent) notificationHelper.postCriticalAlert(NotificationHelper.ID_TTS_FALLBACK, fallbackTitle, message)
      return
    }
    if (!bypassNightSilent && isNightSilentActive(prefs)) return
    if (!bypassScreenOffRule && !screenOn) {
      _assistantState.update { it.copy(lastSkippedReason = "Skipped: screen off") }
      return
    }

    if (skipForPlayback()) return

    val now = System.currentTimeMillis()
    val last = lastSpokenByKey[key] ?: 0L
    if (now - last < 30_000L) return
    lastSpokenByKey[key] = now

    _assistantState.update {
      it.copy(
        lastMilestoneLevel = milestoneLevel ?: it.lastMilestoneLevel,
        lastSpokenMessage = message,
        lastSkippedReason = null,
        announcementCount = it.announcementCount + 1,
      )
    }

    if (initFailed) {
      postFallbackNotification(urgent, fallbackTitle, message)
      return
    }

    if (tts == null || !isReady) {
      pendingUtterance = PendingUtterance(key, message, prefs, urgent, fallbackTitle)
      ensureInitialized()
      if (initFailed) {
        pendingUtterance = null
        postFallbackNotification(urgent, fallbackTitle, message)
      }
      return
    }

    dispatchToTtsEngine(key, message, prefs, urgent, fallbackTitle)
  }

  private fun dispatchToTtsEngine(
    key: String,
    message: String,
    prefs: UserPreferences,
    urgent: Boolean,
    fallbackTitle: String,
  ) {
    // Recheck after lazy initialization: playback may have started while TTS bound.
    if (skipForPlayback()) {
      scheduleIdleShutdown()
      return
    }
    val engine = tts ?: return
    if (currentLocaleLang != prefs.voiceLanguage) {
      applyLocale(prefs.voiceLanguage)
    }

    val params =
      Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.voiceVolume.coerceIn(0f, 1f))
      }
    val queueMode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    val result = engine.speak(message, queueMode, params, key)
    if (result == TextToSpeech.ERROR) {
      postFallbackNotification(urgent, fallbackTitle, message)
    }
    scheduleIdleShutdown()
  }

  private fun skipForPlayback(): Boolean {
    if (!VoicePlaybackGate.shouldSkip(context)) return false
    pendingUtterance = null
    _assistantState.update { it.copy(lastSkippedReason = "Skipped: media/call active or audio state unavailable") }
    return true
  }

  private fun postFallbackNotification(urgent: Boolean, title: String, message: String) {
    if (urgent) {
      notificationHelper.postCriticalAlert(NotificationHelper.ID_TTS_FALLBACK, title, message)
    } else {
      notificationHelper.postAdvisory(NotificationHelper.ID_TTS_FALLBACK, title, message)
    }
  }

  @Synchronized
  fun shutdown() {
    mainHandler.removeCallbacks(autoShutdownRunnable)
    pendingUtterance = null
    runCatching {
      tts?.stop()
      tts?.shutdown()
    }
    tts = null
    isReady = false
    _assistantState.update { it.copy(isTtsBound = false) }
  }

  companion object {
    const val IDLE_SHUTDOWN_DELAY_MS = 20_000L
  }
}
