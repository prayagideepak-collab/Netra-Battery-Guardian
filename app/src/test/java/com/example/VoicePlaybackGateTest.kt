package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.prefs.UserPreferences
import com.example.service.NotificationHelper
import com.example.voice.ChargingVoiceAssistant
import com.example.voice.VoicePlaybackGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoicePlaybackGateTest {
  @Test fun `gate skips music playback calls and failed observations`() {
    assertTrue(VoicePlaybackGate.evaluate({ true }, { false }, { false }))
    assertTrue(VoicePlaybackGate.evaluate({ false }, { true }, { false }))
    assertTrue(VoicePlaybackGate.evaluate({ false }, { false }, { true }))
    assertTrue(VoicePlaybackGate.evaluate({ error("unavailable") }, { false }, { false }))
    assertFalse(VoicePlaybackGate.evaluate({ false }, { false }, { false }))
  }

  @Test fun `active music skips TTS binding and does not control media`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val shadow = Shadows.shadowOf(audio)
    shadow.setIsMusicActive(true)
    val assistant = ChargingVoiceAssistant(context, NotificationHelper(context))
    assistant.announceChargingMilestone(80, UserPreferences(nightSilentEnabled = false), true)
    assertFalse(assistant.assistantState.value.isTtsBound)
    assertEquals(0, assistant.assistantState.value.announcementCount)
    assertTrue(audio.isMusicActive)
    assertTrue(assistant.assistantState.value.lastSkippedReason!!.contains("media/call"))
    assistant.shutdown()
  }
}
