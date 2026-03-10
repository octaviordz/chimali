package com.chimali.fido2.presentation.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.presentation.navigation.Fido2Destinations
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterIsInstance

/**
 * T156a — Authenticator Dashboard (Home Screen).
 * Provides status monitoring and transport control for the FIDO2 module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fido2HomeScreen(
    onManageCredentials: () -> Unit,
    onRegisterRequest: () -> Unit,
    viewModel: Fido2HomeViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()

    // Observe incoming FIDO2 events (e.g. from PC via Bluetooth)
    LaunchedEffect(Unit) {
        // Check for any registration request that arrived while this screen was backgrounded
        // or before it was created.
        if (viewModel.getPendingRegistration() != null) {
            onRegisterRequest()
        }

        // Collect new incoming requests (replay is now 0 in the bus)
        viewModel.uiEvents
            .filterIsInstance<Fido2UiEvent.RegistrationRequested>()
            .collect {
                onRegisterRequest()
            }
    }

    var showBluetoothError by remember { mutableStateOf(false) }

    val bluetoothDiscoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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

    if (showBluetoothError) {
        AlertDialog(
            onDismissRequest = { showBluetoothError = false },
            title = { Text("Bluetooth Required") },
            text = { Text("Chimali Authenticator requires Bluetooth and visibility to act as a security key. Please allow discoverability to continue.") },
            confirmButton = {
                TextButton(onClick = { showBluetoothError = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chimali Authenticator", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Status Card
            StatusIndicator(connectionState)

            Spacer(modifier = Modifier.weight(1f))

            // Primary Action
            TransportToggleButton(
                connectionState = connectionState,
                onToggle = {
                    val isRunning = connectionState !is HidConnectionState.Idle && connectionState !is HidConnectionState.Error
                    if (isRunning) {
                        // Directly stop if it's already running
                        viewModel.toggleTransport()
                    } else {
                        // Starting: Request system prompt to turn on Bluetooth AND make it discoverable.
                        // This simplifies the flow for the user so they don't have to manually go to 
                        // Settings -> Pair New Device.
                        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        }
                        bluetoothDiscoverableLauncher.launch(discoverableIntent)
                    }
                }
            )

            // Secondary Actions
            OutlinedButton(
                onClick = {
                    // Manual test trigger
                    val mockUserId = "user_${System.currentTimeMillis()}"
                    val mockOptions = MakeCredentialOptions.create(
                        rp = PublicKeyCredentialRpEntity.create("webauthn.io", "WebAuthn.io (Test)"),
                        user = PublicKeyCredentialUserEntity.create(mockUserId.toByteArray(), mockUserId, "Chimali User"),
                        challenge = "challenge".toByteArray(),
                        pubKeyCredParams = PublicKeyCredentialParameters.createES256P256()
                    )
                    viewModel.testRegistration(mockOptions)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test Registration UI")
            }

            OutlinedButton(
                onClick = onManageCredentials,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Saved Passkeys")
            }

            Text(
                text = "FIDO2 / WebAuthn over Bluetooth HID",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusIndicator(state: HidConnectionState) {
    val (statusText, color, icon) = when (state) {
        is HidConnectionState.Idle -> Triple("Ready to Start", MaterialTheme.colorScheme.outline, Icons.Default.Bluetooth)
        is HidConnectionState.Advertising -> Triple("Advertising...", Color(0xFF6200EE), Icons.Default.BluetoothSearching)
        is HidConnectionState.Connecting -> Triple("Connecting...", Color(0xFFFF9800), Icons.Default.BluetoothAudio)
        is HidConnectionState.Connected -> Triple("Connected to PC", Color(0xFF4CAF50), Icons.Default.Devices)
        is HidConnectionState.Error -> Triple("Error Occurred", MaterialTheme.colorScheme.error, Icons.Default.Error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                // Pulse Animation if advertising or connecting
                if (state is HidConnectionState.Advertising || state is HidConnectionState.Connecting) {
                    PulseAnimation(color = color)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = color
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (state is HidConnectionState.Connected) {
                Text(
                    text = "Device: ${state.device.name ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color.copy(alpha = 0.8f)
                )
            }
            if (state is HidConnectionState.Error) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TransportToggleButton(
    connectionState: HidConnectionState,
    onToggle: () -> Unit
) {
    val isRunning = connectionState !is HidConnectionState.Idle && 
                    connectionState !is HidConnectionState.Error

    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { running ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "Stop Authenticator" else "Start Authenticator",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun PulseAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(color)
    )
}
