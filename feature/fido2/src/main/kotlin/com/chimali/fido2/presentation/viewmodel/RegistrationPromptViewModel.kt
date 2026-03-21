package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.error.Fido2ErrorHandler
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── MVI: Intent (user actions) ────────────────────────────────────────────────

sealed interface RegistrationIntent {
    /** User approved the registration prompt. */
    data object ConfirmRegistration : RegistrationIntent

    /** User dismissed or back-pressed the registration prompt. */
    data object CancelRegistration : RegistrationIntent

    /** User chose to start verification (either biometric or PIN via system). */
    data object VerifyUser : RegistrationIntent

    /** System verification succeeded. */
    data object UserVerificationSuccess : RegistrationIntent

    /** System verification failed. */
    data class UserVerificationFailed(val message: String) : RegistrationIntent

    /** Retry after an error. */
    data object Retry : RegistrationIntent

    /** ViewModel needs to be initialized with the incoming CTAP2 request. */
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

    /** User confirmed; awaiting system verification (Biometric/PIN). */
    data object AwaitingUserVerification : RegistrationState

    /** Performing cryptographic registration. */
    data object Processing : RegistrationState

    /** Registration completed successfully. */
    data class Success(val credential: PasskeyCredential) : RegistrationState

    /** Registration failed; may be retried. */
    data class Error(
        val message: String,
        val isRetryable: Boolean = true
    ) : RegistrationState

    /** User canceled — presenter should dismiss and notify CTAP2 layer. */
    data object Cancelled : RegistrationState
}

// ── MVI: Side-effects ─────────────────────────────────────────────────────────

sealed interface RegistrationEffect {
    data class LaunchSystemPrompt(val promptTitle: String, val promptSubtitle: String) : RegistrationEffect
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
    private val userVerificationService: UserVerificationService,
    private val uiEventBus: Fido2UiEventBus
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    private val _effects = Channel<RegistrationEffect>(Channel.BUFFERED)
    val effects: Flow<RegistrationEffect> = _effects.receiveAsFlow()

    private var pendingOptions: MakeCredentialOptions? = null
    private var pendingDeferred: CompletableDeferred<*>? = null

    init {
        Timber.d("RegistrationPromptViewModel created — subscribing to event bus")
        // Observe event bus for incoming registration requests from transport.
        // Guard: if we are already showing an error to the user, do NOT let a PC retry
        // silently overwrite the error screen — the user must dismiss/retry first.
        uiEventBus.events
            .filterIsInstance<Fido2UiEvent.RegistrationRequested>()
            .onEach { event ->
                Timber.d("RegistrationRequested received via SharedFlow: rpId=%s", event.options.rp.id)
                if (_state.value is RegistrationState.Error) {
                    Timber.d("Ignoring incoming request — currently showing error to user")
                    return@onEach
                }
                pendingDeferred = event.deferred
                initRegistration(event.options)
            }
            .launchIn(viewModelScope)

        // Also consume any event stored before this ViewModel was created (replay backup).
        uiEventBus.currentRegistrationRequest?.let { event ->
            Timber.d("RegistrationRequested present in currentRequest cache: rpId=%s", event.options.rp.id)
            pendingDeferred = event.deferred
            initRegistration(event.options)
            uiEventBus.clearRegistrationRequest()
        } ?: Timber.d("No currentRegistrationRequest in cache at init time")
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    fun handleIntent(intent: RegistrationIntent) {
        when (intent) {
            is RegistrationIntent.InitRegistration      -> initRegistration(intent.options)
            is RegistrationIntent.ConfirmRegistration   -> confirmRegistration()
            is RegistrationIntent.CancelRegistration    -> cancelRegistration()
            is RegistrationIntent.VerifyUser            -> startSystemVerification()
            is RegistrationIntent.UserVerificationSuccess -> {
                pendingOptions?.let { performRegistration(it) }
            }
            is RegistrationIntent.UserVerificationFailed -> {
                _state.value = RegistrationState.Error(intent.message)
                viewModelScope.launch { emit(RegistrationEffect.ShowSnackbar(intent.message)) }
            }
            is RegistrationIntent.Retry                 -> retryRegistration()
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
                userDisplayName  = options.user.displayName.ifEmpty { options.user.name },
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

            if (availability.getBestAvailableMethod() == VerificationMethod.NONE) {
                // No UV required / available — proceed without verification
                performRegistration(options)
            } else {
                startSystemVerification()
            }
        }
    }

    private fun startSystemVerification() {
        val options = pendingOptions ?: return
        viewModelScope.launch {
            _state.value = RegistrationState.AwaitingUserVerification
            val promptTitle = "Create Passkey"
            val promptSubtitle = options.rp.name
            emit(RegistrationEffect.LaunchSystemPrompt(promptTitle, promptSubtitle))
        }
    }

    private fun cancelRegistration() {
        @Suppress("UNCHECKED_CAST")
        val deferred = pendingDeferred as? CompletableDeferred<Result<com.chimali.fido2.domain.model.AttestationObject>>
        deferred?.complete(Result.failure(Fido2Exception.UserVerificationException("Cancelled by user")))
        pendingOptions = null
        pendingDeferred = null
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

            result.onSuccess { _ ->
                // Complete transport's deferred only on success
                @Suppress("UNCHECKED_CAST")
                val deferred = pendingDeferred as? CompletableDeferred<Result<com.chimali.fido2.domain.model.AttestationObject>>
                deferred?.complete(result)

                // Build a lightweight display credential from the attestation metadata
                val credential = PasskeyCredential.fromMakeCredentialOptions(options)
                _state.value = RegistrationState.Success(credential)

                // Hold the success screen for a moment so the user can read it before
                // navigating away. The transport deferred is already resolved above.
                delay(SUCCESS_DISPLAY_DURATION_MS)
                emit(RegistrationEffect.NavigateToSuccess(credential))

                // Clear pending only after success — on failure we keep them so Retry works
                pendingOptions = null
                pendingDeferred = null
            }
            result.onFailure { error ->
                // Complete the transport deferred with the failure so the PC gets a response
                @Suppress("UNCHECKED_CAST")
                val deferred = pendingDeferred as? CompletableDeferred<Result<com.chimali.fido2.domain.model.AttestationObject>>
                deferred?.complete(result)
                pendingDeferred = null // deferred is consumed; pendingOptions kept for retry

                Timber.e(error, "Registration process failed")

                // T149 / T152 — delegate error classification to Fido2ErrorHandler
                val ui = Fido2ErrorHandler.handle(error)
                _state.value = RegistrationState.Error(ui.message, ui.isRetryable)
            }
        }
    }

    private suspend fun emit(effect: RegistrationEffect) {
        _effects.send(effect)
    }

    companion object {
        /** How long the success screen is shown before automatically dismissing (ms). */
        private const val SUCCESS_DISPLAY_DURATION_MS = 2_000L
    }
}
