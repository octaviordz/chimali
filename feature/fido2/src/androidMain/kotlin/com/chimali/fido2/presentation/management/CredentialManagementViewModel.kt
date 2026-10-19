package com.chimali.fido2.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.isSuccess
import com.chimali.core.common.result.onFailure
import com.chimali.core.common.result.onSuccess
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * T120 — ViewModel for managing FIDO2 Credentials.
 * Uses MVI pattern: Intent -> State -> Effect
 */
@OptIn(FlowPreview::class)
@KoinViewModel
@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialManagementViewModel(
    private val getAllCredentialsUseCase: GetAllCredentialsUseCase,
    private val searchCredentialsUseCase: SearchCredentialsUseCase,
    private val deleteCredentialUseCase: DeleteCredentialUseCase,
    private val deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase?,
) : ViewModel() {
    private val logger = Logger.withTag("CredentialManagement")
    private val _state = MutableStateFlow(CredentialManagementState())

    /** Current display state; credential objects remain owned by the state slots that retain them. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val state: StateFlow<CredentialManagementState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<CredentialManagementEffect>()

    /** One-shot management effects emitted to the UI. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val effect: SharedFlow<CredentialManagementEffect> = _effect.asSharedFlow()

    private val _removalEvents = MutableSharedFlow<PasskeyCredential>()

    /** Credential owners temporarily transferred to the undo/removal UI flow. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val removalEvents: SharedFlow<PasskeyCredential> = _removalEvents.asSharedFlow()

    private val searchQuery =
        MutableStateFlow(CredentialSearchQuery(0L, SensitiveCredentialText.copyOf("")))
    private var fullCredentialList: List<PasskeyCredential> = emptyList()

    private var currentOffset: Long = 0L
    private val pageSize: Long = DEFAULT_PAGE_SIZE
    private var requestGeneration: Long = 0L
    private var pageRequestGeneration: Long? = null
    private var activeSearchJob: Job? = null

    init {
        observeCredentials()
    }

    /** Applies a management intent while preserving the ViewModel's credential-owner lifetimes. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun onIntent(intent: CredentialManagementIntent) {
        when (intent) {
            is CredentialManagementIntent.RefreshCredentials -> {
                requestGeneration += 1L
                activeSearchJob?.cancel()
                val replaced = fullCredentialList
                currentOffset = 0L
                fullCredentialList = emptyList()
                _state.update { it.copy(credentials = emptyList()) }
                eraseCredentialsNoLongerOwned(replaced)
                loadCredentials()
            }

            is CredentialManagementIntent.LoadNextPage -> {
                if (_state.value.hasMore && !_state.value.isPaginating && searchQuery.value.query.isBlank()) {
                    loadCredentials()
                }
            }

            is CredentialManagementIntent.UpdateSearchQuery -> {
                logger.d { "Credential search query updated" }
                requestGeneration += 1L
                activeSearchJob?.cancel()
                val replaced = searchQuery.value
                searchQuery.value =
                    CredentialSearchQuery(
                        requestGeneration,
                        SensitiveCredentialText.copyOf(intent.query),
                    )
                replaced.query.erase()
            }

            is CredentialManagementIntent.SelectCredential -> {
                selectCredential(intent.credential)
            }

            is CredentialManagementIntent.ConfirmDelete -> {
                deleteCredential(intent.credentialId)
            }

            is CredentialManagementIntent.ShowDeleteDialog -> {
                showDeleteDialog(intent.credential)
            }

            is CredentialManagementIntent.ShowDeleteAllDialog -> {
                showDeleteAllDialog()
            }

            is CredentialManagementIntent.ConfirmDeleteAll -> {
                deleteAllCredentials()
            }

            is CredentialManagementIntent.DismissDialog -> {
                dismissDialogs()
            }

            is CredentialManagementIntent.UndoDelete -> {
                undoRemove(intent.credentialId)
            }

            is CredentialManagementIntent.PendingDelete -> {
                pendingRemove(intent.credential)
            }

            is CredentialManagementIntent.CommitDelete -> {
                commitRemove(intent.credentialId)
            }
        }
    }

    private fun observeCredentials() {
        viewModelScope.launch {
            searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val queryOwner = request.query
                    val generation = request.generation
                    if (generation != requestGeneration) return@collectLatest
                    if (queryOwner.isBlank()) {
                        val replaced = fullCredentialList
                        currentOffset = 0L
                        fullCredentialList = emptyList()
                        _state.update { it.copy(credentials = emptyList()) }
                        eraseCredentialsNoLongerOwned(replaced)
                        loadCredentials()
                    } else {
                        val searchJob = currentCoroutineContext()[Job]
                        activeSearchJob = searchJob
                        val operationQuery = queryOwner.copyOwner()
                        // For search, we just load all matches without pagination
                        _state.update { it.copy(isLoading = true, error = null) }
                        val stagedResults = mutableListOf<PasskeyCredential>()
                        var transferred = false
                        try {
                            searchCredentialsUseCase(operationQuery).collect(stagedResults::add)
                            if (generation != requestGeneration) return@collectLatest
                            val replaced = fullCredentialList
                            fullCredentialList = stagedResults.toList()
                            transferred = true
                            updateStateWithFilteredCredentials()
                            _state.update { it.copy(isLoading = false, hasMore = false) }
                            eraseCredentialsNoLongerOwned(replaced)
                        } catch (_: IOException) {
                            if (generation == requestGeneration) {
                                logger.e { "Credential search failed" }
                                _state.update { it.copy(isLoading = false, error = "Credential search failed") }
                            }
                        } finally {
                            if (activeSearchJob === searchJob) activeSearchJob = null
                            operationQuery.erase()
                            if (!transferred) eraseCredentialsNoLongerOwned(stagedResults)
                        }
                    }
                }
        }
    }

    private fun loadCredentials() {
        val generation = requestGeneration
        if (pageRequestGeneration == generation) return
        pageRequestGeneration = generation
        val offset = currentOffset
        viewModelScope.launch {
            try {
                if (generation == requestGeneration) {
                    _state.update {
                        it.copy(isPaginating = offset > 0, isLoading = offset == 0L, error = null)
                    }
                }
                getAllCredentialsUseCase(pageSize, offset)
                    .onSuccess { newItems ->
                        if (generation != requestGeneration) {
                            eraseCredentialsNoLongerOwned(newItems)
                            return@onSuccess
                        }
                        val replaced = fullCredentialList
                        fullCredentialList = fullCredentialList + newItems
                        currentOffset = offset + newItems.size
                        val hasMore = newItems.size >= pageSize

                        _state.update {
                            it.copy(
                                credentials =
                                    fullCredentialList
                                        .filter { c -> c.id !in it.pendingDeleteIds }
                                        .sortedByDescending { c -> c.lastUsedAt },
                                isPaginating = false,
                                isLoading = false,
                                hasMore = hasMore,
                            )
                        }
                        eraseCredentialsNoLongerOwned(replaced)
                    }.onFailure { e ->
                        if (generation == requestGeneration) {
                            _state.update {
                                it.copy(
                                    isPaginating = false,
                                    isLoading = false,
                                    error = e.message,
                                )
                            }
                        }
                    }
            } finally {
                if (pageRequestGeneration == generation) pageRequestGeneration = null
            }
        }
    }

    private fun updateStateWithFilteredCredentials() {
        _state.update { state ->
            state.copy(
                credentials =
                    fullCredentialList
                        .filter { it.id !in state.pendingDeleteIds }
                        .sortedByDescending { it.lastUsedAt },
                isLoading = false,
            )
        }
    }

    /** Replaces the current credential batch, erasing replaced owners no longer retained in another state slot. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun setCredentials(credentials: List<PasskeyCredential>) {
        requestGeneration += 1L
        activeSearchJob?.cancel()
        val replaced = fullCredentialList
        fullCredentialList = credentials
        _state.update {
            it.copy(
                credentials = credentials.sortedByDescending { c -> c.lastUsedAt },
                isLoading = false,
            )
        }
        eraseCredentialsNoLongerOwned(replaced)
    }

    private fun selectCredential(credential: PasskeyCredential) {
        val replaced = _state.value.selectedCredential
        _state.update { it.copy(selectedCredential = credential) }
        replaced?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
    }

    private fun showDeleteDialog(credential: PasskeyCredential) {
        val replaced = _state.value.credentialToDelete
        _state.update { it.copy(credentialToDelete = credential) }
        replaced?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
    }

    private fun showDeleteAllDialog() {
        _state.update { it.copy(isShowDeleteAllWarning = true) }
    }

    private fun dismissDialogs() {
        val dismissed = listOfNotNull(_state.value.selectedCredential, _state.value.credentialToDelete)
        _state.update {
            it.copy(
                selectedCredential = null,
                credentialToDelete = null,
                isShowDeleteAllWarning = false,
            )
        }
        eraseCredentialsNoLongerOwned(dismissed)
    }

    private fun deleteCredential(credentialId: CredentialId) {
        viewModelScope.launch {
            logger.i { "Initiating deletion for credential: $credentialId" }
            val credential = _state.value.credentials.find { it.id == credentialId }
            _state.update { it.copy(isLoading = true, credentialToDelete = null) }
            val result = deleteCredentialUseCase(credentialId)
            if (result.isSuccess) {
                logger.i { "Successfully deleted credential: $credentialId" }
                val replacedLastDeleted = _state.value.lastDeleted
                _state.update { it.copy(isLoading = false, lastDeleted = credential) }
                replacedLastDeleted?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
                _effect.emit(CredentialManagementEffect.ShowToast("Credential deleted"))
            } else {
                logger.e { "Failed to delete credential: $credentialId" }
                _state.update { it.copy(isLoading = false, error = "Failed to delete credential") }
            }
        }
    }

    private fun deleteAllCredentials() {
        val useCase = deleteAllCredentialsUseCase ?: return
        viewModelScope.launch {
            logger.i { "Initiating deletion of all credentials" }
            _state.update { it.copy(isLoading = true, isShowDeleteAllWarning = false) }
            val result = useCase()
            if (result.isSuccess) {
                logger.i { "Successfully deleted all credentials" }
                _state.update { it.copy(isLoading = false) }
                _effect.emit(CredentialManagementEffect.ShowToast("All credentials deleted"))
            } else {
                logger.e { "Failed to delete all credentials" }
                _state.update { it.copy(isLoading = false, error = "Failed to delete all credentials") }
            }
        }
    }

    private fun pendingRemove(credential: PasskeyCredential) {
        logger.i { "Pending removal for credential: ${credential.id}" }
        _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds + credential.id) }
        updateStateWithFilteredCredentials()
        viewModelScope.launch {
            _removalEvents.emit(credential)
        }
    }

    private fun undoRemove(credentialId: CredentialId) {
        logger.i { "Undoing removal for credential: $credentialId" }
        _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
        updateStateWithFilteredCredentials()
    }

    private fun commitRemove(credentialId: CredentialId) {
        viewModelScope.launch {
            logger.i { "Committing removal for credential: $credentialId" }
            val result = deleteCredentialUseCase(credentialId)
            _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
            if (result.isSuccess) {
                logger.i { "Successfully committed removal for credential: $credentialId" }
                // Item is already gone from _fullCredentialList because the flow from repository
                // should emit the new state. If repository is not reactive, we'd need to manually
                // remove from _fullCredentialList.
            } else {
                logger.e { "Failed to commit removal for credential: $credentialId" }
                _effect.emit(CredentialManagementEffect.ShowToast("Failed to delete credential"))
                updateStateWithFilteredCredentials() // Restore view
            }
        }
    }

    /** Erases unique credential and query owners when this ViewModel is permanently cleared. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun onCleared() {
        activeSearchJob?.cancel()
        searchQuery.value.query.erase()
        val ownedCredentials = Collections.newSetFromMap(IdentityHashMap<PasskeyCredential, Boolean>())
        ownedCredentials.addAll(fullCredentialList)
        ownedCredentials.addAll(_state.value.credentials)
        _state.value.selectedCredential?.let(ownedCredentials::add)
        _state.value.credentialToDelete?.let(ownedCredentials::add)
        _state.value.lastDeleted?.let(ownedCredentials::add)
        ownedCredentials.forEach(PasskeyCredential::eraseSensitiveText)
        super.onCleared()
    }

    /** Erases replaced models unless another current ViewModel state slot still owns them. */
    private fun eraseCredentialsNoLongerOwned(candidates: Collection<PasskeyCredential>) {
        val retained = Collections.newSetFromMap(IdentityHashMap<PasskeyCredential, Boolean>())
        retained.addAll(fullCredentialList)
        retained.addAll(_state.value.credentials)
        _state.value.selectedCredential?.let(retained::add)
        _state.value.credentialToDelete?.let(retained::add)
        _state.value.lastDeleted?.let(retained::add)
        candidates.forEach { credential ->
            if (credential !in retained) credential.eraseSensitiveText()
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20L
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

private data class CredentialSearchQuery(
    val generation: Long,
    val query: SensitiveCredentialText,
)

data class CredentialManagementState(
    val credentials: List<PasskeyCredential> = emptyList(),
    val selectedCredential: PasskeyCredential? = null,
    val credentialToDelete: PasskeyCredential? = null,
    val lastDeleted: PasskeyCredential? = null,
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val isShowDeleteAllWarning: Boolean = false,
    val pendingDeleteIds: Set<CredentialId> = emptySet(),
)

sealed interface CredentialManagementIntent {
    object RefreshCredentials : CredentialManagementIntent

    object LoadNextPage : CredentialManagementIntent

    data class UpdateSearchQuery(
        val query: String,
    ) : CredentialManagementIntent

    data class SelectCredential(
        val credential: PasskeyCredential,
    ) : CredentialManagementIntent

    data class ShowDeleteDialog(
        val credential: PasskeyCredential,
    ) : CredentialManagementIntent

    object ShowDeleteAllDialog : CredentialManagementIntent

    object DismissDialog : CredentialManagementIntent

    data class ConfirmDelete(
        val credentialId: CredentialId,
    ) : CredentialManagementIntent

    object ConfirmDeleteAll : CredentialManagementIntent

    data class PendingDelete(
        val credential: PasskeyCredential,
    ) : CredentialManagementIntent

    data class UndoDelete(
        val credentialId: CredentialId,
    ) : CredentialManagementIntent

    data class CommitDelete(
        val credentialId: CredentialId,
    ) : CredentialManagementIntent
}

sealed interface CredentialManagementEffect {
    data class ShowToast(
        val message: String,
    ) : CredentialManagementEffect

    data class ShowUndoSnackbar(
        val message: String,
    ) : CredentialManagementEffect
}
