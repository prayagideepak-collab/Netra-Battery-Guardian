package com.example.data.local.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BluetoothReading(
  val id: String,
  val name: String,
  val supportsGatt: Boolean = false,
  val batteryPercent: Int? = null,
  val rssi: Int? = null,
  val measuredAtMillis: Long? = null,
)

/** RSSI buckets are a signal estimate, never distance or a disconnect prediction. */
fun bluetoothSignalLabel(rssi: Int?): String = when {
  rssi == null || rssi !in -127..-1 -> "Unavailable"
  rssi >= -60 -> "Stronger signal (estimate)"
  rssi >= -80 -> "Intermediate signal (estimate)"
  else -> "Weaker signal (estimate)"
}

/** Foreground, user-requested reads only. No discovery, reflection, network or speech. */
@SuppressLint("MissingPermission")
class ConnectedBluetoothMonitor(private val context: Context) {
  private val manager = context.getSystemService(BluetoothManager::class.java)
  private val handler = Handler(Looper.getMainLooper())
  private val _devices = MutableStateFlow<List<BluetoothReading>>(emptyList())
  val devices = _devices.asStateFlow()
  private val clients = mutableSetOf<BluetoothGatt>()
  private val proxies = mutableMapOf<Int, BluetoothProfile>()
  private var closed = false
  private var generation = 0

  fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
    context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

  fun refresh() {
    generation++
    val requestGeneration = generation
    _devices.value = emptyList()
    closeConnections()
    if (closed || !permitted()) return
    val adapter = manager?.adapter ?: return
    if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return
    val gattDevices = runCatching { manager?.getConnectedDevices(BluetoothProfile.GATT) ?: emptyList() }.getOrDefault(emptyList())
    gattDevices.forEach { add(it, true) }
    for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
      runCatching {
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
          override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
            if (closed || requestGeneration != generation || !permitted()) {
              runCatching { adapter.closeProfileProxy(p, proxy) }; return
            }
            proxies[p]?.let { runCatching { adapter.closeProfileProxy(p, it) } }
            proxies[p] = proxy
            runCatching { proxy.connectedDevices }.getOrDefault(emptyList()).forEach { add(it, false) }
          }
          override fun onServiceDisconnected(p: Int) { if (requestGeneration == generation) refresh() }
        }, profile)
      }
    }
  }

  private fun add(device: BluetoothDevice, gatt: Boolean) {
    val id = runCatching { device.address }.getOrNull() ?: return
    val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Connected accessory"
    val existing = _devices.value.firstOrNull { it.id == id }
    val item = existing?.copy(supportsGatt = existing.supportsGatt || gatt) ?: BluetoothReading(id, name, gatt)
    _devices.value = (_devices.value.filterNot { it.id == id } + item).sortedBy { it.name }
  }

  fun read(id: String) {
    if (closed || !permitted() || _devices.value.none { it.id == id && it.supportsGatt }) return
    closeConnections()
    _devices.value = _devices.value.map { if (it.id == id) it.copy(rssi = null, batteryPercent = null, measuredAtMillis = null) else it }
    val callback = object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, state: Int) {
        handler.post {
          if (closed || !clients.contains(gatt) || !permitted()) { finish(gatt); return@post }
          if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
            runCatching { if (!gatt.readRemoteRssi()) gatt.discoverServices() }.onFailure { finish(gatt) }
          } else if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) finish(gatt)
        }
      }
      override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        handler.post {
          if (closed || !clients.contains(gatt) || !permitted()) return@post
          if (status == BluetoothGatt.GATT_SUCCESS && rssi in -127..-1) update(id) { it.copy(rssi = rssi, measuredAtMillis = System.currentTimeMillis()) }
          runCatching { if (!gatt.discoverServices()) finish(gatt) }.onFailure { finish(gatt) }
        }
      }
      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        handler.post {
          if (closed || !clients.contains(gatt) || !permitted()) return@post
          val characteristic = if (status == BluetoothGatt.GATT_SUCCESS) runCatching {
            gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
          }.getOrNull() else null
          if (characteristic == null) finish(gatt)
          else runCatching { if (!gatt.readCharacteristic(characteristic)) finish(gatt) }.onFailure { finish(gatt) }
        }
      }
      @Deprecated("Legacy callback required on Android 7-12")
      override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        @Suppress("DEPRECATION") val bytes = characteristic.value?.clone() ?: byteArrayOf()
        accept(gatt, characteristic.uuid, bytes, status)
      }
      override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        accept(gatt, characteristic.uuid, value.clone(), status)
      }
      private fun accept(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        handler.post {
          if (closed || !clients.contains(gatt) || !permitted()) return@post
          val level = value.firstOrNull()?.toInt()?.and(255)
          if (uuid == BATTERY_LEVEL && status == BluetoothGatt.GATT_SUCCESS && level != null && level in 0..100) {
            update(id) { it.copy(batteryPercent = level, measuredAtMillis = System.currentTimeMillis()) }
          }
          finish(gatt)
        }
      }
    }
    val device = runCatching { manager?.adapter?.getRemoteDevice(id) }.getOrNull() ?: return
    val client = runCatching { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull() ?: return
    clients.add(client)
    handler.postDelayed({ finish(client) }, 12_000L)
    // Clear old samples rather than displaying them as a live signal.
    handler.postDelayed({ update(id) { it.copy(rssi = null, batteryPercent = null, measuredAtMillis = null) } }, 30_000L)
  }

  private fun update(id: String, change: (BluetoothReading) -> BluetoothReading) {
    if (!closed) _devices.value = _devices.value.map { if (it.id == id) change(it) else it }
  }
  private fun finish(gatt: BluetoothGatt) {
    clients.remove(gatt)
    runCatching { gatt.disconnect() }
    runCatching { gatt.close() }
  }
  private fun closeConnections() { clients.toList().forEach(::finish) }
  fun pause() {
    generation++
    handler.removeCallbacksAndMessages(null)
    closeConnections()
    proxies.forEach { (profile, proxy) -> runCatching { manager?.adapter?.closeProfileProxy(profile, proxy) } }
    proxies.clear()
    _devices.value = emptyList()
  }
  fun close() { closed = true; pause() }
  companion object {
    private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
  }
}
