package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.error.Fido2ErrorHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

// ── MVI: Intent ───────────────────────────────────────────────────────────────

sealed interface AuthenticationIntent {
    data class InitAuthentication(val options: GetAssertionOptions) : AuthenticationIntent

    data object ConfirmAuthentication : AuthenticationIntent

    data object CancelAuthentication : AuthenticationIntent

    data object VerifyUser : AuthenticationIntent

    data object UserVerificationSuccess : AuthenticationIntent

    data class UserVerificationFailed(val message: String) : AuthenticationIntent

    data class SelectCredential(val credential: PasskeyCredential) : AuthenticationIntent

    data object Retry : AuthenticationIntent
}

// ── MVI: State ────────────────────────────────────────────────────────────────

sealed interface AuthenticationState {
    data object Idle : AuthenticationState

    data class AwaitingUserConsent(
        val rpId: String,
        val rpName: String,
        val availableMethod: VerificationMethod,
        val credentialCount: Int,
    ) : AuthenticationState

    data class SelectingCredential(
        val rpId: String,
        val credentials: List<PasskeyCredential>,
    ) : AuthenticationState

    data object AwaitingUserVerification : AuthenticationState

    data object Processing : AuthenticationState

    data class Success(val assertion: AssertionObject) : AuthenticationState

    data class Error(val message: String, val isRetryable: Boolean = true) : AuthenticationState

    data object Cancelled : AuthenticationState
}

// ── MVI: Effects ─────────────────────────────────────────────────────────────

sealed interface AuthenticationEffect {
    data class LaunchSystemPrompt(val promptTitle: String, val promptSubtitle: String) : AuthenticationEffect

    data class NavigateToSuccess(val assertion: AssertionObject) : AuthenticationEffect

    data object NavigateBack : AuthenticationEffect

    data class ShowSnackbar(val message: String) : AuthenticationEffect
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * T095 — MVI ViewModel for the FIDO2 Authentication Prompt.
 *
 * Mirrors the structure of [RegistrationPromptViewModel] for consistency.
 * Drives [GetAssertionUseCase] via intents and emits state/effects.
 */
@KoinViewModel
class AuthenticationPromptViewModel(
    private val getAssertionUseCase: GetAssertionUseCase,
    private val userVerificationService: UserVerificationService,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthenticationState>(AuthenticationState.Idle)
    val state: StateFlow<AuthenticationState> = _state.asStateFlow()

    private val _effects = Channel<AuthenticationEffect>(Channel.BUFFERED)
    val effects: Flow<AuthenticationEffect> = _effects.receiveAsFlow()

    private var pendingOptions: GetAssertionOptions? = null

    // ── Intent dispatch ───────────────────────────────────────────────────────

    fun handleIntent(intent: AuthenticationIntent) {
        when (intent) {
            is AuthenticationIntent.InitAuthentication -> initAuthentication(intent.options)
            is AuthenticationIntent.ConfirmAuthentication -> confirmAuthentication()
            is AuthenticationIntent.CancelAuthentication -> cancel()
            is AuthenticationIntent.VerifyUser -> startSystemVerification()
            is AuthenticationIntent.UserVerificationSuccess -> {
                pendingOptions?.let { performAuthentication(it) }
            }
            is AuthenticationIntent.UserVerificationFailed -> {
                _state.value = AuthenticationState.Error(intent.message)
                viewModelScope.launch { emit(AuthenticationEffect.ShowSnackbar(intent.message)) }
            }
            is AuthenticationIntent.SelectCredential -> onCredentialSelected(intent.credential)
            is AuthenticationIntent.Retry -> retry()
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun initAuthentication(options: GetAssertionOptions) {
        pendingOptions = options
        viewModelScope.launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            _state.value =
                AuthenticationState.AwaitingUserConsent(
                    rpId = options.rpId,
                    // RP name resolved from repo in a future pass
                    rpName = options.rpId,
                    availableMethod = availability.getBestAvailableMethod(),
                    credentialCount = options.allowCredentials?.size ?: 0,
                )
        }
    }

    private fun confirmAuthentication() {
        val options =
            pendingOptions ?: run {
                _state.value = AuthenticationState.Error("No pending authentication request", false)
                return
            }
        viewModelScope.launch {
            val availability = userVerificationService.getUserVerificationAvailability()

            if (availability.getBestAvailableMethod() == VerificationMethod.NONE) {
                performAuthentication(options)
            } else {
                startSystemVerification()
            }
        }
    }

    private fun startSystemVerification() {
        val options = pendingOptions ?: return
        viewModelScope.launch {
            _state.value = AuthenticationState.AwaitingUserVerification
            val promptTitle = "Sign in"
            val promptSubtitle = options.rpId
            emit(AuthenticationEffect.LaunchSystemPrompt(promptTitle, promptSubtitle))
        }
    }

    private fun onCredentialSelected(credential: PasskeyCredential) {
        // Re-run with updated allow-list that only contains selected credential
        val options = pendingOptions ?: return
        val filtered =
            listOf(com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor.create(id = credential.credentialId))
        pendingOptions = options.copy(allowCredentials = filtered)
        confirmAuthentication()
    }

    private fun cancel() {
        pendingOptions = null
        _state.value = AuthenticationState.Cancelled
        viewModelScope.launch { emit(AuthenticationEffect.NavigateBack) }
    }

    private fun retry() {
        val options =
            pendingOptions ?: run {
                _state.value = AuthenticationState.Error("No pending request", false)
                return
            }
        initAuthentication(options)
    }

    private fun performAuthentication(options: GetAssertionOptions) {
        _state.value = AuthenticationState.Processing
        viewModelScope.launch {
            getAssertionUseCase(options)
                .onSuccess { assertion ->
                    _state.value = AuthenticationState.Success(assertion)
                    emit(AuthenticationEffect.NavigateToSuccess(assertion))
                }
                .onFailure { error ->
                    Logger.e(error) { "Authentication process failed" }
                    val ui = Fido2ErrorHandler.handle(error)
                    _state.value = AuthenticationState.Error(ui.message, ui.isRetryable)
                }
        }
    }

    private suspend fun emit(effect: AuthenticationEffect) = _effects.send(effect)
}
