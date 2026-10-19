package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.presentation.error.Fido2ErrorHandler
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    data class UserVerificationSuccess(
        val promptId: Long,
    ) : RegistrationIntent

    /** System verification failed. */
    data class UserVerificationFailed(
        val promptId: Long,
        val message: String,
    ) : RegistrationIntent

    /** Retry after an error. */
    data object Retry : RegistrationIntent

    /** ViewModel needs to be initialized with the incoming CTAP2 request. */
    data class InitRegistration(
        val options: MakeCredentialOptions,
    ) : RegistrationIntent
}

// ── MVI: State ────────────────────────────────────────────────────────────────

sealed interface RegistrationState {
    /** Initial; not yet received a CTAP2 MakeCredential request. */
    data object Idle : RegistrationState

    /** Awaiting user confirmation (shows the prompt). */
    data class AwaitingUserConsent(
        val rpId: SensitiveCredentialText,
        val rpName: SensitiveCredentialText,
        val userName: SensitiveCredentialText,
        val userDisplayName: SensitiveCredentialText,
        val availableMethod: VerificationMethod,
    ) : RegistrationState {
        /** Erases the presenter-owned RP and account text after the consent state ends. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSensitiveText() {
            rpId.erase()
            rpName.erase()
            userName.erase()
            userDisplayName.erase()
        }
    }

    /** User confirmed; awaiting system verification (Biometric/PIN). */
    data object AwaitingUserVerification : RegistrationState

    /** Performing cryptographic registration. */
    data object Processing : RegistrationState

    /** Registration completed successfully. */
    data class Success(
        val credential: PasskeyCredential,
    ) : RegistrationState

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
    data class LaunchSystemPrompt(
        val promptId: Long,
        val promptTitle: String,
        val promptSubtitle: String,
    ) : RegistrationEffect

    data class CancelSystemPrompt(
        val promptId: Long,
    ) : RegistrationEffect

    /** Carries only the identifier required by navigation, avoiding a credential-model owner in the effect queue. */
    data class NavigateToSuccess(
        val credentialId: String,
    ) : RegistrationEffect

    data object NavigateBack : RegistrationEffect

    data class ShowSnackbar(
        val message: String,
    ) : RegistrationEffect
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
    private val registerCredentialUseCase: RegisterCredentialUseCase,
    private val userVerificationService: UserVerificationService,
    private val uiEventBus: Fido2UiEventBus,
    private val masterSeedProvider: MasterSeedProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<RegistrationState>(RegistrationState.Idle)

    /** Current registration and user-verification state observed by the consent screen. */
    @Req("FR-HID-030", "FR-AUTH-010")
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    private val _effects = Channel<RegistrationEffect>(Channel.BUFFERED)

    /** One-shot UI effects for prompts, cancellation, navigation and feedback. */
    @Req("FR-HID-030", "FR-AUTH-010")
    val effects: Flow<RegistrationEffect> = _effects.receiveAsFlow()

    private var pendingOptions: MakeCredentialOptions? = null
    private var pendingDeferred: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>? = null
    private var pendingUserInteractionCompleted: CompletableDeferred<Unit>? = null
    private var ceremonyJob: Job? = null
    private var pendingForegroundJob: Job? = null
    private var pendingPromptAuthorized = false
    private var pendingPromptGeneration = 0L
    private var verificationPromptSequence = 0L
    private var activeVerificationPromptId: Long? = null
    private var responseCompletionOwned: AtomicBoolean? = null
    private var requestWalletGeneration = 0L
    private var requestGeneration = 0L
    private var pendingRequestJob: CompletableJob? = null

    init {
        Logger.d { "RegistrationPromptViewModel created — subscribing to event bus" }
        // Observe event bus for incoming registration requests from transport.
        // Guard: if we are already showing an error to the user, do NOT let a PC retry
        // silently overwrite the error screen — the user must dismiss/retry first.
        uiEventBus.events
            .filterIsInstance<Fido2UiEvent.RegistrationRequested>()
            .onEach { event ->
                Logger.d { "Registration request received" }
                // Guard: If we are already processing a ceremony or showing an error,
                // do NOT let a background retry overwrite our current state/deferred.
                if (_state.value !is RegistrationState.Idle) {
                    Logger.d {
                        "Ignoring incoming RegistrationRequested — current state: ${_state.value::class.simpleName}"
                    }
                    return@onEach
                }
                uiEventBus.clearRegistrationRequest(event.deferred)
                initRegistration(event.options, event.deferred, event.userInteractionCompleted)
            }.launchIn(viewModelScope)

        // Also consume any event stored before this ViewModel was created (replay backup).
        uiEventBus.currentRegistrationRequest?.let { event ->
            Logger.d { "Pending registration request restored" }
            initRegistration(event.options, event.deferred, event.userInteractionCompleted)
            uiEventBus.clearRegistrationRequest(event.deferred)
        } ?: Logger.d { "No currentRegistrationRequest in cache at init time" }
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    /** Applies a UI registration intent and advances or cancels the pending ceremony. */
    @Req("FR-HID-030", "FR-AUTH-010")
    fun handleIntent(intent: RegistrationIntent) {
        when (intent) {
            is RegistrationIntent.InitRegistration -> initRegistration(intent.options)
            is RegistrationIntent.ConfirmRegistration -> confirmRegistration()
            is RegistrationIntent.CancelRegistration -> cancelRegistration()
            is RegistrationIntent.VerifyUser -> startSystemVerification()
            is RegistrationIntent.UserVerificationSuccess -> {
                if (consumeVerificationResult(intent.promptId)) {
                    pendingOptions?.let { performRegistration(it, userVerificationPerformed = true) }
                }
            }
            is RegistrationIntent.UserVerificationFailed -> {
                if (!consumeVerificationResult(intent.promptId)) return
                finishUserInteraction()
                val response = pendingDeferred
                pendingDeferred = null
                pendingUserInteractionCompleted = null
                pendingOptions = null
                finishPendingForegroundPrompt(authorized = false)
                if (!completeResponse(
                        response,
                        Outcome.Error(DomainError.OperationDenied("User verification failed")),
                    )
                ) {
                    return
                }
                pendingRequestJob?.complete()
                pendingRequestJob = null
                setState(RegistrationState.Error(intent.message))
                viewModelScope.launch { emit(RegistrationEffect.ShowSnackbar(intent.message)) }
            }
            is RegistrationIntent.Retry -> retryRegistration()
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun initRegistration(
        options: MakeCredentialOptions,
        response: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>? = null,
        userInteraction: CompletableDeferred<Unit>? = null,
    ) {
        val previousResponse = pendingDeferred
        val previousJob = pendingRequestJob
        finishPendingForegroundPrompt(authorized = false)
        ceremonyJob = null
        requestGeneration += 1
        val generation = requestGeneration
        val request = Job(viewModelScope.coroutineContext[Job])
        pendingRequestJob = request
        requestWalletGeneration = masterSeedProvider.sessionGeneration()
        pendingOptions = options
        pendingDeferred = response
        pendingUserInteractionCompleted = userInteraction
        setState(RegistrationState.Idle)
        previousJob?.cancel(CancellationException("Registration request was replaced"))
        if (previousResponse !== response) previousResponse?.cancel()
        val completionOwned = AtomicBoolean(false)
        responseCompletionOwned = completionOwned
        response?.invokeOnCompletion { cause ->
            if (cause != null || !completionOwned.get()) {
                request.cancel(CancellationException("Registration response ended", cause))
                viewModelScope.launch { terminateRequest(generation, request) }
            }
        }
        if (!isLiveRequest(generation)) return
        startPendingForegroundPrompt()
        CoroutineScope(viewModelScope.coroutineContext + request).launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (!isLiveRequest(generation)) return@launch
            setState(
                RegistrationState.AwaitingUserConsent(
                    rpId = options.rp.copyIdOwner(),
                    rpName = options.rp.copyNameOwner(),
                    userName = options.user.copyNameOwner(),
                    userDisplayName = options.user.copyDisplayNameOwner(),
                    availableMethod = availability.getBestAvailableMethod(),
                ),
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
        if (_state.value !is RegistrationState.AwaitingUserConsent) return
        val generation = requestGeneration
        val request = pendingRequestJob ?: return
        if (!isLiveRequest(generation)) return
        val options =
            pendingOptions ?: run {
                setState(RegistrationState.Error("No pending registration request", false))
                return
            }
        CoroutineScope(viewModelScope.coroutineContext + request).launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (!isLiveRequest(generation) || pendingOptions !== options) return@launch

            if (availability.getBestAvailableMethod() == VerificationMethod.NONE) {
                // No UV required / available — proceed without verification
                performRegistration(options, userVerificationPerformed = false)
            } else {
                startSystemVerification()
            }
        }
    }

    private fun startSystemVerification() {
        val options = pendingOptions ?: return
        if (_state.value !is RegistrationState.AwaitingUserConsent) return
        if (activeVerificationPromptId != null || _state.value == RegistrationState.Processing) return
        val promptId = ++verificationPromptSequence
        activeVerificationPromptId = promptId
        setState(RegistrationState.AwaitingUserVerification)
        viewModelScope.launch {
            val promptTitle = "Create Passkey"
            val promptSubtitle = options.rp.name
            if (activeVerificationPromptId == promptId &&
                _state.value == RegistrationState.AwaitingUserVerification
            ) {
                emit(RegistrationEffect.LaunchSystemPrompt(promptId, promptTitle, promptSubtitle))
            }
        }
    }

    private fun consumeVerificationResult(promptId: Long): Boolean {
        if (!isLiveRequest(requestGeneration) ||
            activeVerificationPromptId != promptId ||
            _state.value != RegistrationState.AwaitingUserVerification
        ) {
            return false
        }
        activeVerificationPromptId = null
        return true
    }

    private fun cancelRegistration() {
        val request = pendingRequestJob
        pendingRequestJob = null
        requestGeneration += 1
        finishUserInteraction()
        ceremonyJob?.cancel(CancellationException("Registration was cancelled by the user"))
        ceremonyJob = null
        finishPendingForegroundPrompt(authorized = false)
        completeResponse(pendingDeferred, Outcome.Error(DomainError.OperationCanceled("Cancelled by user")))
        uiEventBus.clearRegistrationRequest(pendingDeferred)
        pendingOptions = null
        pendingDeferred = null
        setState(RegistrationState.Cancelled)
        request?.cancel(CancellationException("Registration was cancelled by the user"))
        viewModelScope.launch { emit(RegistrationEffect.NavigateBack) }
    }

    /** A terminal CTAP request can only be retried through a newly admitted transport request. */
    private fun retryRegistration() {
        if (isLiveRequest(requestGeneration)) return
        val next = uiEventBus.currentRegistrationRequest
        if (next != null && next.deferred.isActive) {
            uiEventBus.clearRegistrationRequest(next.deferred)
            initRegistration(next.options, next.deferred, next.userInteractionCompleted)
        } else {
            pendingOptions = null
            setState(RegistrationState.Error("Start a new request to try again", false))
        }
    }

    private fun performRegistration(
        options: MakeCredentialOptions,
        userVerificationPerformed: Boolean,
    ) {
        val generation = requestGeneration
        val admittedWalletGeneration = requestWalletGeneration
        val request = pendingRequestJob ?: return
        if (!isLiveRequest(generation)) return
        finishUserInteraction()
        finishPendingForegroundPrompt(authorized = true)
        setState(RegistrationState.Processing)
        val responseDeferred = pendingDeferred
        val operation =
            CoroutineScope(viewModelScope.coroutineContext + request).launch(start = CoroutineStart.LAZY) {
                currentCoroutineContext().ensureActive()
                if (!isLiveRequest(generation)) return@launch
                requireCurrentWallet(admittedWalletGeneration)
                val result =
                    masterSeedProvider.withSessionOwner(
                        ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                    ) { ownerGeneration ->
                        currentCoroutineContext().ensureActive()
                        requireCurrentWallet(admittedWalletGeneration, ownerGeneration)
                        if (!isLiveRequest(generation)) throw CancellationException("Registration request ended")
                        registerCredentialUseCase(options, userVerificationPerformed)
                    }
                if (!isLiveRequest(generation)) {
                    (result as? Outcome.Success)?.data?.eraseSensitiveText()
                    return@launch
                }

                if (result is Outcome.Success) {
                    val makeResult = result.data
                    val credential = makeResult.credential
                    val uiCredential = credential.copyWithIndependentSensitiveText()

                    // Complete transport's deferred only on success with the attestation object
                    if (!completeResponse(
                            responseDeferred,
                            Outcome.Success(makeResult),
                        )
                    ) {
                        makeResult.eraseSensitiveText()
                        uiCredential.eraseSensitiveText()
                        return@launch
                    }

                    uiEventBus.clearRegistrationRequest(responseDeferred)
                    setState(RegistrationState.Success(uiCredential))

                    // Hold the success screen for a moment so the user can read it before
                    // navigating away. The transport deferred is already resolved above.
                    delay(SUCCESS_DISPLAY_DURATION)
                    emit(RegistrationEffect.NavigateToSuccess(uiCredential.id.encoded))

                    // Release request data after the terminal result is shown.
                    pendingOptions = null
                    pendingDeferred = null
                    pendingUserInteractionCompleted = null
                } else if (result is Outcome.Error) {
                    val error = result.error
                    // Complete the transport deferred with the failure so the PC gets a response
                    if (!completeResponse(
                            responseDeferred,
                            result,
                        )
                    ) {
                        return@launch
                    }
                    pendingDeferred = null
                    pendingOptions = null
                    pendingUserInteractionCompleted = null

                    Logger.e { "Registration process failed" }

                    // T149 / T152 — delegate error classification to Fido2ErrorHandler
                    val ui = Fido2ErrorHandler.handle(error)
                    setState(RegistrationState.Error(ui.message, ui.isRetryable))
                }
            }
        ceremonyJob = operation
        operation.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                responseDeferred?.cancel(cause)
                request.cancel(cause)
                viewModelScope.launch { terminateRequest(generation, request) }
            } else {
                request.complete()
            }
        }
        operation.start()
    }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun onCleared() {
        pendingRequestJob?.cancel()
        pendingRequestJob = null
        finishUserInteraction()
        ceremonyJob?.cancel()
        ceremonyJob = null
        finishPendingForegroundPrompt(authorized = false)
        eraseStateSensitiveText(_state.value)
        super.onCleared()
    }

    /** Releasing a success state ends the ViewModel-owned credential-text lifetime. */
    private fun setState(next: RegistrationState) {
        if (_state.value !== next) eraseStateSensitiveText(_state.value)
        _state.value = next
    }

    private fun eraseStateSensitiveText(state: RegistrationState) {
        when (state) {
            is RegistrationState.AwaitingUserConsent -> state.eraseSensitiveText()
            is RegistrationState.Success -> state.credential.eraseSensitiveText()
            RegistrationState.AwaitingUserVerification,
            RegistrationState.Cancelled,
            RegistrationState.Idle,
            RegistrationState.Processing,
            is RegistrationState.Error,
            -> Unit
        }
    }

    /** Foreground-bound prompt ownership ends before the separate authorized CTAP operation starts. */

    private fun startPendingForegroundPrompt() {
        cancelActiveSystemPrompt()
        pendingPromptGeneration += 1
        val promptGeneration = pendingPromptGeneration
        pendingPromptAuthorized = false
        pendingForegroundJob?.cancel(CancellationException("Registration prompt was replaced"))
        pendingForegroundJob = null
        if (pendingDeferred == null) return
        pendingForegroundJob =
            CoroutineScope(viewModelScope.coroutineContext + requireNotNull(pendingRequestJob)).launch {
                try {
                    masterSeedProvider.withSessionOwner(
                        initialize = false,
                        ownerKind = MasterSeedOwnerKind.FOREGROUND,
                    ) {
                        awaitCancellation()
                    }
                } catch (cancellation: CancellationException) {
                    if (promptGeneration != pendingPromptGeneration || pendingPromptAuthorized) {
                        throw cancellation
                    }
                    cancelActiveSystemPrompt()
                    pendingRequestJob?.cancel(cancellation)
                    finishUserInteraction()
                    val response = pendingDeferred
                    pendingDeferred = null
                    pendingUserInteractionCompleted = null
                    pendingOptions = null
                    uiEventBus.clearRegistrationRequest(response)
                    completeResponse(
                        response,
                        Outcome.Error(DomainError.OperationCanceled("Registration prompt left the foreground")),
                    )
                    setState(RegistrationState.Cancelled)
                }
            }
    }

    private fun finishPendingForegroundPrompt(authorized: Boolean) {
        cancelActiveSystemPrompt()
        pendingPromptGeneration += 1
        pendingPromptAuthorized = authorized
        pendingForegroundJob?.cancel(CancellationException("Registration prompt ownership ended"))
        pendingForegroundJob = null
    }

    private fun cancelActiveSystemPrompt() {
        activeVerificationPromptId?.let { _effects.trySend(RegistrationEffect.CancelSystemPrompt(it)) }
        activeVerificationPromptId = null
    }

    /** Checks both the admission generation and the generation bound to the authorized owner. */
    private fun requireCurrentWallet(
        expected: Long,
        ownerGeneration: Long = expected,
    ) {
        if (ownerGeneration != expected || !masterSeedProvider.isSessionGenerationCurrent(expected)) {
            throw CancellationException("Wallet session ended before request authorization")
        }
    }

    private fun isLiveRequest(generation: Long): Boolean =
        generation == requestGeneration && pendingRequestJob?.isActive == true && pendingDeferred?.isActive != false

    private fun terminateRequest(
        generation: Long,
        request: CompletableJob,
    ) {
        if (generation != requestGeneration || pendingRequestJob !== request) return
        val response = pendingDeferred
        pendingRequestJob = null
        finishUserInteraction()
        finishPendingForegroundPrompt(authorized = false)
        ceremonyJob = null
        pendingOptions = null
        pendingDeferred = null
        pendingUserInteractionCompleted = null
        uiEventBus.clearRegistrationRequest(response)
        setState(RegistrationState.Cancelled)
        _effects.trySend(RegistrationEffect.NavigateBack)
    }

    /** Marks presenter-owned completion before synchronous deferred listeners can run. */
    private fun completeResponse(
        response: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>?,
        result: Outcome<MakeCredentialResult, DomainError>,
    ): Boolean {
        responseCompletionOwned?.set(true)
        val completed = response?.complete(result) ?: true
        if (!completed) {
            pendingRequestJob?.let { request ->
                request.cancel(CancellationException("Registration response already ended"))
                terminateRequest(requestGeneration, request)
            }
        }
        return completed
    }

    private fun finishUserInteraction() {
        pendingUserInteractionCompleted?.complete(Unit)
    }

    private suspend fun emit(effect: RegistrationEffect) {
        _effects.send(effect)
    }

    companion object {
        /** How long the success screen is shown before automatically dismissing. */
        private val SUCCESS_DISPLAY_DURATION = 2_000.milliseconds
    }
}
