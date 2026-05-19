package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultViewModel(
    private val vaultService: VaultService,
    private val clipboardManager: ClipboardManagerService,
) : ViewModel() {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    fun processIntent(intent: VaultIntent) {
        when (intent) {
            is VaultIntent.LoadItems -> loadItems(intent)
            is VaultIntent.SaveItem -> saveItem(intent)
            is VaultIntent.DeleteItem -> deleteItem(intent)
            is VaultIntent.DecryptItem -> decryptItem(intent)
            VaultIntent.ClearClipboard -> clearClipboard()
        }
    }

    private fun loadItems(intent: VaultIntent.LoadItems) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = vaultService.getItems(intent.filterLabelId)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, items = result.data) }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.message)
                    }
            }
        }
    }

    private fun saveItem(intent: VaultIntent.SaveItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val saveResult = vaultService.saveItem(intent.item)) {
                is Outcome.Success -> {
                    // Reload items after saving
                    when (val loadResult = vaultService.getItems(null)) {
                        is Outcome.Success -> _state.update { it.copy(isLoading = false, items = loadResult.data) }
                        is Outcome.Error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = loadResult.error.message)
                            }
                    }
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = saveResult.error.message)
                    }
            }
        }
    }

    private fun deleteItem(intent: VaultIntent.DeleteItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val deleteResult = vaultService.deleteItem(intent.id)) {
                is Outcome.Success -> {
                    // Reload items after deletion
                    when (val loadResult = vaultService.getItems(null)) {
                        is Outcome.Success -> _state.update { it.copy(isLoading = false, items = loadResult.data) }
                        is Outcome.Error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = loadResult.error.message)
                            }
                    }
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = deleteResult.error.message)
                    }
            }
        }
    }

    private fun decryptItem(intent: VaultIntent.DecryptItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val item = _state.value.items.find { it.id == intent.id }
            if (item != null) {
                // DEFERRED(040): Payload decryption — pending VaultCryptoService integration
                _state.update { it.copy(isLoading = false, selectedItem = item) }
            } else {
                _state.update { it.copy(isLoading = false, errorMessage = "Item not found") }
            }
        }
    }

    private fun clearClipboard() {
        viewModelScope.launch {
            clipboardManager.clearClipboard()
        }
    }
}
