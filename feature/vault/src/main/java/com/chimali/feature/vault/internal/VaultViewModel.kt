package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultState
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultViewModel(
    private val vaultService: VaultService,
    private val clipboardManager: ClipboardManagerService,
    private val vaultCryptoService: VaultCryptoService,
) : ViewModel() {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    fun processIntent(intent: VaultIntent) {
        when (intent) {
            is VaultIntent.LoadItems -> loadItems(intent)
            is VaultIntent.SaveItem -> saveItem(intent)
            is VaultIntent.LoadLabels -> loadLabels()
            is VaultIntent.CreateLabel -> createLabel(intent)
            is VaultIntent.DeleteLabel -> deleteLabel(intent)
            is VaultIntent.SavePassword -> savePassword(intent)
            is VaultIntent.SaveCreditCard -> saveCreditCard(intent)
            is VaultIntent.SaveSecureNote -> saveSecureNote(intent)
            is VaultIntent.DeleteItem -> deleteItem(intent)
            is VaultIntent.DecryptItem -> decryptItem(intent)
            VaultIntent.ClearSelectedItem -> clearSelectedItem()
            VaultIntent.ClearClipboard -> clearClipboard()
        }
    }

    private fun loadItems(intent: VaultIntent.LoadItems) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, selectedLabelId = intent.filterLabelId) }
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
                is Outcome.Success -> reloadItems()
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = saveResult.error.message)
                    }
            }
        }
    }

    private fun savePassword(intent: VaultIntent.SavePassword) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val encResult = vaultCryptoService.encryptPassword(intent.id, intent.payload, intent.identityId)) {
                is Outcome.Success -> {
                    when (val saveResult = vaultService.saveItem(encResult.data)) {
                        is Outcome.Success -> reloadItems()
                        is Outcome.Error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = saveResult.error.message)
                            }
                    }
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = encResult.error.message)
                    }
            }
        }
    }

    private fun saveCreditCard(intent: VaultIntent.SaveCreditCard) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val encResult = vaultCryptoService.encryptCreditCard(intent.id, intent.payload, intent.identityId)) {
                is Outcome.Success -> {
                    when (val saveResult = vaultService.saveItem(encResult.data)) {
                        is Outcome.Success -> reloadItems()
                        is Outcome.Error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = saveResult.error.message)
                            }
                    }
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = encResult.error.message)
                    }
            }
        }
    }

    private fun saveSecureNote(intent: VaultIntent.SaveSecureNote) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val encResult = vaultCryptoService.encryptSecureNote(intent.id, intent.payload, intent.identityId)) {
                is Outcome.Success -> {
                    when (val saveResult = vaultService.saveItem(encResult.data)) {
                        is Outcome.Success -> reloadItems()
                        is Outcome.Error ->
                            _state.update {
                                it.copy(isLoading = false, errorMessage = saveResult.error.message)
                            }
                    }
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = encResult.error.message)
                    }
            }
        }
    }

    private fun deleteItem(intent: VaultIntent.DeleteItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val deleteResult = vaultService.deleteItem(intent.id)) {
                is Outcome.Success -> {
                    clearSelectedItem()
                    reloadItems()
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
                when (item.type) {
                    VaultType.PASSWORD -> {
                        when (val dec = vaultCryptoService.decryptPassword(item)) {
                            is Outcome.Success ->
                                _state.update {
                                    it.copy(isLoading = false, selectedItem = item, selectedPasswordPayload = dec.data)
                                }
                            is Outcome.Error ->
                                _state.update {
                                    it.copy(isLoading = false, errorMessage = dec.error.message)
                                }
                        }
                    }
                    VaultType.CREDIT_CARD -> {
                        when (val dec = vaultCryptoService.decryptCreditCard(item)) {
                            is Outcome.Success ->
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        selectedItem = item,
                                        selectedCreditCardPayload = dec.data,
                                    )
                                }
                            is Outcome.Error ->
                                _state.update {
                                    it.copy(isLoading = false, errorMessage = dec.error.message)
                                }
                        }
                    }
                    VaultType.NOTE -> {
                        when (val dec = vaultCryptoService.decryptSecureNote(item)) {
                            is Outcome.Success ->
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        selectedItem = item,
                                        selectedSecureNotePayload = dec.data,
                                    )
                                }
                            is Outcome.Error ->
                                _state.update {
                                    it.copy(isLoading = false, errorMessage = dec.error.message)
                                }
                        }
                    }
                }
            } else {
                _state.update { it.copy(isLoading = false, errorMessage = "Item not found") }
            }
        }
    }

    private fun clearSelectedItem() {
        _state.value.selectedPasswordPayload?.clearMemory()
        _state.value.selectedCreditCardPayload?.clearMemory()
        _state.value.selectedSecureNotePayload?.clearMemory()
        _state.update {
            it.copy(
                selectedItem = null,
                selectedPasswordPayload = null,
                selectedCreditCardPayload = null,
                selectedSecureNotePayload = null,
            )
        }
    }

    private suspend fun reloadItems() {
        when (val loadResult = vaultService.getItems(_state.value.selectedLabelId)) {
            is Outcome.Success -> _state.update { it.copy(isLoading = false, items = loadResult.data) }
            is Outcome.Error ->
                _state.update {
                    it.copy(isLoading = false, errorMessage = loadResult.error.message)
                }
        }
    }

    private fun loadLabels() {
        viewModelScope.launch {
            when (val result = vaultService.getLabels()) {
                is Outcome.Success -> _state.update { it.copy(labels = result.data) }
                is Outcome.Error -> _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun createLabel(intent: VaultIntent.CreateLabel) {
        viewModelScope.launch {
            when (val result = vaultService.createLabel(intent.name, intent.colorHex)) {
                is Outcome.Success -> loadLabels()
                is Outcome.Error -> _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun deleteLabel(intent: VaultIntent.DeleteLabel) {
        viewModelScope.launch {
            when (val result = vaultService.deleteLabel(intent.id)) {
                is Outcome.Success -> loadLabels()
                is Outcome.Error -> _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun clearClipboard() {
        viewModelScope.launch {
            clipboardManager.clearClipboard()
        }
    }
}
