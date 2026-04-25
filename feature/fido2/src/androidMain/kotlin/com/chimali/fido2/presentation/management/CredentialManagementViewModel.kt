package com.chimali.fido2.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * T120 — ViewModel for managing FIDO2 Credentials.
 * Uses MVI pattern: Intent -> State -> Effect
 */
@KoinViewModel
class CredentialManagementViewModel(
    private val getAllCredentialsUseCase: GetAllCredentialsUseCase,
    private val searchCredentialsUseCase: SearchCredentialsUseCase,
    private val deleteCredentialUseCase: DeleteCredentialUseCase,
    private val deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase? = null,
) : ViewModel() {
    private val logger = Logger.withTag("CredentialManagement")
    private val _state = MutableStateFlow(CredentialManagementState())
    val state: StateFlow<CredentialManagementState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<CredentialManagementEffect>()
    val effect: SharedFlow<CredentialManagementEffect> = _effect.asSharedFlow()

    private val _removalEvents = MutableSharedFlow<PasskeyCredential>()
    val removalEvents: SharedFlow<PasskeyCredential> = _removalEvents.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    private var _fullCredentialList: List<PasskeyCredential> = emptyList()

    private var currentOffset: Long = 0L
    private val pageSize: Long = DEFAULT_PAGE_SIZE

    init {
        observeCredentials()
    }

    fun onIntent(intent: CredentialManagementIntent) {
        when (intent) {
            is CredentialManagementIntent.RefreshCredentials -> {
                currentOffset = 0L
                _fullCredentialList = emptyList()
                loadCredentials()
            }
            is CredentialManagementIntent.LoadNextPage -> {
                if (_state.value.hasMore && !_state.value.isPaginating && _searchQuery.value.isBlank()) {
                    loadCredentials()
                }
            }
            is CredentialManagementIntent.UpdateSearchQuery -> {
                logger.d { "Updating search query: ${intent.query}" }
                _searchQuery.value = intent.query
            }
            is CredentialManagementIntent.SelectCredential -> selectCredential(intent.credential)
            is CredentialManagementIntent.ConfirmDelete -> deleteCredential(intent.credentialId)
            is CredentialManagementIntent.ShowDeleteDialog -> showDeleteDialog(intent.credential)
            is CredentialManagementIntent.ShowDeleteAllDialog -> showDeleteAllDialog()
            is CredentialManagementIntent.ConfirmDeleteAll -> deleteAllCredentials()
            is CredentialManagementIntent.DismissDialog -> dismissDialogs()
            is CredentialManagementIntent.UndoDelete -> undoRemove(intent.credentialId)
            is CredentialManagementIntent.PendingDelete -> pendingRemove(intent.credential)
            is CredentialManagementIntent.CommitDelete -> commitRemove(intent.credentialId)
        }
    }

    private fun observeCredentials() {
        viewModelScope.launch {
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        currentOffset = 0L
                        _fullCredentialList = emptyList()
                        loadCredentials()
                    } else {
                        // For search, we just load all matches without pagination
                        _state.update { it.copy(isLoading = true, error = null) }
                        try {
                            val results = searchCredentialsUseCase(query).toList()
                            _fullCredentialList = results
                            updateStateWithFilteredCredentials()
                            _state.update { it.copy(isLoading = false, hasMore = false) }
                        } catch (e: Exception) {
                            _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") }
                        }
                    }
                }
        }
    }

    private fun loadCredentials() {
        viewModelScope.launch {
            _state.update { it.copy(isPaginating = currentOffset > 0, isLoading = currentOffset == 0L, error = null) }
            val result = getAllCredentialsUseCase(pageSize, currentOffset)
            result.onSuccess { newItems ->
                _fullCredentialList = _fullCredentialList + newItems
                currentOffset += newItems.size
                val hasMore = newItems.size >= pageSize

                _state.update {
                    it.copy(
                        credentials =
                            _fullCredentialList
                                .filter { c -> c.id !in it.pendingDeleteIds }
                                .sortedByDescending { c -> c.lastUsedAt },
                        isPaginating = false,
                        isLoading = false,
                        hasMore = hasMore,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isPaginating = false,
                        isLoading = false,
                        error = e.message ?: "Failed to load",
                    )
                }
            }
        }
    }

    private fun updateStateWithFilteredCredentials() {
        _state.update { state ->
            state.copy(
                credentials =
                    _fullCredentialList
                        .filter { it.id !in state.pendingDeleteIds }
                        .sortedByDescending { it.lastUsedAt },
                isLoading = false,
            )
        }
    }

    /**
     * Directly sets credentials in the state (used for testing and direct data injection).
     */
    fun setCredentials(credentials: List<PasskeyCredential>) {
        _state.update {
            it.copy(
                credentials = credentials.sortedByDescending { c -> c.lastUsedAt },
                isLoading = false,
            )
        }
    }

    private fun selectCredential(credential: PasskeyCredential) {
        _state.update { it.copy(selectedCredential = credential) }
    }

    private fun showDeleteDialog(credential: PasskeyCredential) {
        _state.update { it.copy(credentialToDelete = credential) }
    }

    private fun showDeleteAllDialog() {
        _state.update { it.copy(showDeleteAllWarning = true) }
    }

    private fun dismissDialogs() {
        _state.update {
            it.copy(
                selectedCredential = null,
                credentialToDelete = null,
                showDeleteAllWarning = false,
            )
        }
    }

    private fun deleteCredential(credentialId: String) {
        viewModelScope.launch {
            logger.i { "Initiating deletion for credential: $credentialId" }
            val credential = _state.value.credentials.find { it.id == credentialId }
            _state.update { it.copy(isLoading = true, credentialToDelete = null) }
            val result = deleteCredentialUseCase(credentialId)
            if (result.isSuccess) {
                logger.i { "Successfully deleted credential: $credentialId" }
                _state.update { it.copy(isLoading = false, lastDeleted = credential) }
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
            _state.update { it.copy(isLoading = true, showDeleteAllWarning = false) }
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

    private fun undoRemove(credentialId: String) {
        logger.i { "Undoing removal for credential: $credentialId" }
        _state.update { it.copy(pendingDeleteIds = it.pendingDeleteIds - credentialId) }
        updateStateWithFilteredCredentials()
    }

    private fun commitRemove(credentialId: String) {
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

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20L
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

data class CredentialManagementState(
    val credentials: List<PasskeyCredential> = emptyList(),
    val selectedCredential: PasskeyCredential? = null,
    val credentialToDelete: PasskeyCredential? = null,
    val lastDeleted: PasskeyCredential? = null,
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val showDeleteAllWarning: Boolean = false,
    val pendingDeleteIds: Set<String> = emptySet(),
)

sealed interface CredentialManagementIntent {
    object RefreshCredentials : CredentialManagementIntent

    object LoadNextPage : CredentialManagementIntent

    data class UpdateSearchQuery(val query: String) : CredentialManagementIntent

    data class SelectCredential(val credential: PasskeyCredential) : CredentialManagementIntent

    data class ShowDeleteDialog(val credential: PasskeyCredential) : CredentialManagementIntent

    object ShowDeleteAllDialog : CredentialManagementIntent

    object DismissDialog : CredentialManagementIntent

    data class ConfirmDelete(val credentialId: String) : CredentialManagementIntent

    object ConfirmDeleteAll : CredentialManagementIntent

    data class PendingDelete(val credential: PasskeyCredential) : CredentialManagementIntent

    data class UndoDelete(val credentialId: String) : CredentialManagementIntent

    data class CommitDelete(val credentialId: String) : CredentialManagementIntent
}

sealed interface CredentialManagementEffect {
    data class ShowToast(val message: String) : CredentialManagementEffect

    data class ShowUndoSnackbar(val message: String) : CredentialManagementEffect
}
