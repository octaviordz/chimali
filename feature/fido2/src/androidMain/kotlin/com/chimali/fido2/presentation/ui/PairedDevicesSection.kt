package com.chimali.fido2.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.fido2.domain.model.PairedDevice
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress(
    // TODO: Reorder params (modifier after required params) in follow-up refactor
    "ComposableParamOrder",
    "FunctionNaming",
    "ForbiddenComment",
)
@Composable
fun PairedDevicesSection(
    modifier: Modifier = Modifier,
    onEditDevice: (String) -> Unit,
    devices: List<PairedDevice>,
    onPendingRemove: (PairedDevice) -> Unit,
    onUndoRemove: (String) -> Unit,
    onCommitRemove: (String) -> Unit,
    removalEvents: Flow<PairedDevice>,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Collect removal events to show the Undo snackbar
    LaunchedEffect(removalEvents, onUndoRemove, onCommitRemove) {
        removalEvents.collect { device ->
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = "\"${device.alias ?: device.name ?: "Device"}\" removed",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoRemove(device.macAddress)
                } else {
                    onCommitRemove(device.macAddress)
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
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant
                                        .copy(alpha = 0.5f),
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
                                        onPendingRemove(device)
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
@Suppress("FunctionNaming")
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

private const val MAJOR_CLASS_MASK = 0x1F00
private const val MAJOR_CLASS_COMPUTER = 0x0100
private const val MAJOR_CLASS_PHONE = 0x0200
private const val MAJOR_CLASS_WEARABLE = 0x0700

/**
 * Maps a raw Bluetooth Device Class integer into a specific Material Icon.
 */
@Composable
private fun getDeviceIcon(deviceClass: Int?): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    if (deviceClass == null) return Icons.Default.Bluetooth to "Generic Bluetooth device"

    // The major class is stored in bits 8-12. Masking extracts these bits.
    return when (deviceClass and MAJOR_CLASS_MASK) {
        MAJOR_CLASS_COMPUTER -> Icons.Default.Computer to "Computer"
        MAJOR_CLASS_PHONE -> Icons.Default.Smartphone to "Phone"
        MAJOR_CLASS_WEARABLE -> Icons.Default.Watch to "Wearable"
        else -> Icons.Default.Bluetooth to "Generic Bluetooth device"
    }
}
