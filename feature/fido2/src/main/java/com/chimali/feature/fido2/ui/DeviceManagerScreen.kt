package com.chimali.feature.fido2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PairedDevice(
    val address: String,
    val name: String,
    val isConnected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagerScreen(
    devices: List<PairedDevice>,
    onDisconnect: (PairedDevice) -> Unit,
    onUnpair: (PairedDevice) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Paired Devices") })
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No paired devices",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(device, onDisconnect, onUnpair)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: PairedDevice,
    onDisconnect: (PairedDevice) -> Unit,
    onUnpair: (PairedDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                contentDescription = if (device.isConnected) "Connected" else "Disconnected",
                tint = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (device.isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (device.isConnected) {
                TextButton(onClick = { onDisconnect(device) }) {
                    Text("Disconnect")
                }
            }
            TextButton(onClick = { onUnpair(device) }) {
                Text("Unpair", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
