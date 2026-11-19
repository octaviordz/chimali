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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _removalEvents = Channel<CredentialId>(Channel.BUFFERED)

    /** One-shot, non-secret notifications; the pending-removal map retains credential ownership. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val removalEvents: Flow<CredentialId> = _removalEvents.receiveAsFlow()
    private val pendingRemovalOwners = mutableMapOf<CredentialId, PasskeyCredential>()
    private val deleteAllCommittedIds = mutableSetOf<CredentialId>()

    private val searchQuery =
        MutableStateFlow(CredentialSearchQuery(0L, SensitiveCredentialText.copyOf("")))
    private var fullCredentialList: List<PasskeyCredential> = emptyList()

    private var currentOffset: Long = 0L
    private val pageSize: Long = DEFAULT_PAGE_SIZE
    private var requestGeneration: Long = 0L
    private var pageRequestGeneration: Long? = null
    private var activeSearchJob: Job? = null
    private var screenActive: Boolean = true

    init {
        observeCredentials()
    }

    /** Applies a management intent while preserving the ViewModel's credential-owner lifetimes. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun onIntent(intent: CredentialManagementIntent) {
        if (!screenActive &&
            intent != CredentialManagementIntent.ScreenStarted &&
            intent != CredentialManagementIntent.ScreenStopped &&
            intent != CredentialManagementIntent.RefreshCredentials
        ) {
            return
        }
        when (intent) {
            CredentialManagementIntent.ScreenStarted -> {
                if (!screenActive) {
                    screenActive = true
                    onIntent(CredentialManagementIntent.RefreshCredentials)
                }
            }

            CredentialManagementIntent.ScreenStopped -> suspendScreenOwners()

            is CredentialManagementIntent.RefreshCredentials -> {
                screenActive = true
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
                    if (generation != requestGeneration || !screenActive) return@collectLatest
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
                        fullCredentialList =
                            combineOwnedCredentialPage(fullCredentialList, newItems) { current, incoming ->
                                current + incoming
                            }
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
                    }.onFailure { _ ->
                        if (generation == requestGeneration) {
                            _state.update {
                                it.copy(
                                    isPaginating = false,
                                    isLoading = false,
                                    error = "Credential loading failed",
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

    /** Drops screen-session credential and query owners when the management UI stops. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun suspendScreenOwners() {
        screenActive = false
        requestGeneration += 1L
        activeSearchJob?.cancel()
        activeSearchJob = null
        pageRequestGeneration = null

        val ownedCredentials = Collections.newSetFromMap(IdentityHashMap<PasskeyCredential, Boolean>())
        ownedCredentials.addAll(fullCredentialList)
        ownedCredentials.addAll(_state.value.credentials)
        _state.value.selectedCredential?.let(ownedCredentials::add)
        _state.value.credentialToDelete?.let(ownedCredentials::add)
        ownedCredentials.addAll(pendingRemovalOwners.values)

        fullCredentialList = emptyList()
        pendingRemovalOwners.clear()
        deleteAllCommittedIds.clear()
        _state.value = CredentialManagementState()
        while (_removalEvents.tryReceive().isSuccess) {
            // Discard stale one-shot identifiers; no removal is committed while stopped.
        }

        val previousQuery = searchQuery.value
        searchQuery.value =
            CredentialSearchQuery(
                requestGeneration,
                SensitiveCredentialText.copyOf(""),
            )
        previousQuery.query.erase()
        ownedCredentials.forEach(PasskeyCredential::eraseSensitiveText)
    }

    private fun deleteCredential(credentialId: CredentialId) {
        viewModelScope.launch {
            logger.i { "Initiating credential deletion" }
            _state.update { it.copy(isLoading = true, credentialToDelete = null) }
            val result = deleteCredentialUseCase(credentialId)
            if (result.isSuccess) {
                logger.i { "Credential deletion completed" }
                val removed =
                    fullCredentialList.filter { it.id == credentialId } +
                        _state.value.credentials.filter { it.id == credentialId }
                fullCredentialList = fullCredentialList.filterNot { it.id == credentialId }
                _state.update {
                    it.copy(
                        credentials = it.credentials.filterNot { item -> item.id == credentialId },
                        selectedCredential = it.selectedCredential?.takeUnless { item -> item.id == credentialId },
                        credentialToDelete = it.credentialToDelete?.takeUnless { item -> item.id == credentialId },
                        isLoading = false,
                    )
                }
                eraseCredentialsNoLongerOwned(removed)
                _effect.emit(CredentialManagementEffect.ShowToast("Credential deleted"))
            } else {
                logger.e { "Credential deletion failed" }
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
                val removed =
                    fullCredentialList +
                        _state.value.credentials +
                        listOfNotNull(_state.value.selectedCredential, _state.value.credentialToDelete)
                deleteAllCommittedIds.addAll(pendingRemovalOwners.keys)
                fullCredentialList = emptyList()
                _state.update {
                    it.copy(
                        credentials = emptyList(),
                        selectedCredential = null,
                        credentialToDelete = null,
                        isLoading = false,
                        isShowDeleteAllWarning = false,
                    )
                }
                eraseCredentialsNoLongerOwned(removed)
                _effect.emit(CredentialManagementEffect.ShowToast("All credentials deleted"))
            } else {
                logger.e { "Failed to delete all credentials" }
                _state.update { it.copy(isLoading = false, error = "Failed to delete all credentials") }
            }
        }
    }

    private fun pendingRemove(credential: PasskeyCredential) {
        logger.i { "Credential removal pending" }
        pendingRemovalOwners[credential.id] = credential
        _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds + credential.id) }
        updateStateWithFilteredCredentials()
        viewModelScope.launch {
            _removalEvents.send(credential.id)
        }
    }

    private fun undoRemove(credentialId: CredentialId) {
        logger.i { "Credential removal undo requested" }
        _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
        if (deleteAllCommittedIds.remove(credentialId)) {
            pendingRemovalOwners.remove(credentialId)?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
        } else {
            updateStateWithFilteredCredentials()
            pendingRemovalOwners.remove(credentialId)?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
        }
    }

    private fun commitRemove(credentialId: CredentialId) {
        viewModelScope.launch {
            logger.i { "Committing credential removal" }
            if (deleteAllCommittedIds.remove(credentialId)) {
                _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
                pendingRemovalOwners.remove(credentialId)?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
                return@launch
            }
            val result = deleteCredentialUseCase(credentialId)
            if (result.isSuccess) {
                logger.i { "Credential removal commit completed" }
                val removed = fullCredentialList.filter { it.id == credentialId }
                val pendingOwner = pendingRemovalOwners.remove(credentialId)
                fullCredentialList = fullCredentialList.filterNot { it.id == credentialId }
                _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
                updateStateWithFilteredCredentials()
                eraseCredentialsNoLongerOwned(removed + listOfNotNull(pendingOwner))
            } else {
                logger.e { "Credential removal commit failed" }
                val pendingOwner = pendingRemovalOwners.remove(credentialId)
                _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
                _effect.emit(CredentialManagementEffect.ShowToast("Failed to delete credential"))
                updateStateWithFilteredCredentials() // Restore view
                pendingOwner?.let { eraseCredentialsNoLongerOwned(listOf(it)) }
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
        ownedCredentials.addAll(pendingRemovalOwners.values)
        ownedCredentials.forEach(PasskeyCredential::eraseSensitiveText)
        _removalEvents.cancel()
        super.onCleared()
    }

    /** Erases replaced models unless another current ViewModel state slot still owns them. */
    private fun eraseCredentialsNoLongerOwned(candidates: Collection<PasskeyCredential>) {
        val retained = Collections.newSetFromMap(IdentityHashMap<PasskeyCredential, Boolean>())
        retained.addAll(fullCredentialList)
        retained.addAll(_state.value.credentials)
        _state.value.selectedCredential?.let(retained::add)
        _state.value.credentialToDelete?.let(retained::add)
        retained.addAll(pendingRemovalOwners.values)
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

/** Keeps a fetched page owned by the caller until the replacement page list is constructed. */
internal fun combineOwnedCredentialPage(
    retained: List<PasskeyCredential>,
    incoming: List<PasskeyCredential>,
    combine: (List<PasskeyCredential>, List<PasskeyCredential>) -> List<PasskeyCredential>,
): List<PasskeyCredential> {
    var transferred = false
    try {
        val combined = combine(retained, incoming)
        transferred = true
        return combined
    } finally {
        if (!transferred) {
            val retainedOwners = Collections.newSetFromMap(IdentityHashMap<PasskeyCredential, Boolean>())
            retainedOwners.addAll(retained)
            incoming.filterNot(retainedOwners::contains).forEach(PasskeyCredential::eraseSensitiveText)
        }
    }
}

data class CredentialManagementState(
    val credentials: List<PasskeyCredential> = emptyList(),
    val selectedCredential: PasskeyCredential? = null,
    val credentialToDelete: PasskeyCredential? = null,
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val isShowDeleteAllWarning: Boolean = false,
    val pendingDeleteIds: Set<CredentialId> = emptySet(),
)

sealed interface CredentialManagementIntent {
    object ScreenStarted : CredentialManagementIntent

    object ScreenStopped : CredentialManagementIntent

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
