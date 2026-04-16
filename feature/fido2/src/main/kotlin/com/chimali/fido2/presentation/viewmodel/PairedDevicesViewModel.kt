package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairedDevicesViewModel
    @Inject
    constructor(
        private val repository: com.chimali.fido2.domain.repository.PairedDeviceRepository,
        private val updateDeviceAliasUseCase: com.chimali.fido2.domain.usecase.UpdateDeviceAliasUseCase,
    ) : ViewModel() {
        // MAC addresses pending deletion (swiped but not yet committed)
        private val _pendingDelete = MutableStateFlow<Set<String>>(emptySet())

        // Deletion events to be handled by the UI (e.g. show snackbar)
        private val _removalEvents = Channel<PairedDevice>(Channel.BUFFERED)
        val removalEvents = _removalEvents.receiveAsFlow()

        // All devices from DB, with pending-delete items hidden from the UI
        val pairedDevices: StateFlow<List<PairedDevice>> =
            combine(repository.getAllPairedDevices(), _pendingDelete) { all, pending ->
                all.filter { it.macAddress !in pending }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /** Hide item immediately — starts the undo window. */
        fun pendingRemove(device: PairedDevice) {
            _pendingDelete.value = _pendingDelete.value + device.macAddress
            viewModelScope.launch {
                _removalEvents.send(device)
            }
        }

        /** User pressed Undo — bring the item back. */
        fun undoRemove(macAddress: String) {
            _pendingDelete.value = _pendingDelete.value - macAddress
        }

        /** Snackbar timed out — permanently delete from DB. */
        fun commitRemove(macAddress: String) {
            _pendingDelete.value = _pendingDelete.value - macAddress
            viewModelScope.launch {
                repository.deleteDevice(macAddress)
            }
        }

        fun updateAlias(
            macAddress: String,
            alias: String?,
        ) {
            viewModelScope.launch {
                updateDeviceAliasUseCase(macAddress, alias)
            }
        }
    }
