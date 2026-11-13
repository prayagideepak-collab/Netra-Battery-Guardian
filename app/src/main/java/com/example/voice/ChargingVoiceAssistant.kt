package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.example.data.local.prefs.UserPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChargingSpeedClass
import com.example.service.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargingVoiceAssistant @Inject constructor(
  @ApplicationContext private val context: Context,
  private val notificationHelper: NotificationHelper,
) : TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  @Volatile private var isReady: Boolean = false
  @Volatile private var initFailed: Boolean = false

  private val lastSpokenByKey = mutableMapOf<String, Long>()
  private var currentLocaleLang: AppLanguage? = null

  fun ensureInitialized() {
    if (tts == null && !initFailed) {
      runCatching {
        tts = TextToSpeech(context, this)
      }.onFailure {
        initFailed = true
      }
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isReady = true
      applyLocale(currentLocaleLang ?: AppLanguage.ENGLISH)
    } else {
      isReady = false
      initFailed = true
    }
  }

  private fun applyLocale(language: AppLanguage) {
    val engine = tts ?: return
    val locale =
      when (language) {
        AppLanguage.HINDI -> Locale("hi", "IN")
        AppLanguage.HINGLISH -> Locale("en", "IN")
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
      // Wraps midnight (e.g., 23:00 to 06:00)
      minuteOfDay >= start || minuteOfDay < end
    }
  }

  fun announceChargerConnected(
    speedClass: ChargingSpeedClass,
    prefs: UserPreferences,
    screenOn: Boolean,
  ) {
    // Never suppressed by Night Silent Mode
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
      bypassScreenOffRule = true,
      screenOn = screenOn,
      fallbackTitle = "Netra Charging",
    )
  }

  fun announceChargerDisconnected(prefs: UserPreferences, screenOn: Boolean) {
    // Must work at ANY time including Night Silent Mode
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

  fun announceChargingMilestone(level: Int, prefs: UserPreferences, screenOn: Boolean) {
    if (!prefs.announceCharging) return
    val isFull = level == 100
    // Night silent suppresses C5-C95, keeps full-battery alert
    if (isNightSilentActive(prefs) && !isFull) return

    val code = "C$level"
    val text =
      if (isFull) {
        when (prefs.voiceLanguage) {
          AppLanguage.ENGLISH -> "C100. Battery is fully charged. You can disconnect the charger."
          AppLanguage.HINDI -> "C100. बैटरी पूरी चार्ज हो गई है। आप चार्जर हटा सकते हैं।"
          AppLanguage.HINGLISH -> "C100. Battery full charge ho gayi hai. Charger hata sakte hain."
        }
      } else {
        code
      }
    speak(
      key = "milestone_$code",
      message = text,
      prefs = prefs,
      urgent = isFull,
      bypassNightSilent = isFull,
      bypassScreenOffRule = isFull,
      screenOn = screenOn,
      fallbackTitle = "Netra Charge $code",
    )
  }

  fun announceDischargingMilestone(level: Int, prefs: UserPreferences, screenOn: Boolean) {
    if (!prefs.announceDischarging) return
    // Night silent suppresses all D100-D1 percentage announcements
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
  ) {
    if (!prefs.voiceEnabled) {
      if (urgent) notificationHelper.postCriticalAlert(NotificationHelper.ID_TTS_FALLBACK, fallbackTitle, message)
      return
    }
    if (!bypassNightSilent && isNightSilentActive(prefs)) return
    if (!bypassScreenOffRule && !screenOn && !prefs.announceWhileScreenOff) return

    val now = System.currentTimeMillis()
    val last = lastSpokenByKey[key] ?: 0L
    // 30-second debounce rule
    if (now - last < 30_000L) return
    lastSpokenByKey[key] = now

    ensureInitialized()
    val engine = tts
    if (engine == null || !isReady || initFailed) {
      // Fallback to notification when TTS is unavailable
      if (urgent) {
        notificationHelper.postCriticalAlert(NotificationHelper.ID_TTS_FALLBACK, fallbackTitle, message)
      } else {
        notificationHelper.postAdvisory(NotificationHelper.ID_TTS_FALLBACK, fallbackTitle, message)
      }
      return
    }

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
      notificationHelper.postAdvisory(NotificationHelper.ID_TTS_FALLBACK, fallbackTitle, message)
    }
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
    isReady = false
  }
}
