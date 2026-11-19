package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PairedDevicesViewModel(
    private val repository: com.chimali.fido2.domain.repository.PairedDeviceRepository,
    private val updateDeviceAliasUseCase: com.chimali.fido2.domain.usecase.UpdateDeviceAliasUseCase,
) : ViewModel() {
    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L
        private const val REPLAY_EXPIRATION_MS = 0L
    }

    // MAC addresses pending deletion (swiped but not yet committed)
    private val pendingDeleteState = MutableStateFlow<Set<String>>(emptySet())

    // The selected address is passed through this screen-scoped ViewModel, not a Navigation route.
    private val _editingDeviceAddress = MutableStateFlow<String?>(null)
    val editingDeviceAddress: StateFlow<String?> = _editingDeviceAddress

    // Only the address is needed to finish Undo/commit; do not buffer a full device record.
    private val _removalEvents = Channel<String>(Channel.BUFFERED)
    val removalEvents = _removalEvents.receiveAsFlow()

    // All devices from DB, with pending-delete items hidden from the UI
    val pairedDevices: StateFlow<List<PairedDevice>> =
        combine(repository.getAllPairedDevices(), pendingDeleteState) { all, pending ->
            all.filter { it.macAddress !in pending }
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = SUBSCRIBE_TIMEOUT_MS,
                    replayExpirationMillis = REPLAY_EXPIRATION_MS,
                ),
            initialValue = emptyList(),
        )

    override fun onCleared() {
        pendingDeleteState.value = emptySet()
        _editingDeviceAddress.value = null
        _removalEvents.cancel()
        super.onCleared()
    }

    /** Selects a paired device for the editor without copying its address into Navigation state. */
    fun beginEditingDevice(macAddress: String) {
        _editingDeviceAddress.value = macAddress
    }

    /** Releases the editor's selected-device reference when its screen leaves composition. */
    fun clearEditingDevice() {
        _editingDeviceAddress.value = null
    }

    /** Hide item immediately — starts the undo window. */
    fun pendingRemove(device: PairedDevice) {
        pendingDeleteState.value = pendingDeleteState.value + device.macAddress
        viewModelScope.launch {
            _removalEvents.send(device.macAddress)
        }
    }

    /** User pressed Undo — bring the item back. */
    fun undoRemove(macAddress: String) {
        pendingDeleteState.value = pendingDeleteState.value - macAddress
    }

    /** Snackbar timed out — permanently delete from DB. */
    fun commitRemove(macAddress: String) {
        pendingDeleteState.value = pendingDeleteState.value - macAddress
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
