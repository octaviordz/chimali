package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.clipboard.ClipboardError
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.annotation.Req
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SAFE_VAULT_ERROR_MESSAGE = "Unable to complete the requested vault operation."

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
    private var itemLoadGeneration = 0L
    private var labelLoadGeneration = 0L
    private val clipboardReceipts = ConcurrentHashMap.newKeySet<Long>()

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
            VaultIntent.ClearListData -> clearListData()
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
        val generation = ++itemLoadGeneration
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, selectedLabelId = intent.filterLabelId) }
            when (val result = vaultService.getItems(intent.filterLabelId)) {
                is Outcome.Success -> {
                    if (generation == itemLoadGeneration) {
                        replaceItems(result.data)
                    } else {
                        result.data.forEach(VaultItem::clearMemory)
                    }
                }
                is Outcome.Error -> {
                    if (generation == itemLoadGeneration) {
                        _state.update {
                            it.copy(isLoading = false, errorMessage = SAFE_VAULT_ERROR_MESSAGE)
                        }
                    }
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
                when (vaultService.saveItem(intent.item)) {
                    is Outcome.Success -> {
                        _state.update { it.copy(mutationState = VaultMutationState.SUCCEEDED) }
                        reloadItems()
                    }
                    is Outcome.Error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = SAFE_VAULT_ERROR_MESSAGE,
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
        clipboardReceipts.forEach(clipboardManager::transferToCustodian)
        clipboardReceipts.clear()
        clearSelectedItem()
        _state.value.items.forEach(VaultItem::clearMemory)
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
                        errorMessage = SAFE_VAULT_ERROR_MESSAGE,
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
            when (vaultService.deleteItem(intent.id)) {
                is Outcome.Success -> {
                    _state.update { it.copy(mutationState = VaultMutationState.SUCCEEDED) }
                    clearSelectedItem()
                    reloadItems()
                }
                is Outcome.Error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = SAFE_VAULT_ERROR_MESSAGE,
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
                _state.update { it.copy(isLoading = false, errorMessage = SAFE_VAULT_ERROR_MESSAGE) }
                return@launch
            }
            val fetchedItems = (stored as Outcome.Success).data
            var transferredItem: VaultItem? = null
            try {
                if (generation != detailGeneration) return@launch
                val item = fetchedItems.find { it.id == intent.id }
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
                        is Outcome.Success -> {
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
                            transferredItem = item
                        }
                        is Outcome.Error ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = SAFE_VAULT_ERROR_MESSAGE,
                                )
                            }
                    }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "Item not found") }
                }
            } finally {
                fetchedItems.filterNot { it === transferredItem }.forEach(VaultItem::clearMemory)
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
        val generation = ++itemLoadGeneration
        val loadResult = vaultService.getItems(_state.value.selectedLabelId)
        currentCoroutineContext().ensureActive()
        when (loadResult) {
            is Outcome.Success -> {
                if (generation == itemLoadGeneration) {
                    replaceItems(loadResult.data)
                } else {
                    loadResult.data.forEach(VaultItem::clearMemory)
                }
            }
            is Outcome.Error -> {
                if (generation == itemLoadGeneration) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = SAFE_VAULT_ERROR_MESSAGE)
                    }
                }
            }
        }
    }

    private fun replaceItems(items: List<VaultItem>) {
        val oldItems = _state.value.items
        _state.update { it.copy(isLoading = false, items = items) }
        oldItems.forEach { it.clearMemory() }
    }

    private fun clearListData() {
        itemLoadGeneration++
        labelLoadGeneration++
        val oldItems = _state.value.items
        _state.update { it.copy(items = emptyList(), labels = emptyList(), isLoading = false) }
        oldItems.forEach(VaultItem::clearMemory)
    }

    private fun loadLabels() {
        val generation = ++labelLoadGeneration
        viewModelScope.launch {
            when (val result = vaultService.getLabels()) {
                is Outcome.Success -> {
                    if (generation == labelLoadGeneration) _state.update { it.copy(labels = result.data) }
                }
                is Outcome.Error -> {
                    if (generation == labelLoadGeneration) {
                        _state.update {
                            it.copy(errorMessage = SAFE_VAULT_ERROR_MESSAGE)
                        }
                    }
                }
            }
        }
    }

    private fun createLabel(intent: VaultIntent.CreateLabel) {
        viewModelScope.launch {
            when (val result = vaultService.createLabel(intent.name, intent.colorHex)) {
                is Outcome.Success -> loadLabels()
                is Outcome.Error -> _state.update { it.copy(errorMessage = SAFE_VAULT_ERROR_MESSAGE) }
            }
        }
    }

    private fun deleteLabel(intent: VaultIntent.DeleteLabel) {
        viewModelScope.launch {
            when (val result = vaultService.deleteLabel(intent.id)) {
                is Outcome.Success -> loadLabels()
                is Outcome.Error -> _state.update { it.copy(errorMessage = SAFE_VAULT_ERROR_MESSAGE) }
            }
        }
    }

    private fun clearClipboard() {
        viewModelScope.launch {
            clipboardManager.clearClipboard().onFailure {
                _state.update { state ->
                    state.copy(copyMessage = "Couldn't clear the clipboard. It may still be available.")
                }
            }
        }
    }

    @Req("NFR-SEC-050", "TR-CLIP-010")
    private fun copyPassword(password: CharArray) {
        viewModelScope
            .launch {
                try {
                    // Approved I.5 platform adapter: no String is stored in an intent or application state.
                    val result = clipboardManager.copySensitiveData("Password", password)
                    val message =
                        result.fold(
                            onSuccess = { receipt ->
                                clipboardReceipts.add(receipt.generation)
                                observeClipboardReceipt(receipt.generation)
                                if (receipt.publication ==
                                    com.chimali.core.clipboard.PublicationStatus.MetadataConfirmed
                                ) {
                                    "Password copied. We'll try to clear the clipboard in 30 seconds."
                                } else {
                                    "Copy couldn't be confirmed. If copied, we'll try to clear it in 30 seconds."
                                }
                            },
                            onFailure = { error ->
                                if (error is ClipboardError.CopyAdmissionRejected) {
                                    "Copy is temporarily unavailable. Try again later."
                                } else {
                                    "Unable to copy password to clipboard."
                                }
                            },
                        )
                    currentCoroutineContext().ensureActive()
                    _state.update { it.copy(copyMessage = message) }
                } finally {
                    password.fill('\u0000')
                }
            }.invokeOnCompletion { password.fill('\u0000') }
    }

    private fun observeClipboardReceipt(generation: Long) {
        viewModelScope.launch {
            val statusFlow = runCatching { clipboardManager.cleanupStatuses }.getOrNull() ?: return@launch
            val terminal =
                statusFlow.first { statuses ->
                    statuses[generation]?.outcome?.let { it != com.chimali.core.clipboard.CleanupOutcome.Pending } ==
                        true
                }[generation] ?: return@launch
            if (terminal.outcome in
                setOf(
                    com.chimali.core.clipboard.CleanupOutcome.ClearRequestSubmitted,
                    com.chimali.core.clipboard.CleanupOutcome.ManualClearSubmitted,
                    com.chimali.core.clipboard.CleanupOutcome.SkippedOwnershipUnavailable,
                    com.chimali.core.clipboard.CleanupOutcome.ClearRejected,
                    com.chimali.core.clipboard.CleanupOutcome.Interrupted,
                )
            ) {
                _state.update { it.copy(copyMessage = "Clipboard cleanup couldn't be confirmed.") }
            }
            clipboardManager.acknowledge(generation)
            clipboardReceipts.remove(generation)
        }
    }
}
