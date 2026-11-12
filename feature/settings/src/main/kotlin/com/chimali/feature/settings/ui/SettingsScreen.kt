package com.chimali.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chimali.core.ui.theme.LegibilityType
import com.chimali.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vaultEnabled: Boolean,
    passkeyEnabled: Boolean,
    onRetakeOnboarding: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bluetoothTransportSettings: BluetoothTransportSettingsUiState? = null,
    onSelectTransport: (String) -> Unit = {},
    onRetryTransportSettings: () -> Unit = {},
    onCancelPendingTransportChange: () -> Unit = {},
) {
    val selectionLabels = settingsSelectionLabels(vaultEnabled, passkeyEnabled)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Settings", modifier = Modifier.semantics { heading() }) })
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Current selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = LegibilityType.AtkinsonFontFamily,
                    )
                    Text(text = selectionLabels.vault)
                    Text(text = selectionLabels.passkey)
                }
            }

            if (passkeyEnabled && bluetoothTransportSettings != null) {
                BluetoothTransportSettingsCard(
                    state = bluetoothTransportSettings,
                    onSelectTransport = onSelectTransport,
                    onRetry = onRetryTransportSettings,
                    onCancelPendingChange = onCancelPendingTransportChange,
                )
            }

            Text(
                text = "You can restart the onboarding flow to change which features Chimali uses by default.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
                Button(onClick = onRetakeOnboarding) {
                    Text("Re-take Onboarding")
                }
            }
        }
    }
}

@Composable
private fun BluetoothTransportSettingsCard(
    state: BluetoothTransportSettingsUiState,
    onSelectTransport: (String) -> Unit,
    onRetry: () -> Unit,
    onCancelPendingChange: () -> Unit,
) {
    val interactionEnabled =
        !state.isLoading && !state.isSaving && state.readError == null && state.processChoiceResolved
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.bluetooth_transport_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.bluetooth_transport_intro))
            TransportRadioOption(
                label = stringResource(R.string.bluetooth_transport_ble),
                code = "fido_ble",
                selectedCode = state.savedTransportCode,
                enabled = interactionEnabled,
                onSelect = onSelectTransport,
            )
            TransportRadioOption(
                label = stringResource(R.string.bluetooth_transport_classic),
                code = "classic_hid",
                selectedCode = state.savedTransportCode,
                enabled = interactionEnabled,
                onSelect = onSelectTransport,
            )
            if (state.isLoading) Text(stringResource(R.string.bluetooth_transport_loading))
            val processStatus =
                when {
                    state.processTransportCode == null ->
                        stringResource(R.string.bluetooth_transport_process_unresolved)
                    state.availabilityCode == "ready" -> {
                        val processLabel = processTransportLabel(state.processTransportCode)
                        stringResource(R.string.bluetooth_transport_active_prefix, processLabel)
                    }
                    else -> {
                        val processLabel = processTransportLabel(state.processTransportCode)
                        stringResource(R.string.bluetooth_transport_selected_prefix, processLabel)
                    }
                }
            Text(processStatus)
            Text(availabilityString(state.availabilityCode))
            if (state.restartRequired) {
                Text(stringResource(R.string.bluetooth_transport_restart_required))
                OutlinedButton(onClick = onCancelPendingChange, enabled = !state.isSaving) {
                    Text(stringResource(R.string.bluetooth_transport_cancel_pending))
                }
            }
            state.readError?.let { errorCode ->
                Text(readErrorString(errorCode), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text(stringResource(R.string.bluetooth_transport_retry)) }
            }
            state.saveError?.let { errorCode ->
                Text(saveErrorString(errorCode), color = MaterialTheme.colorScheme.error)
            }
            if (state.isSaving) Text(stringResource(R.string.bluetooth_transport_saving))
        }
    }
}

@Composable
private fun processTransportLabel(code: String): String =
    when (code) {
        "fido_ble" -> stringResource(R.string.bluetooth_transport_active_ble)
        "classic_hid" -> stringResource(R.string.bluetooth_transport_active_classic)
        else -> stringResource(R.string.bluetooth_transport_active_unknown)
    }

@Composable
private fun TransportRadioOption(
    label: String,
    code: String,
    selectedCode: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val selected = selectedCode == code
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(code) },
                ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun availabilityString(code: String): String =
    stringResource(
        when (code) {
            "loading" -> R.string.bluetooth_transport_status_loading
            "selected_not_started" -> R.string.bluetooth_transport_status_selected_not_started
            "starting" -> R.string.bluetooth_transport_status_starting
            "ready" -> R.string.bluetooth_transport_status_ready
            "preference_read_failed" -> R.string.bluetooth_transport_status_read_failed
            "invalid_preference" -> R.string.bluetooth_transport_status_invalid
            "initialization_timeout" -> R.string.bluetooth_transport_status_timeout
            "unsupported" -> R.string.bluetooth_transport_status_unsupported
            "permission_required" -> R.string.bluetooth_transport_status_permission
            "bluetooth_off" -> R.string.bluetooth_transport_status_off
            "startup_failed" -> R.string.bluetooth_transport_status_start_failed
            else -> R.string.bluetooth_transport_status_loading
        },
    )

@Composable
private fun readErrorString(code: String): String =
    stringResource(
        if (code == "invalid_preference") {
            R.string.bluetooth_transport_invalid_error
        } else {
            R.string.bluetooth_transport_read_error
        },
    )

@Composable
private fun saveErrorString(code: String): String =
    stringResource(
        if (code == "invalid_input") {
            R.string.bluetooth_transport_input_error
        } else {
            R.string.bluetooth_transport_write_error
        },
    )
