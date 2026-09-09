package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultMutationState
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultState
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.internal.payload.SensitivePayload
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    private var submissionJob: Job? = null
    private var activeSubmission: SensitivePayload? = null
    private var retryId: UUID? = null
    private var detailGeneration = 0L

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
            VaultIntent.ResetMutation -> {
                retryId = null
                _state.update { it.copy(mutationState = VaultMutationState.IDLE) }
            }
            VaultIntent.AbandonMutation -> abandonMutation()
            VaultIntent.ClearClipboard -> clearClipboard()
            VaultIntent.ClearCopyMessage -> _state.update { it.copy(copyMessage = null) }
            is VaultIntent.CopyPassword -> copyPassword(intent.password)
        }
    }

    private fun loadItems(intent: VaultIntent.LoadItems) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, selectedLabelId = intent.filterLabelId) }
            when (val result = vaultService.getItems(intent.filterLabelId)) {
                is Outcome.Success -> replaceItems(result.data)
                is Outcome.Error ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.error.message)
                    }
            }
        }
    }

    private fun saveItem(intent: VaultIntent.SaveItem) {
        if (_state.value.mutationState == VaultMutationState.PENDING) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    mutationState = VaultMutationState.PENDING,
                )
            }
            try {
                when (val saveResult = vaultService.saveItem(intent.item)) {
                    is Outcome.Success -> {
                        _state.update { it.copy(mutationState = VaultMutationState.SUCCEEDED) }
                        reloadItems()
                    }
                    is Outcome.Error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = saveResult.error.message,
                                mutationState = VaultMutationState.FAILED,
                            )
                        }
                }
            } finally {
                intent.item.clearMemory()
            }
        }
    }

    /** FR-VAULT-026: completion handlers also cover jobs cancelled before their body starts. */
    private fun launchSubmission(
        payload: SensitivePayload,
        save: suspend () -> Unit,
    ) {
        if (_state.value.mutationState == VaultMutationState.PENDING) {
            if (payload !== activeSubmission) payload.clearMemory()
            return
        }
        activeSubmission = payload
        _state.update { it.copy(isLoading = true, errorMessage = null, mutationState = VaultMutationState.PENDING) }
        submissionJob =
            viewModelScope.launch { save() }.also { job ->
                job.invokeOnCompletion {
                    payload.clearMemory()
                    if (activeSubmission === payload) {
                        activeSubmission = null
                        if (job.isCancelled) {
                            _state.update { state ->
                                if (state.mutationState == VaultMutationState.PENDING) {
                                    state.copy(isLoading = false, mutationState = VaultMutationState.IDLE)
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }
            }
    }

    override fun onCleared() {
        clearSelectedItem()
        super.onCleared()
    }

    private fun abandonMutation() {
        submissionJob?.cancel()
        submissionJob = null
        retryId = null
        _state.update { it.copy(isLoading = false, mutationState = VaultMutationState.IDLE) }
    }

    private fun savePassword(intent: VaultIntent.SavePassword) {
        launchSubmission(intent.payload) {
            savePayload(intent.id, intent.labelIds) { id ->
                vaultCryptoService.encryptPassword(id, intent.payload, intent.identityId)
            }
        }
    }

    private fun saveCreditCard(intent: VaultIntent.SaveCreditCard) {
        launchSubmission(intent.payload) {
            savePayload(intent.id, intent.labelIds) { id ->
                vaultCryptoService.encryptCreditCard(id, intent.payload, intent.identityId)
            }
        }
    }

    private fun saveSecureNote(intent: VaultIntent.SaveSecureNote) {
        launchSubmission(intent.payload) {
            savePayload(intent.id, intent.labelIds) { id ->
                vaultCryptoService.encryptSecureNote(id, intent.payload, intent.identityId)
            }
        }
    }

    private suspend fun savePayload(
        id: UUID?,
        labelIds: List<UUID>,
        encrypt: suspend (UUID) -> Outcome<VaultItem, DomainError>,
    ) {
        val stableId = id ?: retryId ?: UUID.randomUUID().also { retryId = it }
        val encrypted = encrypt(stableId)
        currentCoroutineContext().ensureActive()
        val outcome =
            when (encrypted) {
                is Outcome.Error -> encrypted
                is Outcome.Success -> {
                    try {
                        val saved = vaultService.saveItem(encrypted.data)
                        currentCoroutineContext().ensureActive()
                        when (saved) {
                            is Outcome.Error -> saved
                            is Outcome.Success -> vaultService.setItemLabels(encrypted.data.id, labelIds)
                        }
                    } finally {
                        encrypted.data.clearMemory()
                    }
                }
            }
        currentCoroutineContext().ensureActive()
        when (outcome) {
            is Outcome.Success -> {
                reloadItems()
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(mutationState = VaultMutationState.SUCCEEDED) }
            }
            is Outcome.Error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = outcome.error.message,
                        mutationState = VaultMutationState.FAILED,
                    )
                }
        }
    }

    private fun deleteItem(intent: VaultIntent.DeleteItem) {
        if (_state.value.mutationState == VaultMutationState.PENDING) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    mutationState = VaultMutationState.PENDING,
                )
            }
            when (val deleteResult = vaultService.deleteItem(intent.id)) {
                is Outcome.Success -> {
                    _state.update { it.copy(mutationState = VaultMutationState.SUCCEEDED) }
                    clearSelectedItem()
                    reloadItems()
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = deleteResult.error.message,
                            mutationState = VaultMutationState.FAILED,
                        )
                    }
            }
        }
    }

    private fun decryptItem(intent: VaultIntent.DecryptItem) {
        clearSelectedItem()
        val generation = detailGeneration
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val stored = vaultService.getItems(null)
            if (generation != detailGeneration) return@launch
            if (stored is Outcome.Error) {
                _state.update { it.copy(isLoading = false, errorMessage = stored.error.message) }
                return@launch
            }
            val item = (stored as Outcome.Success).data.find { it.id == intent.id }
            if (item != null) {
                val labelIds = vaultService.getItemLabelIds(intent.id)
                val selectedLabelIds = (labelIds as? Outcome.Success)?.data?.toSet() ?: emptySet()
                val dec =
                    when (item.type) {
                        VaultType.PASSWORD -> vaultCryptoService.decryptPassword(item)
                        VaultType.CREDIT_CARD -> vaultCryptoService.decryptCreditCard(item)
                        VaultType.NOTE -> vaultCryptoService.decryptSecureNote(item)
                    }
                if (generation != detailGeneration || !currentCoroutineContext()[Job]!!.isActive) {
                    if (dec is Outcome.Success) dec.data.clearMemory()
                    return@launch
                }
                when (dec) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedItem = item,
                                selectedPasswordPayload = dec.data as? PasswordPayload,
                                selectedCreditCardPayload = dec.data as? CreditCardPayload,
                                selectedSecureNotePayload = dec.data as? SecureNotePayload,
                                selectedItemLabelIds = selectedLabelIds,
                            )
                        }
                    is Outcome.Error -> _state.update { it.copy(isLoading = false, errorMessage = dec.error.message) }
                }
            } else {
                _state.update { it.copy(isLoading = false, errorMessage = "Item not found") }
            }
        }
    }

    private fun clearSelectedItem() {
        detailGeneration++
        _state.value.selectedPasswordPayload?.clearMemory()
        _state.value.selectedCreditCardPayload?.clearMemory()
        _state.value.selectedSecureNotePayload?.clearMemory()
        _state.value.selectedItem?.clearMemory()
        _state.update {
            it.copy(
                selectedItem = null,
                selectedPasswordPayload = null,
                selectedCreditCardPayload = null,
                selectedSecureNotePayload = null,
                selectedItemLabelIds = emptySet(),
            )
        }
    }

    private suspend fun reloadItems() {
        val loadResult = vaultService.getItems(_state.value.selectedLabelId)
        currentCoroutineContext().ensureActive()
        when (loadResult) {
            is Outcome.Success -> replaceItems(loadResult.data)
            is Outcome.Error ->
                _state.update {
                    it.copy(isLoading = false, errorMessage = loadResult.error.message)
                }
        }
    }

    private fun replaceItems(items: List<VaultItem>) {
        val oldItems = _state.value.items
        _state.update { it.copy(isLoading = false, items = items) }
        oldItems.forEach { it.clearMemory() }
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

    private fun copyPassword(password: CharArray) {
        viewModelScope
            .launch {
                try {
                    // Approved I.5 platform adapter: no String is stored in an intent or application state.
                    val result = clipboardManager.copySensitiveData("Password", String(password))
                    val message =
                        result.fold(
                            onSuccess = { "Password copied. Clipboard clears in 60s." },
                            onFailure = { "Unable to copy password to clipboard." },
                        )
                    currentCoroutineContext().ensureActive()
                    _state.update { it.copy(copyMessage = message) }
                } finally {
                    password.fill('\u0000')
                }
            }.invokeOnCompletion { password.fill('\u0000') }
    }
}
