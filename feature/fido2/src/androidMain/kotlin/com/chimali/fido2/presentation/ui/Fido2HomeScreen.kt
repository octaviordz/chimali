package com.chimali.fido2.presentation.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.R
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel
import com.chimali.fido2.presentation.viewmodel.TransportToggleAction
import com.chimali.fido2.presentation.viewmodel.dashboardStateForTransport
import com.chimali.fido2.presentation.viewmodel.isTransportStartEnabled
import com.chimali.fido2.presentation.viewmodel.transportToggleAction
import org.koin.compose.viewmodel.koinViewModel

private const val DISCOVERABLE_DURATION_SECONDS = 120
private const val PULSE_ANIMATION_DURATION_MS = 1500
private const val PULSE_START_ALPHA = 0.5f
private const val PULSE_TARGET_SCALE = 2.5f

private const val COLOR_ADVERTISING_VAL = 0xFF6200EE
private val COLOR_ADVERTISING = Color(COLOR_ADVERTISING_VAL)

private const val COLOR_CONNECTING_VAL = 0xFFFF9800
private val COLOR_CONNECTING = Color(COLOR_CONNECTING_VAL)

private const val COLOR_CONNECTED_VAL = 0xFF4CAF50
private val COLOR_CONNECTED = Color(COLOR_CONNECTED_VAL)

/**
 * T156a — Authenticator Dashboard (Home Screen).
 * Provides status monitoring and transport control for the FIDO2 module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Req("FR-BLE-070", "FR-BT-SEL-050")
fun Fido2HomeScreen(
    onManageCredentials: () -> Unit,
    onRegisterRequest: () -> Unit,
    onAuthenticateRequest: () -> Unit,
    onEditDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: Fido2HomeViewModel = koinViewModel(),
    pairedDevicesViewModel: PairedDevicesViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val processState by viewModel.processTransportState.collectAsState()
    val outboundBleUnsupported by viewModel.outboundBleUnsupported.collectAsState()
    val connectedDisplayName by viewModel.connectedDeviceDisplayName.collectAsState()
    val showPairingHelp = remember { mutableStateOf(false) }
    val selectedMode = processState.processChoice
    val toggleAction = transportToggleAction(connectionState, processState.availability)
    val dashboardConnectionState = dashboardStateForTransport(connectionState, processState.availability)

    Fido2EventObserver(
        pendingRegistration = viewModel.pendingRegistrationRequest,
        pendingAuthentication = viewModel.pendingAuthenticationRequest,
        onRegisterRequest = onRegisterRequest,
        onAuthenticateRequest = onAuthenticateRequest,
    )

    val showBluetoothError = remember { mutableStateOf(false) }

    val bluetoothDiscoverableLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_CANCELED) {
                viewModel.toggleTransport()
            } else {
                // User denied or failed to enable Bluetooth/Discoverable
                showBluetoothError.value = true
            }
        }

    val bluetoothPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            if (permissions.values.all { it }) {
                if (selectedMode == BluetoothTransportMode.CLASSIC_HID) {
                    launchDiscoverability(bluetoothDiscoverableLauncher) { showBluetoothError.value = true }
                } else if (selectedMode == BluetoothTransportMode.FIDO_BLE) {
                    viewModel.toggleTransport()
                }
            } else {
                showBluetoothError.value = true
            }
        }

    val handleToggle = {
        val isRunning =
            connectionState !is Fido2ConnectionState.Idle &&
                connectionState !is Fido2ConnectionState.Error
        if (isRunning) {
            viewModel.toggleTransport()
        } else {
            val required = getRequiredPermissions()
            if (required.isNotEmpty() && !hasPermissions(context, required)) {
                bluetoothPermissionLauncher.launch(required)
            } else if (selectedMode == BluetoothTransportMode.CLASSIC_HID) {
                launchDiscoverability(bluetoothDiscoverableLauncher) { showBluetoothError.value = true }
            } else if (selectedMode == BluetoothTransportMode.FIDO_BLE) {
                viewModel.toggleTransport()
            }
        }
    }

    if (showBluetoothError.value) {
        BluetoothErrorDialog(
            onDismiss = { showBluetoothError.value = false },
            onRetry = handleToggle,
        )
    }

    if (showPairingHelp.value) {
        PairingRecoveryDialog(
            onDismiss = { showPairingHelp.value = false },
            onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onRetry = {
                showPairingHelp.value = false
                viewModel.retryTransport()
            },
        )
    }

    if (outboundBleUnsupported) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOutboundBleUnsupported,
            title = { Text(stringResource(R.string.fido_ble_outbound_unsupported_title)) },
            text = { Text(stringResource(R.string.fido_ble_outbound_unsupported_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOutboundBleUnsupported) {
                    Text(stringResource(R.string.fido_ble_pairing_help_dismiss))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Chimali Authenticator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                actions = {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pairedDevices by pairedDevicesViewModel.pairedDevices.collectAsState()
            StatusIndicator(
                state = dashboardConnectionState,
                displayName = connectedDisplayName,
                hasPairedDevices = pairedDevices.isNotEmpty(),
            )

            if (selectedMode?.let { shouldShowPairingHelp(it, connectionState) } == true) {
                PairingHelpButton(onClick = { showPairingHelp.value = true })
            }

            PairedDevicesSection(
                modifier = Modifier.weight(1f),
                onEditDevice = onEditDevice,
                devices = pairedDevices,
                onPendingRemove = { device -> pairedDevicesViewModel.pendingRemove(device) },
                onUndoRemove = { macAddress -> pairedDevicesViewModel.undoRemove(macAddress) },
                onCommitRemove = { macAddress -> pairedDevicesViewModel.commitRemove(macAddress) },
                removalEvents = pairedDevicesViewModel.removalEvents,
                onDeviceClick = { device -> viewModel.connectDevice(device.macAddress) },
            )

            TransportToggleButton(
                connectionState = dashboardConnectionState,
                retryAvailable = toggleAction == TransportToggleAction.Retry,
                onToggle = handleToggle,
                enabled = isTransportStartEnabled(selectedMode) || connectionState !is Fido2ConnectionState.Idle,
            )

            ChimaliOutlinedButton(
                onClick = onManageCredentials,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Saved Passkeys")
            }

            selectedMode?.let { mode ->
                Text(
                    text =
                        if (mode == BluetoothTransportMode.FIDO_BLE) {
                            stringResource(R.string.fido_transport_ble)
                        } else {
                            stringResource(R.string.fido_transport_classic_hid)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Displays the current Bluetooth HID connection status and any error messages.
 */
@Composable
fun StatusIndicator(
    state: Fido2ConnectionState,
    displayName: String?,
    modifier: Modifier = Modifier,
    hasPairedDevices: Boolean = false,
) {
    val title: String
    val subtitle: String
    val color: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    when (state) {
        is Fido2ConnectionState.Idle -> {
            title = "Ready to Start"
            subtitle = "Tap 'Start Authenticator' to begin"
            color = MaterialTheme.colorScheme.outline
            icon = Icons.Default.Bluetooth
        }

        is Fido2ConnectionState.Advertising -> {
            if (hasPairedDevices) {
                title = "Ready for Authentication"
                subtitle = "Waiting for request from PC"
            } else {
                title = "Advertising..."
                subtitle = "Ready to pair with a new host PC"
            }
            color = COLOR_ADVERTISING
            icon = Icons.AutoMirrored.Filled.BluetoothSearching
        }

        is Fido2ConnectionState.Connecting -> {
            title = "Connecting..."
            subtitle = "Establishing Bluetooth HID connection"
            color = COLOR_CONNECTING
            icon = Icons.Default.BluetoothAudio
        }

        is Fido2ConnectionState.Connected -> {
            title = displayName ?: "Connected"
            subtitle = "Connected to PC"
            color = COLOR_CONNECTED
            icon = Icons.Default.Devices
        }

        is Fido2ConnectionState.Error -> {
            title = "Error Occurred"
            subtitle = state.message
            color = MaterialTheme.colorScheme.error
            icon = Icons.Default.Error
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (state is Fido2ConnectionState.Advertising || state is Fido2ConnectionState.Connecting) {
                    PulseAnimation(color = color)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = color,
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (state is Fido2ConnectionState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            color.copy(alpha = 0.8f)
                        },
                )
            }
        }
    }
}

/**
 * Observes retained pending requests so navigation cannot miss a non-replaying event during startup.
 */
@Composable
internal fun PairingHelpButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.fido_ble_pairing_help_button))
    }
}

internal fun shouldShowPairingHelp(
    mode: BluetoothTransportMode,
    connectionState: Fido2ConnectionState,
): Boolean =
    mode == BluetoothTransportMode.FIDO_BLE &&
        connectionState !is Fido2ConnectionState.Connecting &&
        connectionState !is Fido2ConnectionState.Connected

@Composable
internal fun PairingRecoveryDialog(
    onDismiss: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fido_ble_pairing_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fido_ble_pairing_help_body))
                TextButton(onClick = onOpenBluetoothSettings) {
                    Text(stringResource(R.string.fido_ble_pairing_help_open_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fido_ble_pairing_help_dismiss))
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.fido_ble_pairing_help_retry))
            }
        },
    )
}

@Composable
private fun Fido2EventObserver(
    pendingRegistration: kotlinx.coroutines.flow.StateFlow<Fido2UiEvent.RegistrationRequested?>,
    pendingAuthentication: kotlinx.coroutines.flow.StateFlow<Fido2UiEvent.AuthenticationRequested?>,
    onRegisterRequest: () -> Unit,
    onAuthenticateRequest: () -> Unit,
) {
    val registration by pendingRegistration.collectAsState()
    val authentication by pendingAuthentication.collectAsState()
    val latestOnRegister by rememberUpdatedState(onRegisterRequest)
    val latestOnAuthenticate by rememberUpdatedState(onAuthenticateRequest)

    LaunchedEffect(registration?.deferred) {
        if (registration != null) latestOnRegister()
    }
    LaunchedEffect(authentication?.deferred, registration?.deferred) {
        if (authentication != null && registration == null) latestOnAuthenticate()
    }
}

/**
 * A dialog that explains why Bluetooth and Nearby Devices permissions are required.
 */
@Composable
private fun BluetoothErrorDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Required") },
        text = {
            Text(
                "Chimali Authenticator requires Bluetooth and \"Nearby Devices\" permissions " +
                    "to act as a security key. Allow the requested permissions to continue.",
            )
        },
        dismissButton = {
            TextButton(onClick = {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Settings")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRetry()
            }) {
                Text("Retry")
            }
        },
    )
}

private fun getRequiredPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        }

        else -> {
            emptyArray()
        }
    }

private fun hasPermissions(
    context: Context,
    permissions: Array<String>,
): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun launchDiscoverability(
    launcher: ActivityResultLauncher<Intent>,
    onError: () -> Unit,
) {
    try {
        val intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
            }
        launcher.launch(intent)
    } catch (e: SecurityException) {
        Logger.e(e) { "Fido2HomeScreen: SecurityException launching discoverability" }
        onError()
    }
}

/**
 * A button that starts, stops or retries the selected Bluetooth transport.
 */
@Composable
fun TransportToggleButton(
    connectionState: Fido2ConnectionState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    retryAvailable: Boolean = connectionState is Fido2ConnectionState.Error,
    enabled: Boolean = true,
) {
    val isRunning =
        connectionState !is Fido2ConnectionState.Idle &&
            connectionState !is Fido2ConnectionState.Error &&
            !retryAvailable

    ChimaliButton(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (isRunning) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                contentColor =
                    if (isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
            ),
    ) {
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TransportToggle",
        ) { running ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        when {
                            retryAvailable || connectionState is Fido2ConnectionState.Error -> Icons.Default.Refresh
                            running -> Icons.Default.Stop
                            else -> Icons.Default.PlayArrow
                        },
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        retryAvailable || connectionState is Fido2ConnectionState.Error -> "Retry Authenticator"
                        running -> "Stop Authenticator"
                        else -> "Start Authenticator"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * An animated pulse effect shown when the authenticator is advertising or connecting.
 */
@Composable
fun PulseAnimation(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_TARGET_SCALE,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PULSE_ANIMATION_DURATION_MS, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = PULSE_START_ALPHA,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PULSE_ANIMATION_DURATION_MS, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "alpha",
    )

    Box(
        modifier =
            modifier
                .size(80.dp)
                .clip(CircleShape)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }.background(color),
    )
}
