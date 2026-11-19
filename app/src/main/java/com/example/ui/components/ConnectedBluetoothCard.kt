package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.system.BluetoothReading
import com.example.data.local.system.ConnectedBluetoothMonitor
import com.example.data.local.system.bluetoothSignalLabel

@Composable
fun ConnectedBluetoothCard(isHindi: Boolean = false, measurementsAllowed: Boolean = true) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val monitor = remember { ConnectedBluetoothMonitor(context.applicationContext) }
  val readings by monitor.devices.collectAsState()
  var permitted by remember { mutableStateOf(monitor.permitted()) }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    permitted = it
    if (it) monitor.refresh()
  }
  DisposableEffect(monitor, lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) { permitted = monitor.permitted(); monitor.refresh() }
      if (event == Lifecycle.Event.ON_STOP) monitor.pause()
    }
    lifecycle.addObserver(observer)
    monitor.refresh()
    onDispose { lifecycle.removeObserver(observer); monitor.close() }
  }
  GuardianCard {
    Text(if (isHindi) "Bluetooth डिवाइस" else "Bluetooth devices", style = MaterialTheme.typography.titleLarge)
    Text(if (isHindi) "केवल सामने खुली स्क्रीन पर। कोई स्कैन या आवाज़ नहीं।" else "Foreground only. No discovery or voice announcements.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
      permitted = monitor.permitted()
      if (!permitted && Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
      else monitor.refresh()
    }) { Text(if (permitted) "Refresh connections" else "Allow Nearby Devices") }
    if (!permitted) Text("Allow Nearby Devices to view connected accessories. No location access is needed.")
    else if (readings.isEmpty()) Text("No supported connected device reported. Bluetooth may be off.")
    readings.forEach { reading ->
      BluetoothDeviceTiles(reading)
      if (reading.supportsGatt && measurementsAllowed) {
        TextButton(onClick = { monitor.read(reading.id) }) { Text("Read BLE signal and battery") }
      } else if (!measurementsAllowed) Text("Optional BLE measurements paused in power-saving tier.", style = MaterialTheme.typography.bodySmall)
    }
    Text("Connection snapshot: refresh after connecting or disconnecting. Battery appears only if the device reports Battery Service data. Signal colors are RSSI estimates, not distance or disconnect predictions. Readings expire after 30 seconds.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
fun BluetoothDeviceTiles(reading: BluetoothReading) {
  val label = bluetoothSignalLabel(reading.rssi)
  val color = when {
    reading.rssi == null || reading.rssi !in -127..-1 -> Color.Gray
    reading.rssi >= -60 -> Color(0xFF50BFA3)
    reading.rssi >= -80 -> Color(0xFFE5B84C)
    else -> Color(0xFFEF6570)
  }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(reading.name, style = MaterialTheme.typography.titleMedium)
    Text("Connected (last refresh)", style = MaterialTheme.typography.bodySmall)
    Text("Battery: ${reading.batteryPercent?.let { "$it%" }.orEmpty()}")
    if (label == "Unavailable") Text("Signal: Unavailable", color = color) else StatusPill(text = label, color = color)
    if (reading.rssi != null) Text("${reading.rssi} dBm | measured sample", style = MaterialTheme.typography.bodySmall)
    else Text("Signal unavailable through this device's supported APIs.", style = MaterialTheme.typography.bodySmall)
  }
}
