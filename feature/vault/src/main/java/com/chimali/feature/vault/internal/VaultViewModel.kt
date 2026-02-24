package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultService: VaultService,
    private val clipboardManager: ClipboardManagerWrapper
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
            try {
                val items = vaultService.getItems(intent.filterLabelId)
                _state.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load items") }
            }
        }
    }

    private fun saveItem(intent: VaultIntent.SaveItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                vaultService.saveItem(intent.item)
                // Reload items after saving
                val items = vaultService.getItems(null)
                _state.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to save item") }
            }
        }
    }

    private fun deleteItem(intent: VaultIntent.DeleteItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                vaultService.deleteItem(intent.id)
                // Reload items after deletion
                val items = vaultService.getItems(null)
                _state.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete item") }
            }
        }
    }

    private fun decryptItem(intent: VaultIntent.DecryptItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val item = _state.value.items.find { it.id == intent.id }
                if (item != null) {
                    // TODO: Trigger actual payload decryption and UI state update here
                    _state.update { it.copy(isLoading = false, selectedItem = item) }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "Item not found") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to decrypt item") }
            }
        }
    }

    private fun clearClipboard() {
        clipboardManager.clear()
    }
}
