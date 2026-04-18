package com.chimali.fido2.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairedDevicesSection(
    modifier: Modifier = Modifier,
    onEditDevice: (String) -> Unit,
    viewModel: PairedDevicesViewModel = koinViewModel(),
) {
    val devices by viewModel.pairedDevices.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Collect removal events to show the Undo snackbar
    LaunchedEffect(Unit) {
        viewModel.removalEvents.collect { device ->
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "\"${device.alias ?: device.name ?: "Device"}\" removed",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoRemove(device.macAddress)
                } else {
                    viewModel.commitRemove(device.macAddress)
                }
            }
        }
    }

    AnimatedVisibility(visible = true, modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (devices.isNotEmpty()) {
                    Text(
                        text = "Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                    ) {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                        ) {
                            items(
                                items = devices,
                                key = { it.macAddress },
                            ) { device ->
                                PairedDeviceItem(
                                    device = device,
                                    onSwipedAway = {
                                        viewModel.pendingRemove(device)
                                    },
                                    onEditClick = { onEditDevice(device.macAddress) },
                                )
                            }
                        }
                    }
                }
            }

            // Snackbar pinned to the bottom of the section box
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairedDeviceItem(
    device: PairedDevice,
    onSwipedAway: () -> Unit,
    onEditClick: () -> Unit,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val lastUsed = dateFormat.format(Date(device.lastUsedAt))

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onSwipedAway()
                    // Return false: we handle visibility via ViewModel, not SwipeToDismissBox
                    false
                } else {
                    false
                }
            },
            // Require 40% drag before triggering — prevents accidental deletes
            positionalThreshold = { totalDistance -> totalDistance * 0.4f },
        )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val progress = dismissState.progress
            val targetValue = dismissState.targetValue

            // Background only becomes visible when actively swiping
            val isSwiping = progress > 0.01f

            val bgAlpha by animateFloatAsState(
                targetValue = if (isSwiping) 1f else 0f,
                label = "bg_alpha",
            )
            val iconAlpha by animateFloatAsState(
                targetValue = if (progress > 0.15f) 1f else 0f,
                label = "icon_alpha",
            )

            // Determine swipe direction from targetValue (not dismissDirection which can be null)
            val isSwipingLeft = targetValue == SwipeToDismissBoxValue.EndToStart

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(bgAlpha)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                contentAlignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete device",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.alpha(iconAlpha),
                )
            }
        },
    ) {
        // Foreground — icon depends on device class
        val iconInfo = getDeviceIcon(device.deviceClass)

        ListItem(
            headlineContent = {
                Text(
                    text = device.alias ?: device.name ?: "Unknown Device",
                    fontWeight = if (device.alias != null) FontWeight.Bold else FontWeight.Normal,
                )
            },
            supportingContent = {
                Column {
                    if (device.alias != null && device.name != null) {
                        Text(
                            text = "Device: ${device.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Last used: $lastUsed")
                }
            },
            leadingContent = {
                Icon(
                    imageVector = iconInfo.first,
                    contentDescription = iconInfo.second,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = if (device.alias != null) Icons.Default.Label else Icons.Default.Edit,
                        contentDescription = "Edit name",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            },
            colors =
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        )
    }
}

/**
 * Maps a raw Bluetooth Device Class integer into a specific Material Icon.
 */
@Composable
private fun getDeviceIcon(deviceClass: Int?): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    if (deviceClass == null) return Icons.Default.Bluetooth to "Generic Bluetooth device"

    // The major class is stored in bits 8-12. Masking with 0x1F00 extracts these bits.
    return when (deviceClass and 0x1F00) {
        0x0100 -> Icons.Default.Computer to "Computer"
        0x0200 -> Icons.Default.Smartphone to "Phone"
        0x0700 -> Icons.Default.Watch to "Wearable"
        else -> Icons.Default.Bluetooth to "Generic Bluetooth device"
    }
}
