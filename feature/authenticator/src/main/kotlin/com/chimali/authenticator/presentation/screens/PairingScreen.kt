package com.chimali.authenticator.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.model.ConnectionState
import com.chimali.authenticator.domain.model.Platform
import com.chimali.authenticator.presentation.viewmodel.PairingViewModel
import com.chimali.authenticator.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FIDO2 Authenticator",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pair with desktop computers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Service status indicator
            StatusCard(
                title = "Service Status",
                subtitle = if (uiState.isServiceActive) "Active" else "Inactive",
                status = if (uiState.isServiceActive) Status.Success else Status.Warning,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Discovery section
        DiscoverySection(
            isDiscovering = uiState.isDiscovering,
            onStartDiscovery = { viewModel.startDiscovery() },
            onStopDiscovery = { viewModel.stopDiscovery() }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Paired devices section
        PairedDevicesSection(
            devices = uiState.pairedDevices,
            connections = uiState.connections,
            onConnect = { viewModel.connectToDevice(it) },
            onDisconnect = { viewModel.disconnectFromDevice(it) },
            onUnpair = { viewModel.unpairDevice(it) },
            onToggleTrust = { deviceId, isTrusted -> viewModel.updateTrustStatus(deviceId, isTrusted) },
            isLoading = uiState.isLoading
        )
        
        // Error handling
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusCard(
                title = "Error",
                subtitle = error,
                status = Status.Error
            )
        }
    }
}

@Composable
private fun DiscoverySection(
    isDiscovering: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Discovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isDiscovering) "Scanning for nearby devices..." else "Start scanning to find devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isDiscovering) {
                    SecondaryButton(
                        text = "Stop",
                        onClick = onStopDiscovery,
                        icon = Icons.Default.Stop,
                        modifier = Modifier.width(100.dp)
                    )
                } else {
                    PrimaryButton(
                        text = "Start",
                        onClick = onStartDiscovery,
                        icon = Icons.Default.Search,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
            
            if (isDiscovering) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PairedDevicesSection(
    devices: List<PairedDevice>,
    connections: List<BluetoothHidConnection>,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onUnpair: (String) -> Unit,
    onToggleTrust: (String, Boolean) -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paired Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${devices.size} devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Loading devices..."
                )
            } else if (devices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No paired devices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Start device discovery to pair with computers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        val connection = connections.find { it.deviceId == device.deviceId }
                        PairedDeviceItem(
                            device = device,
                            connection = connection,
                            onConnect = { onConnect(device.deviceId) },
                            onDisconnect = { onDisconnect(device.deviceId) },
                            onUnpair = { onUnpair(device.deviceId) },
                            onToggleTrust = { isTrusted -> onToggleTrust(device.deviceId, isTrusted) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairedDeviceItem(
    device: PairedDevice,
    connection: BluetoothHidConnection?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onUnpair: () -> Unit,
    onToggleTrust: (Boolean) -> Unit
) {
    var showUnpairDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Device info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getPlatformIcon(device.platform),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Platform: ${device.platform.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "ID: ${device.deviceId.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (device.isTrusted) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Trusted Device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // Connection status
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    when (connection?.connectionState) {
                        ConnectionState.CONNECTED -> {
                            StatusCard(
                                title = "Connected",
                                status = Status.Success,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        ConnectionState.CONNECTING -> {
                            StatusCard(
                                title = "Connecting...",
                                status = Status.Info,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        ConnectionState.DISCONNECTED -> {
                            StatusCard(
                                title = "Disconnected",
                                status = Status.Warning,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        ConnectionState.ERROR -> {
                            StatusCard(
                                title = "Error",
                                status = Status.Error,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        null -> {
                            StatusCard(
                                title = "Not Connected",
                                status = Status.Warning,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (connection?.connectionState) {
                    ConnectionState.CONNECTED -> {
                        SecondaryButton(
                            text = "Disconnect",
                            onClick = onDisconnect,
                            icon = Icons.Default.BluetoothDisabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ConnectionState.DISCONNECTED, null -> {
                        PrimaryButton(
                            text = "Connect",
                            onClick = onConnect,
                            icon = Icons.Default.Bluetooth,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ConnectionState.CONNECTING -> {
                        SecondaryButton(
                            text = "Connecting...",
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        PrimaryButton(
                            text = "Retry",
                            onClick = onConnect,
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Trust toggle
                SecondaryButton(
                    text = if (device.isTrusted) "Untrust" else "Trust",
                    onClick = { onToggleTrust(!device.isTrusted) },
                    icon = if (device.isTrusted) Icons.Default.Security else Icons.Default.Security,
                    modifier = Modifier.weight(1f)
                )
                
                // Unpair button
                SecondaryButton(
                    text = "Unpair",
                    onClick = { showUnpairDialog = true },
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    
    // Unpair confirmation dialog
    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Unpair Device") },
            text = { Text("Are you sure you want to unpair ${device.deviceName}? This will remove all connection data and settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnpair()
                        showUnpairDialog = false
                    }
                ) {
                    Text("Unpair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun getPlatformIcon(platform: Platform) = when (platform) {
    Platform.WINDOWS -> Icons.Default.Computer
    Platform.MACOS -> Icons.Default.LaptopMac
    Platform.LINUX -> Icons.Default.Computer
    Platform.UNKNOWN -> Icons.Default.Devices
}
