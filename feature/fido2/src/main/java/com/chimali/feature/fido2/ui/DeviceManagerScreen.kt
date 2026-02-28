package com.chimali.feature.fido2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.clickable

import androidx.compose.material3.pulltorefresh.PullToRefreshBox

data class PairedDevice(
    val address: String,
    val name: String,
    val isConnected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagerScreen(
    devices: List<PairedDevice>,
    availableDevices: List<PairedDevice>,
    recentDevices: List<PairedDevice>,
    isRefreshing: Boolean,
    isBlePeripheralSupported: Boolean,
    onRefresh: () -> Unit,
    onPair: (PairedDevice) -> Unit,
    onConnect: (PairedDevice) -> Unit,
    onDisconnect: (PairedDevice) -> Unit,
    onUnpair: (PairedDevice) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Device Manager") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRefresh,
                icon = { Icon(Icons.Default.Search, contentDescription = "Scan") },
                text = { Text(if (isRefreshing) "Scanning..." else "Scan for Devices") }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (devices.isEmpty() && availableDevices.isEmpty() && recentDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No devices or recent connections found. Pull to scan.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isBlePeripheralSupported) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Hardware Limited",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "This phone does not support BLE Peripheral mode, which is required for FIDO2 over Bluetooth. Only USB mode will work.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    if (recentDevices.isNotEmpty()) {
                        item {
                            Text(
                                "Recent Devices",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(recentDevices) { device ->
                            DeviceCard(device, onConnect, onDisconnect, onUnpair)
                        }
                    }
                    if (devices.isNotEmpty()) {
                        item {
                            Text(
                                "Paired Devices",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(devices) { device ->
                            DeviceCard(device, onConnect, onDisconnect, onUnpair)
                        }
                    }
                    
                    if (availableDevices.isNotEmpty()) {
                        item {
                            Text(
                                "Available Devices",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(availableDevices) { device ->
                            AvailableDeviceCard(device, onPair)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: PairedDevice,
    onConnect: (PairedDevice) -> Unit,
    onDisconnect: (PairedDevice) -> Unit,
    onUnpair: (PairedDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { if (!device.isConnected) onConnect(device) }
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
            } else {
                TextButton(onClick = { onConnect(device) }) {
                    Text("Connect")
                }
            }
            TextButton(onClick = { onUnpair(device) }) {
                Text("Unpair", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AvailableDeviceCard(
    device: PairedDevice,
    onPair: (PairedDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPair(device) }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Available",
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onPair(device) }) {
                Text("Pair")
            }
        }
    }
}
