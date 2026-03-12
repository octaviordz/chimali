package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairedDevicesViewModel @Inject constructor(
    private val repository: PairedDeviceRepository
) : ViewModel() {

    // MAC addresses pending deletion (swiped but not yet committed)
    private val _pendingDelete = MutableStateFlow<Set<String>>(emptySet())

    // All devices from DB, with pending-delete items hidden from the UI
    val pairedDevices: StateFlow<List<PairedDevice>> =
        combine(repository.getAllPairedDevices(), _pendingDelete) { all, pending ->
            all.filter { it.macAddress !in pending }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Hide item immediately — starts the undo window. */
    fun pendingRemove(macAddress: String) {
        _pendingDelete.value = _pendingDelete.value + macAddress
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
}
