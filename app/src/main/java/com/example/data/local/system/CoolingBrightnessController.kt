package com.example.data.local.system

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CoolingBrightnessState(
  val enabled: Boolean = false,
  val restorePending: Boolean = false,
  val status: String = "Cooling brightness is off.",
)

internal interface BrightnessIo {
  fun permitted(): Boolean
  fun brightness(): Int
  fun mode(): Int
  fun brightness(value: Int): Boolean
  fun mode(value: Int): Boolean
}

internal class AndroidBrightnessIo(private val context: Context) : BrightnessIo {
  override fun permitted() = Settings.System.canWrite(context)
  override fun brightness() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
  override fun mode() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
  override fun brightness(value: Int) = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
  override fun mode(value: Int) = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, value)
}

/** One cooling owner with durable write-ahead originals. Old system permission is never consent. */
internal class CoolingBrightnessController(
  context: Context,
  private val io: BrightnessIo = AndroidBrightnessIo(context),
  private val persist: (SharedPreferences.Editor) -> Boolean = { it.commit() },
) {
  private val prefs = context.getSharedPreferences("guardian_cooling_consent_v1", Context.MODE_PRIVATE)
  private var storageFailed = false
  private val mutableState = MutableStateFlow(CoolingBrightnessState())
  val state: StateFlow<CoolingBrightnessState> = mutableState.asStateFlow()
  init { publish() }

  private fun save(editor: SharedPreferences.Editor) {
    try { check(persist(editor)) } catch (error: Exception) { storageFailed = true; throw error }
  }
  private fun owned() = prefs.contains("before_b") && prefs.contains("before_m")
  private fun publish(message: String? = null) {
    mutableState.value = CoolingBrightnessState(
      enabled = prefs.getBoolean("enabled", false),
      restorePending = owned() && (storageFailed || !prefs.getBoolean("complete", false) || prefs.contains("error")),
      status = message ?: prefs.getString("error", null)
        ?: if (owned()) "Original settings saved for cooling." else "Cooling brightness is off or not applied.",
    )
  }
  private fun error(message: String) {
    runCatching { save(prefs.edit().putString("error", message)) }
    publish(message)
  }
  private fun clear(external: Boolean = false) {
    save(prefs.edit().remove("before_b").remove("before_m").remove("applied_b")
      .remove("complete").remove("previous_b").remove("error").putBoolean("external", external))
  }

  @Synchronized fun setEnabled(enabled: Boolean) {
    try {
      save(prefs.edit().putBoolean("enabled", enabled).putBoolean("external", false))
      if (!enabled) restoreInternal() else publish()
    } catch (_: Exception) { error("Could not save consent. New changes paused; retry or revoke Android permission.") }
  }

  /** null means temperature is unknown: do not claim that a thermal session ended. */
  @Synchronized fun reconcile(active: Boolean?, target: Int = 95): Boolean {
    if (!prefs.getBoolean("enabled", false)) { restoreInternal(); return false }
    if (active == false) { restoreInternal(); return false }
    if (storageFailed) { publish("Settings storage unavailable. New changes paused."); return false }
    if (active == null) { publish("Temperature unavailable; no new brightness change."); return false }
    return try {
      if (owned() && (!prefs.getBoolean("complete", false) || prefs.contains("error"))) {
        restoreInternal(); return false
      }
      if (owned() && (io.brightness() != prefs.getInt("applied_b", -1) || io.mode() != 0)) {
        clear(external = true)
        publish("Manual change kept; cooling will not overwrite it.")
        return false
      }
      if (prefs.getBoolean("external", false)) { publish("Manual change kept until cooling ends."); return false }
      if (!io.permitted()) { publish("Android brightness permission required. No new change."); return false }
      val brightness = io.brightness()
      val mode = io.mode()
      check(brightness in 0..255 && mode in 0..1)
      val next = target.coerceIn(30, 255)
      if (brightness <= next) { publish(); return owned() }
      val edit = prefs.edit().putInt("previous_b", brightness).putInt("applied_b", next).putBoolean("complete", false).remove("error")
      if (!owned()) edit.putInt("before_b", brightness).putInt("before_m", mode)
      save(edit) // Nothing is changed without durable originals and intended values.
      check(io.mode(0)); check(io.mode() == 0)
      check(io.brightness(next)); check(io.brightness() == next)
      save(prefs.edit().putBoolean("complete", true))
      publish()
      true
    } catch (_: Exception) {
      error("Restore pending: brightness change failed. Retry restoration.")
      restoreInternal()
      false
    }
  }

  @Synchronized fun retryRestore() { restoreInternal() }

  private fun restoreInternal() {
    if (!owned()) {
      if (prefs.getBoolean("external", false) || prefs.contains("error")) runCatching { save(prefs.edit().putBoolean("external", false).remove("error")) }
      publish()
      return
    }
    try {
      check(io.permitted())
      val beforeB = prefs.getInt("before_b", -1)
      val beforeM = prefs.getInt("before_m", -1)
      val appliedB = prefs.getInt("applied_b", -1)
      check(beforeB in 0..255 && beforeM in 0..1 && appliedB in 0..255)
      val b = io.brightness()
      val m = io.mode()
      // A completed application owns exactly this pair. A different later pair belongs to the user.
      // For an interrupted write, either original or intended values are the only recoverable pair.
      val ours = if (prefs.getBoolean("complete", false)) b == appliedB && m == 0
        else b in setOf(beforeB, appliedB, prefs.getInt("previous_b", appliedB)) && m in setOf(beforeM, 0)
      if (!ours) { clear(external = true); publish("Manual settings kept. No restore overwrote them."); return }
      // Persist the recovery phase first, allowing restart after only brightness was restored.
      save(prefs.edit().putBoolean("complete", false))
      if (b != beforeB) { check(io.brightness(beforeB)); check(io.brightness() == beforeB) }
      if (m != beforeM) { check(io.mode(beforeM)); check(io.mode() == beforeM) }
      clear()
      publish("Original brightness settings restored.")
    } catch (_: Exception) { error("Restore pending: allow Android brightness permission and retry.") }
  }
}
