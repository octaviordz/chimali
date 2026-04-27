package com.chimali.fido2.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton
import com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * New Screen for editing a trusted host's alias and managing its record.
 * Replaces the previous AlertDialog for better UX as requested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ModifierMissing", "FunctionNaming", "ForbiddenComment") // TODO: Add modifier parameter in follow-up refactor
@Composable
fun EditPairedDeviceScreen(
    macAddress: String,
    onNavigateUp: () -> Unit,
    viewModel: PairedDevicesViewModel = koinViewModel(),
) {
    val devices by viewModel.pairedDevices.collectAsState()
    val device =
        remember(devices, macAddress) {
            devices.find { it.macAddress == macAddress }
        }

    // Capture initial alias or empty string
    var aliasText by remember(device) {
        mutableStateOf(device?.alias ?: "")
    }

    if (device == null) {
        // Just show an empty box during the transition back to the list
        Box(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Device Alias") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val targetDevice = device
                            onNavigateUp()
                            viewModel.pendingRemove(targetDevice)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete device",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChimaliOutlinedButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    ChimaliButton(
                        onClick = {
                            viewModel.updateAlias(macAddress, aliasText.ifBlank { null })
                            onNavigateUp()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Summary Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = device.name ?: "Unknown Device",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "MAC: ${device.macAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Alias Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Device Alias",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    placeholder = { Text("e.g. Work Computer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                )
                Text(
                    text = "This name is only visible to you inside Chimali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
