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
fun ConnectedBluetoothCard(isHindi: Boolean = false, measurementsAllowed: Boolean = com.example.domain.engine.MeasuredPowerBudget.permits(com.example.domain.model.PowerFeature.CONNECTED_ACCESSORY_READ)) {
  val canRead = measurementsAllowed && com.example.domain.engine.MeasuredPowerBudget.permits(com.example.domain.model.PowerFeature.CONNECTED_ACCESSORY_READ)
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
    }) { Text(if (permitted) { if (isHindi) "कनेक्शन रिफ्रेश करें" else "Refresh connections" } else { if (isHindi) "Nearby Devices की अनुमति दें" else "Allow Nearby Devices" }) }
    if (!permitted) Text(if (isHindi) "जुड़ी एक्सेसरी देखने के लिए Nearby Devices की अनुमति दें। लोकेशन एक्सेस नहीं चाहिए।" else "Allow Nearby Devices to view connected accessories. No location access is needed.")
    else if (readings.isEmpty()) Text(if (isHindi) "समर्थित जुड़ा डिवाइस उपलब्ध नहीं है। Bluetooth बंद हो सकता है।" else "No supported connected device reported. Bluetooth may be off.")
    readings.forEach { reading ->
      BluetoothDeviceTiles(reading, isHindi)
      if (reading.supportsGatt && canRead) {
        TextButton(onClick = { monitor.read(reading.id) }) { Text(if (isHindi) "BLE सिग्नल और बैटरी पढ़ें" else "Read BLE signal and battery") }
      }
    }
    if (!canRead) Text(if (isHindi) "वैकल्पिक BLE माप स्थगित: पावर बजट सत्यापित नहीं है। एक्सेसरी से संपर्क नहीं किया गया।" else "Optional BLE measurements deferred: power budget not verified. No accessory read requested.", style = MaterialTheme.typography.bodySmall)
    Text(if (isHindi) "कनेक्शन का पिछला रिफ्रेश दिखता है। जोड़ने या हटाने पर रिफ्रेश करें। समर्थित Battery Service डेटा मिलने पर ही बैटरी दिखती है। सिग्नल रंग RSSI के अनुमान हैं, दूरी या कनेक्शन टूटने की भविष्यवाणी नहीं। माप 30 सेकंड बाद हटते हैं।" else "Connection snapshot: refresh after connecting or disconnecting. Battery appears only if the device reports Battery Service data. Signal colors are RSSI estimates, not distance or disconnect predictions. Readings expire after 30 seconds.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
fun BluetoothDeviceTiles(reading: BluetoothReading, isHindi: Boolean = false) {
  val rssi = reading.rssi?.takeIf { it in -127..-1 }
  val englishLabel = bluetoothSignalLabel(rssi)
  val label = if (!isHindi || rssi == null) englishLabel else when {
    rssi >= -60 -> "मज़बूत सिग्नल (अनुमान)"
    rssi >= -80 -> "मध्यम सिग्नल (अनुमान)"
    else -> "कमज़ोर सिग्नल (अनुमान)"
  }
  val color = when {
    reading.rssi == null || reading.rssi !in -127..-1 -> Color.Gray
    reading.rssi >= -60 -> Color(0xFF50BFA3)
    reading.rssi >= -80 -> Color(0xFFE5B84C)
    else -> Color(0xFFEF6570)
  }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(reading.name, style = MaterialTheme.typography.titleMedium)
    Text(if (isHindi) "कनेक्टेड (पिछले रिफ्रेश पर)" else "Connected (last refresh)", style = MaterialTheme.typography.bodySmall)
    Text("${if (isHindi) "बैटरी" else "Battery"}: ${reading.batteryPercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "Unavailable"}")
    if (label == "Unavailable") Text(if (isHindi) "सिग्नल: Unavailable" else "Signal: Unavailable", color = color) else StatusPill(text = label, color = color)
    if (rssi != null) Text("$rssi dBm | ${if (isHindi) "दर्ज माप" else "measured sample"}", style = MaterialTheme.typography.bodySmall)
    else Text(if (isHindi) "इस डिवाइस के समर्थित API से सिग्नल उपलब्ध नहीं है।" else "Signal unavailable through this device's supported APIs.", style = MaterialTheme.typography.bodySmall)
  }
}
