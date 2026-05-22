package com.chimali.fido2.presentation.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
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
    val connectedDisplayName by viewModel.connectedDeviceDisplayName.collectAsState()

    val updatedOnRegisterRequest by rememberUpdatedState(onRegisterRequest)
    val updatedOnAuthenticateRequest by rememberUpdatedState(onAuthenticateRequest)

    // Observe incoming FIDO2 events (e.g. from PC via Bluetooth).
    // Single LaunchedEffect so both live collectors share the same composable lifecycle.
    LaunchedEffect(Unit) {
        // One-time startup check: events that arrived before this screen entered composition.
        if (viewModel.getPendingRegistration() != null) {
            updatedOnRegisterRequest()
        } else if (viewModel.getPendingAuthentication() != null) {
            // Only check auth if there is no pending registration (registration takes priority
            // when both arrive simultaneously, which shouldn't happen in practice).
            updatedOnAuthenticateRequest()
        }

        // Launch both live collectors in parallel inside this scope so they are both
        // cancelled together when the composable leaves composition.
        coroutineScope {
            launch {
                viewModel.uiEvents
                    .filterIsInstance<Fido2UiEvent.RegistrationRequested>()
                    .collect { updatedOnRegisterRequest() }
            }
            launch {
                viewModel.uiEvents
                    .filterIsInstance<Fido2UiEvent.AuthenticationRequested>()
                    .collect {
                        // Fix D — Guard against navigating to auth while registration is active.
                        // webauthn.io and some other RPs send a concurrent GetAssertion on a
                        // second CTAP2 channel while MakeCredential is still in progress.
                        // Registration takes priority: if a registration is already pending,
                        // skip the auth navigation entirely (the CTAP2 handler will time out
                        // or return CHANNEL_BUSY via the Mutex guard in Fix A).
                        if (viewModel.getPendingRegistration() == null) {
                            updatedOnAuthenticateRequest()
                        } else {
                            Logger.w { "Fido2HomeScreen: Ignoring AuthenticationRequested — registration is active" }
                        }
                    }
            }
        }
    }

    var showBluetoothError by remember { mutableStateOf(false) }

    val bluetoothDiscoverableLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            // For ACTION_REQUEST_DISCOVERABLE, result.resultCode is the duration of discoverability in seconds,
            // or Activity.RESULT_CANCELED (0) if the user denied it.
            if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
                // User enabled Bluetooth and/or discoverability, proceed with starting the transport
                viewModel.toggleTransport()
            } else {
                // User denied or failed to enable Bluetooth/Discoverable
                showBluetoothError = true
            }
        }

    val bluetoothPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                try {
                    val discoverableIntent =
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
                        }
                    bluetoothDiscoverableLauncher.launch(discoverableIntent)
                } catch (e: SecurityException) {
                    Logger.e(e) { "Fido2HomeScreen: SecurityException launching discoverability (permission request)" }
                    showBluetoothError = true
                }
            } else {
                showBluetoothError = true
            }
        }

    fun startBluetoothDiscoverability() {
        try {
            val discoverableIntent =
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION_SECONDS)
                }
            bluetoothDiscoverableLauncher.launch(discoverableIntent)
        } catch (e: SecurityException) {
            Logger.e(e) { "Fido2HomeScreen: SecurityException launching discoverability" }
            showBluetoothError = true
        }
    }

    val handleToggle = {
        val isRunning = connectionState !is HidConnectionState.Idle && connectionState !is HidConnectionState.Error
        if (isRunning) {
            viewModel.toggleTransport()
        } else {
            // Permission logic varies by Android version:
            // - Android 13+ (TIRAMISU, API 33): Requires Nearby Devices (BT) + POST_NOTIFICATIONS
            //   for foreground services.
            // - Android 12 (S, API 31): Requires Nearby Devices (BT) only.
            // - Legacy: Permissions are handled during installation or simplified.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
                val connectGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                    ) == granted
                val advertiseGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    ) == granted
                val scanGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                    ) == granted
                val notificationsGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == granted

                if (!connectGranted || !advertiseGranted || !scanGranted || !notificationsGranted) {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_ADVERTISE,
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                } else {
                    startBluetoothDiscoverability()
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
                val connectGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                    ) == granted
                val advertiseGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    ) == granted
                val scanGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                    ) == granted
                if (!connectGranted || !advertiseGranted || !scanGranted) {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_ADVERTISE,
                            android.Manifest.permission.BLUETOOTH_SCAN,
                        ),
                    )
                } else {
                    startBluetoothDiscoverability()
                }
            } else {
                startBluetoothDiscoverability()
            }
        }
    }

    if (showBluetoothError) {
        AlertDialog(
            onDismissRequest = { showBluetoothError = false },
            title = { Text("Bluetooth Required") },
            text = {
                Text(
                    "Chimali Authenticator requires Bluetooth and \"Nearby Devices\" permissions " +
                        "to act as a security key. " +
                        "Please allow discoverability and permissions to continue.",
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    val intent =
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    context.startActivity(intent)
                    showBluetoothError = false
                }) {
                    Text("Settings")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBluetoothError = false
                    handleToggle()
                }) {
                    Text("Retry")
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
                        androidx.compose.material3.IconButton(onClick = onOpenSettings) {
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
            StatusIndicator(
                state = connectionState,
                displayName = connectedDisplayName,
            )

            // Paired Devices List — takes all remaining vertical space
            val pairedDevices by pairedDevicesViewModel.pairedDevices.collectAsState()
            PairedDevicesSection(
                modifier = Modifier.weight(1f),
                onEditDevice = onEditDevice,
                devices = pairedDevices,
                onPendingRemove = { device ->
                    pairedDevicesViewModel.pendingRemove(device)
                },
                onUndoRemove = { macAddress ->
                    pairedDevicesViewModel.undoRemove(macAddress)
                },
                onCommitRemove = { macAddress ->
                    pairedDevicesViewModel.commitRemove(macAddress)
                },
                removalEvents = pairedDevicesViewModel.removalEvents,
            )

            // Primary Action
            TransportToggleButton(
                connectionState = connectionState,
                onToggle = handleToggle,
            )

            ChimaliOutlinedButton(
                onClick = onManageCredentials,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Saved Passkeys")
            }

            Text(
                text = "FIDO2 / WebAuthn over Bluetooth HID",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusIndicator(
    state: HidConnectionState,
    displayName: String?,
    modifier: Modifier = Modifier,
) {
    val (statusText, color, icon) =
        when (state) {
            is HidConnectionState.Idle ->
                Triple(
                    "Ready to Start",
                    MaterialTheme.colorScheme.outline,
                    Icons.Default.Bluetooth,
                )
            is HidConnectionState.Advertising ->
                Triple(
                    "Advertising...",
                    COLOR_ADVERTISING,
                    Icons.AutoMirrored.Filled.BluetoothSearching,
                )
            is HidConnectionState.Connecting -> Triple("Connecting...", COLOR_CONNECTING, Icons.Default.BluetoothAudio)
            is HidConnectionState.Connected -> Triple("Connected to PC", COLOR_CONNECTED, Icons.Default.Devices)
            is HidConnectionState.Error ->
                Triple(
                    "Error Occurred",
                    MaterialTheme.colorScheme.error,
                    Icons.Default.Error,
                )
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
                if (state is HidConnectionState.Advertising || state is HidConnectionState.Connecting) {
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
                if (state is HidConnectionState.Connected) {
                    Text(
                        text = displayName ?: "Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.8f),
                    )
                } else {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    if (state is HidConnectionState.Error) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransportToggleButton(
    connectionState: HidConnectionState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning =
        connectionState !is HidConnectionState.Idle &&
            connectionState !is HidConnectionState.Error

    ChimaliButton(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (isRunning) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                contentColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
        ) { running ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "Stop Authenticator" else "Start Authenticator",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

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
