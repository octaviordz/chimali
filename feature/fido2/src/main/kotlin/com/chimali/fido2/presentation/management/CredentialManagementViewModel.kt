package com.chimali.fido2.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import com.chimali.fido2.domain.usecase.UpdateCredentialLabelUseCase
import kotlinx.coroutines.flow.*
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
    private val updateCredentialLabelUseCase: UpdateCredentialLabelUseCase? = null,
) : ViewModel() {
    private val logger = Logger.withTag("CredentialManagement")
    private val _state = MutableStateFlow(CredentialManagementState())
    val state: StateFlow<CredentialManagementState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<CredentialManagementEffect>()
    val effect: SharedFlow<CredentialManagementEffect> = _effect.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeCredentials()
    }

    fun onIntent(intent: CredentialManagementIntent) {
        when (intent) {
            is CredentialManagementIntent.RefreshCredentials -> observeCredentials()
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
            is CredentialManagementIntent.UndoDelete -> undoDelete()
        }
    }

    private fun observeCredentials() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .flatMapLatest { query ->
                    logger.d { "Performing search for: $query" }
                    _state.update { it.copy(isLoading = true, error = null) }
                    val sourceFlow = if (query.isBlank()) {
                        getAllCredentialsUseCase()
                    } else {
                        searchCredentialsUseCase(query)
                    }
                    // Collect individual PasskeyCredential items into a single list emission
                    kotlinx.coroutines.flow.flow {
                        val list = sourceFlow.toList()
                        emit(list)
                    }
                }
                .catch { e ->
                    logger.e(e) { "Failed to load credentials" }
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") }
                }
                .collect { credentials ->
                    logger.d { "Loaded ${credentials.size} credentials" }
                    _state.update {
                        it.copy(
                            credentials = credentials.sortedByDescending { c -> c.lastUsedAt },
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * Directly sets credentials in the state (used for testing and direct data injection).
     */
    fun setCredentials(credentials: List<PasskeyCredential>) {
        _state.update {
            it.copy(
                credentials = credentials.sortedByDescending { c -> c.lastUsedAt },
                isLoading = false
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

    private fun undoDelete() {
        val lastDeleted = _state.value.lastDeleted ?: return
        logger.i { "Attempting to undo deletion of credential: ${lastDeleted.id}" }
        viewModelScope.launch {
            _state.update { it.copy(lastDeleted = null) }
            _effect.emit(CredentialManagementEffect.ShowToast("Undo not fully implemented in DB layer yet"))
        }
    }

}

data class CredentialManagementState(
    val credentials: List<PasskeyCredential> = emptyList(),
    val selectedCredential: PasskeyCredential? = null,
    val credentialToDelete: PasskeyCredential? = null,
    val lastDeleted: PasskeyCredential? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteAllWarning: Boolean = false,
)

sealed interface CredentialManagementIntent {
    object RefreshCredentials : CredentialManagementIntent
    data class UpdateSearchQuery(val query: String) : CredentialManagementIntent
    data class SelectCredential(val credential: PasskeyCredential) : CredentialManagementIntent
    data class ShowDeleteDialog(val credential: PasskeyCredential) : CredentialManagementIntent
    object ShowDeleteAllDialog : CredentialManagementIntent
    object DismissDialog : CredentialManagementIntent
    data class ConfirmDelete(val credentialId: String) : CredentialManagementIntent
    object ConfirmDeleteAll : CredentialManagementIntent
    object UndoDelete : CredentialManagementIntent
}

sealed interface CredentialManagementEffect {
    data class ShowToast(val message: String) : CredentialManagementEffect
    data class ShowUndoSnackbar(val message: String) : CredentialManagementEffect
}
