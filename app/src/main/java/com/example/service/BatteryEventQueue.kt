package com.example.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One active evaluation. Coalesce repeats of the same bounded battery state, while
 * retaining observed levels and direction changes instead of losing milestones.
 */
internal class BatteryEventQueue<T>(
  scope: CoroutineScope,
  private val stateKey: (T) -> Any = { Unit },
  private val process: suspend (T, Boolean) -> Unit,
) {
  private val signals = Channel<Unit>(Channel.CONFLATED)
  private val lock = Any()
  private val pending = linkedMapOf<Any, Pair<T, Boolean>>()
  init {
    scope.launch {
      for (signal in signals) {
        while (true) {
          val event = synchronized(lock) {
            pending.entries.firstOrNull()?.let { entry -> pending.remove(entry.key) }
          } ?: break
          try {
            process(event.first, event.second)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (failure: Exception) {
            android.util.Log.w("GuardianEvents", "Battery evaluation failed", failure)
          }
        }
      }
    }
  }
  fun submit(value: T, force: Boolean = false) {
    synchronized(lock) {
      val key = stateKey(value)
      val old = pending.remove(key)
      pending[key] = value to (force || old?.second == true)
    }
    signals.trySend(Unit)
  }
}

/** Bounded keys: only OS level, plug, status and health enums. No per-event timestamps. */
internal fun batteryEventKey(intent: android.content.Intent?): Any = listOf(
  intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)?.coerceIn(-1, 100),
  intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)?.coerceIn(0, 16),
  intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 1)?.coerceIn(1, 5),
  intent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 1)?.coerceIn(1, 7),
)
