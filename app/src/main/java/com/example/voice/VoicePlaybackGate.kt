package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.os.Build

/** Best-effort local observation only. Never requests focus or controls playback. */
internal object VoicePlaybackGate {
  fun shouldSkip(context: Context): Boolean = try {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio == null) true else evaluate(
      music = { audio.isMusicActive },
      playback = { Build.VERSION.SDK_INT >= 26 && audio.activePlaybackConfigurations.isNotEmpty() },
      call = { audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION },
    )
  } catch (_: Exception) { true }

  internal fun evaluate(music: () -> Boolean, playback: () -> Boolean, call: () -> Boolean): Boolean = try {
    music() || playback() || call()
  } catch (_: Exception) { true }
}
