package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.error.Fido2ErrorHandler
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import kotlin.time.Duration.Companion.milliseconds
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
import org.koin.android.annotation.KoinViewModel

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
        val availableMethod: VerificationMethod,
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
        val isRetryable: Boolean = true,
    ) : RegistrationState

    /** User canceled — presenter should dismiss and notify CTAP2 layer. */
    data object Cancelled : RegistrationState
}

// ── MVI: Side-effects ─────────────────────────────────────────────────────────

sealed interface RegistrationEffect {
    data class LaunchSystemPrompt(val promptTitle: String, val promptSubtitle: String) : RegistrationEffect

    data class NavigateToSuccess(val credential: PasskeyCredential) : RegistrationEffect

    data object NavigateBack : RegistrationEffect

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
@KoinViewModel
class RegistrationPromptViewModel(
    private val fido2Service: Fido2Service,
    private val userVerificationService: UserVerificationService,
    private val uiEventBus: Fido2UiEventBus,
) : ViewModel() {
    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    private val _effects = Channel<RegistrationEffect>(Channel.BUFFERED)
    val effects: Flow<RegistrationEffect> = _effects.receiveAsFlow()

    private var pendingOptions: MakeCredentialOptions? = null
    private var pendingDeferred: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>? = null

    init {
        Logger.d { "RegistrationPromptViewModel created — subscribing to event bus" }
        // Observe event bus for incoming registration requests from transport.
        // Guard: if we are already showing an error to the user, do NOT let a PC retry
        // silently overwrite the error screen — the user must dismiss/retry first.
        uiEventBus.events
            .filterIsInstance<Fido2UiEvent.RegistrationRequested>()
            .onEach { event ->
                Logger.d { "RegistrationRequested received via SharedFlow: rpId=${event.options.rp.id}" }
                // Guard: If we are already processing a ceremony or showing an error,
                // do NOT let a background retry overwrite our current state/deferred.
                if (_state.value !is RegistrationState.Idle) {
                    Logger.d {
                        "Ignoring incoming RegistrationRequested — current state: ${_state.value::class.simpleName}"
                    }
                    return@onEach
                }
                uiEventBus.clearRegistrationRequest()
                pendingDeferred = event.deferred
                initRegistration(event.options)
            }
            .launchIn(viewModelScope)

        // Also consume any event stored before this ViewModel was created (replay backup).
        uiEventBus.currentRegistrationRequest?.let { event ->
            Logger.d { "RegistrationRequested present in currentRequest cache: rpId=${event.options.rp.id}" }
            pendingDeferred = event.deferred
            initRegistration(event.options)
            uiEventBus.clearRegistrationRequest()
        } ?: Logger.d { "No currentRegistrationRequest in cache at init time" }
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    fun handleIntent(intent: RegistrationIntent) {
        when (intent) {
            is RegistrationIntent.InitRegistration -> initRegistration(intent.options)
            is RegistrationIntent.ConfirmRegistration -> confirmRegistration()
            is RegistrationIntent.CancelRegistration -> cancelRegistration()
            is RegistrationIntent.VerifyUser -> startSystemVerification()
            is RegistrationIntent.UserVerificationSuccess -> {
                pendingOptions?.let { performRegistration(it) }
            }
            is RegistrationIntent.UserVerificationFailed -> {
                _state.value = RegistrationState.Error(intent.message)
                viewModelScope.launch { emit(RegistrationEffect.ShowSnackbar(intent.message)) }
            }
            is RegistrationIntent.Retry -> retryRegistration()
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun initRegistration(options: MakeCredentialOptions) {
        pendingOptions = options
        viewModelScope.launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            _state.value =
                RegistrationState.AwaitingUserConsent(
                    rpId = options.rp.id.value,
                    rpName = options.rp.name,
                    userName = options.user.name,
                    userDisplayName = options.user.displayName.ifEmpty { options.user.name },
                    availableMethod = availability.getBestAvailableMethod(),
                )

            // Fix F — Fallback auto-confirm for UV=NONE capability.
            // If the device has no biometric/PIN and UV is not strictly required,
            // we auto-confirm to skip the redundant "Create Passkey" button click.
            if (availability.getBestAvailableMethod() == VerificationMethod.NONE &&
                options.authenticatorSelection?.userVerification !=
                com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED
            ) {
                Logger.d { "Auto-confirming registration (UV capability: NONE)" }
                confirmRegistration()
            }
        }
    }

    private fun confirmRegistration() {
        val options =
            pendingOptions ?: run {
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
        pendingDeferred?.complete(Outcome.Error(DomainError.OperationCanceled("Cancelled by user")))
        uiEventBus.clearAll()
        pendingOptions = null
        pendingDeferred = null
        _state.value = RegistrationState.Cancelled
        viewModelScope.launch { emit(RegistrationEffect.NavigateBack) }
    }

    private fun retryRegistration() {
        val options =
            pendingOptions ?: run {
                _state.value = RegistrationState.Error("No pending registration request", false)
                return
            }
        initRegistration(options)
    }

    private fun performRegistration(options: MakeCredentialOptions) {
        _state.value = RegistrationState.Processing
        viewModelScope.launch {
            val result = fido2Service.makeCredential(options)

            if (result is Outcome.Success) {
                val makeResult = result.data
                makeResult.attestationObject
                val credential = makeResult.credential

                // Complete transport's deferred only on success with the attestation object
                pendingDeferred?.complete(Outcome.Success(makeResult))

                uiEventBus.clearAll()
                _state.value = RegistrationState.Success(credential)

                // Hold the success screen for a moment so the user can read it before
                // navigating away. The transport deferred is already resolved above.
                delay(SUCCESS_DISPLAY_DURATION)
                emit(RegistrationEffect.NavigateToSuccess(credential))

                // Clear pending only after success — on failure we keep them so Retry works
                pendingOptions = null
                pendingDeferred = null
            } else if (result is Outcome.Error) {
                val error = result.error
                // Complete the transport deferred with the failure so the PC gets a response
                pendingDeferred?.complete(result)
                pendingDeferred = null // deferred is consumed; pendingOptions kept for retry

                Logger.e(error.cause) { "Registration process failed: ${error.message}" }

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
        /** How long the success screen is shown before automatically dismissing. */
        private val SUCCESS_DISPLAY_DURATION = 2_000.milliseconds
    }
}
