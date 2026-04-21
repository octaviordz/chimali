package com.chimali.fido2.presentation.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
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
                    if (query.isBlank()) {
                        getAllCredentialsUseCase()
                    } else {
                        searchCredentialsUseCase(query)
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


    private fun selectCredential(credential: PasskeyCredential) {
        _state.update { it.copy(selectedCredential = credential) }
    }

    private fun showDeleteDialog(credential: PasskeyCredential) {
        _state.update { it.copy(credentialToDelete = credential) }
    }

    private fun dismissDialogs() {
        _state.update { it.copy(
            credentialToDelete = null,
            selectedCredential = null
        ) }
    }

    private fun deleteCredential(credentialId: String) {
        viewModelScope.launch {
            logger.i { "Initiating deletion for credential: $credentialId" }
            val credential = _state.value.credentials.find { it.id == credentialId }
            _state.update { it.copy(isLoading = true, credentialToDelete = null) }
            val result = deleteCredentialUseCase(credentialId)
            if (result.isSuccess) {
                logger.i { "Successfully deleted credential: $credentialId" }
                _state.update { it.copy(lastDeleted = credential) }
                _effect.emit(CredentialManagementEffect.ShowUndoSnackbar("Passkey deleted"))
            } else {
                logger.e { "Failed to delete credential: $credentialId" }
                _state.update { it.copy(isLoading = false, error = "Failed to delete passkey") }
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
)

sealed interface CredentialManagementIntent {
    object RefreshCredentials : CredentialManagementIntent
    data class UpdateSearchQuery(val query: String) : CredentialManagementIntent
    data class SelectCredential(val credential: PasskeyCredential) : CredentialManagementIntent
    data class ShowDeleteDialog(val credential: PasskeyCredential) : CredentialManagementIntent
    object DismissDialog : CredentialManagementIntent
    data class ConfirmDelete(val credentialId: String) : CredentialManagementIntent
    object UndoDelete : CredentialManagementIntent
}

sealed interface CredentialManagementEffect {
    data class ShowToast(val message: String) : CredentialManagementEffect
    data class ShowUndoSnackbar(val message: String) : CredentialManagementEffect
}
