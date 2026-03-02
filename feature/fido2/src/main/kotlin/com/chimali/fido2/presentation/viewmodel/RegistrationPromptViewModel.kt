package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.error.RegistrationErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── MVI: Intent (user actions) ────────────────────────────────────────────────

sealed interface RegistrationIntent {
    /** User approved the registration prompt. */
    data object ConfirmRegistration : RegistrationIntent

    /** User dismissed or back-pressed the registration prompt. */
    data object CancelRegistration : RegistrationIntent

    /** User chose biometric verification. */
    data object UseBiometric : RegistrationIntent

    /** User chose PIN verification. */
    data class UsePinVerification(val pin: String) : RegistrationIntent

    /** Retry after an error. */
    data object Retry : RegistrationIntent

    /** ViewModel needs to be initialised with the incoming CTAP2 request. */
    data class InitRegistration(val options: MakeCredentialOptions) : RegistrationIntent
}

// ── MVI: State ────────────────────────────────────────────────────────────────

sealed interface RegistrationState {
    /** Initial; not yet received a CTAP2 MakeCredential request. */
    data object Idle : RegistrationState

    /** Awaiting user confirmation (shows the prompt). */
    data class AwaitingUserConsent(
        val rpId: String,
        val rpName: String,
        val userName: String,
        val userDisplayName: String,
        val availableMethod: VerificationMethod
    ) : RegistrationState

    /** User confirmed; awaiting biometric. */
    data object AwaitingBiometric : RegistrationState

    /** User confirmed; awaiting PIN entry. */
    data object AwaitingPin : RegistrationState

    /** Performing cryptographic registration. */
    data object Processing : RegistrationState

    /** Registration completed successfully. */
    data class Success(val credential: PasskeyCredential) : RegistrationState

    /** Registration failed; may be retried. */
    data class Error(
        val message: String,
        val isRetryable: Boolean = true
    ) : RegistrationState

    /** User cancelled — presenter should dismiss and notify CTAP2 layer. */
    data object Cancelled : RegistrationState
}

// ── MVI: Side-effects ─────────────────────────────────────────────────────────

sealed interface RegistrationEffect {
    data object NavigateToBiometricPrompt : RegistrationEffect
    data object NavigateToPinEntry       : RegistrationEffect
    data class NavigateToSuccess(val credential: PasskeyCredential) : RegistrationEffect
    data object NavigateBack             : RegistrationEffect
    data class ShowSnackbar(val message: String) : RegistrationEffect
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * T062 — MVI ViewModel for the FIDO2 Registration Prompt.
 *
 * Receives [RegistrationIntent]s from the UI, updates [RegistrationState], and emits
 * one-shot [RegistrationEffect]s for navigation/toast side-effects.
 *
 * Flow:
 * 1. CTAP2 layer calls `initRegistration(options)`
 * 2. VM moves to [RegistrationState.AwaitingUserConsent] and shows the prompt
 * 3. User confirms → biometric or PIN → [RegistrationState.Processing]
 * 4. [Fido2Service] performs registration → [RegistrationState.Success] or [RegistrationState.Error]
 */
@HiltViewModel
class RegistrationPromptViewModel @Inject constructor(
    private val fido2Service: Fido2Service,
    private val userVerificationService: UserVerificationService
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    private val _effects = Channel<RegistrationEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var pendingOptions: MakeCredentialOptions? = null

    // ── Intent dispatch ───────────────────────────────────────────────────────

    fun handleIntent(intent: RegistrationIntent) {
        when (intent) {
            is RegistrationIntent.InitRegistration    -> initRegistration(intent.options)
            is RegistrationIntent.ConfirmRegistration -> confirmRegistration()
            is RegistrationIntent.CancelRegistration  -> cancelRegistration()
            is RegistrationIntent.UseBiometric        -> startBiometricVerification()
            is RegistrationIntent.UsePinVerification  -> startPinVerification(intent.pin)
            is RegistrationIntent.Retry               -> retryRegistration()
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun initRegistration(options: MakeCredentialOptions) {
        pendingOptions = options
        viewModelScope.launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            _state.value = RegistrationState.AwaitingUserConsent(
                rpId             = options.rp.id,
                rpName           = options.rp.name,
                userName         = options.user.name,
                userDisplayName  = options.user.displayName ?: options.user.name,
                availableMethod  = availability.getBestAvailableMethod()
            )
        }
    }

    private fun confirmRegistration() {
        val options = pendingOptions ?: run {
            _state.value = RegistrationState.Error("No pending registration request", false)
            return
        }
        viewModelScope.launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            when (availability.getBestAvailableMethod()) {
                VerificationMethod.BIOMETRIC -> {
                    _state.value = RegistrationState.AwaitingBiometric
                    emit(RegistrationEffect.NavigateToBiometricPrompt)
                }
                VerificationMethod.PIN -> {
                    _state.value = RegistrationState.AwaitingPin
                    emit(RegistrationEffect.NavigateToPinEntry)
                }
                VerificationMethod.DEVICE_LOCK,
                VerificationMethod.NONE -> {
                    // No UV required / available — proceed without verification
                    performRegistration(options)
                }
                else -> performRegistration(options)
            }
        }
    }

    private fun startBiometricVerification() {
        val options = pendingOptions ?: return
        viewModelScope.launch {
            _state.value = RegistrationState.AwaitingBiometric
            val result = userVerificationService.verifyBiometric(
                prompt = "Verify to register passkey for ${options.rp.name}",
                rpId   = options.rp.id
            )
            if (result.isSuccess) {
                performRegistration(options)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Biometric verification failed"
                _state.value = RegistrationState.Error(msg)
                emit(RegistrationEffect.ShowSnackbar(msg))
            }
        }
    }

    private fun startPinVerification(pin: String) {
        val options = pendingOptions ?: return
        viewModelScope.launch {
            val result = userVerificationService.verifyPin(
                prompt = "Enter PIN to register passkey",
                rpId   = options.rp.id
            )
            if (result.isSuccess) {
                performRegistration(options)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "PIN verification failed"
                _state.value = RegistrationState.Error(msg)
                emit(RegistrationEffect.ShowSnackbar(msg))
            }
        }
    }

    private fun cancelRegistration() {
        pendingOptions = null
        _state.value = RegistrationState.Cancelled
        viewModelScope.launch { emit(RegistrationEffect.NavigateBack) }
    }

    private fun retryRegistration() {
        val options = pendingOptions ?: run {
            _state.value = RegistrationState.Error("No pending registration request", false)
            return
        }
        initRegistration(options)
    }

    private fun performRegistration(options: MakeCredentialOptions) {
        _state.value = RegistrationState.Processing
        viewModelScope.launch {
            val result = fido2Service.makeCredential(options)
            result.onSuccess { attestation ->
                // Build a lightweight display credential from the attestation metadata
                val credential = PasskeyCredential.fromMakeCredentialOptions(options)
                _state.value = RegistrationState.Success(credential)
                emit(RegistrationEffect.NavigateToSuccess(credential))
            }
            result.onFailure { error ->
                // T074 — delegate error classification to RegistrationErrorHandler
                val ui = RegistrationErrorHandler.handle(error)
                _state.value = RegistrationState.Error(ui.message, ui.isRetryable)
            }
        }
    }

    private suspend fun emit(effect: RegistrationEffect) {
        _effects.send(effect)
    }
}
