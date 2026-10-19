package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.error.Fido2ErrorHandler
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
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

// ── MVI: Intent ───────────────────────────────────────────────────────────────

sealed interface AuthenticationIntent {
    data class InitAuthentication(
        val options: GetAssertionOptions,
    ) : AuthenticationIntent

    data object ConfirmAuthentication : AuthenticationIntent

    data object CancelAuthentication : AuthenticationIntent

    data object VerifyUser : AuthenticationIntent

    data class UserVerificationSuccess(
        val promptId: Long,
    ) : AuthenticationIntent

    data class UserVerificationFailed(
        val promptId: Long,
        val message: String,
    ) : AuthenticationIntent

    data class SelectCredential(
        val credential: PasskeyCredential,
    ) : AuthenticationIntent

    data object Retry : AuthenticationIntent
}

// ── MVI: State ────────────────────────────────────────────────────────────────

sealed interface AuthenticationState {
    data object Idle : AuthenticationState

    data class AwaitingUserConsent(
        val rpId: SensitiveCredentialText,
        val rpName: SensitiveCredentialText,
        val availableMethod: VerificationMethod,
        val credentialCount: Int,
    ) : AuthenticationState {
        /** Erases the presenter-owned RP text after the consent state ends. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSensitiveText() {
            rpId.erase()
            rpName.erase()
        }
    }

    data class SelectingCredential(
        val rpId: SensitiveCredentialText,
        val credentials: List<PasskeyCredential>,
    ) : AuthenticationState

    data object AwaitingUserVerification : AuthenticationState

    data object Processing : AuthenticationState

    data class Success(
        val assertion: AssertionObject,
    ) : AuthenticationState

    data class Error(
        val message: String,
        val isRetryable: Boolean = true,
    ) : AuthenticationState

    data object Cancelled : AuthenticationState
}

// ── MVI: Effects ─────────────────────────────────────────────────────────────

sealed interface AuthenticationEffect {
    data class LaunchSystemPrompt(
        val promptId: Long,
        val promptTitle: String,
        val promptSubtitle: String,
    ) : AuthenticationEffect

    data class CancelSystemPrompt(
        val promptId: Long,
    ) : AuthenticationEffect

    data class NavigateToSuccess(
        val assertion: AssertionObject,
    ) : AuthenticationEffect

    data object NavigateBack : AuthenticationEffect

    data class ShowSnackbar(
        val message: String,
    ) : AuthenticationEffect
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
    private val uiEventBus: Fido2UiEventBus,
    private val masterSeedProvider: MasterSeedProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthenticationState>(AuthenticationState.Idle)
    val state: StateFlow<AuthenticationState> = _state.asStateFlow()

    private val _effects = Channel<AuthenticationEffect>(Channel.BUFFERED)
    val effects: Flow<AuthenticationEffect> = _effects.receiveAsFlow()

    private var pendingOptions: GetAssertionOptions? = null
    private var ownsPendingOptions = false
    private var pendingDeferred: kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>? = null
    private var pendingUserInteractionCompleted: kotlinx.coroutines.CompletableDeferred<Unit>? = null
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
        uiEventBus.events
            .filterIsInstance<Fido2UiEvent.AuthenticationRequested>()
            .onEach { event ->
                // Guard: If we are already processing a ceremony or showing an error,
                // do NOT let a background retry overwrite our current state/deferred.
                if (_state.value !is AuthenticationState.Idle) {
                    Logger.d {
                        "Ignoring incoming AuthenticationRequested — current state: ${_state.value::class.simpleName}"
                    }
                    return@onEach
                }
                uiEventBus.clearAuthenticationRequest(event.deferred)
                initAuthentication(event.options, event.deferred, event.userInteractionCompleted)
            }.launchIn(viewModelScope)

        uiEventBus.currentAuthenticationRequest?.let { event ->
            initAuthentication(event.options, event.deferred, event.userInteractionCompleted)
            uiEventBus.clearAuthenticationRequest(event.deferred)
        }
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun handleIntent(intent: AuthenticationIntent) {
        when (intent) {
            is AuthenticationIntent.InitAuthentication -> initAuthentication(intent.options)
            is AuthenticationIntent.ConfirmAuthentication -> confirmAuthentication()
            is AuthenticationIntent.CancelAuthentication -> cancel()
            is AuthenticationIntent.VerifyUser -> startSystemVerification()
            is AuthenticationIntent.UserVerificationSuccess -> {
                if (consumeVerificationResult(intent.promptId)) {
                    pendingOptions?.let { performAuthentication(it, userVerificationPerformed = true) }
                }
            }
            is AuthenticationIntent.UserVerificationFailed -> {
                if (!consumeVerificationResult(intent.promptId)) return
                finishUserInteraction()
                val response = pendingDeferred
                pendingDeferred = null
                pendingUserInteractionCompleted = null
                clearOwnedPendingOptions()
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
                setState(AuthenticationState.Error(intent.message))
                viewModelScope.launch { emit(AuthenticationEffect.ShowSnackbar(intent.message)) }
            }
            is AuthenticationIntent.SelectCredential -> onCredentialSelected(intent.credential)
            is AuthenticationIntent.Retry -> retry()
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun initAuthentication(
        options: GetAssertionOptions,
        response: kotlinx.coroutines.CompletableDeferred<Outcome<AssertionObject, DomainError>>? = null,
        userInteraction: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
    ) {
        val previousResponse = pendingDeferred
        val previousJob = pendingRequestJob
        clearOwnedPendingOptions()
        finishPendingForegroundPrompt(authorized = false)
        ceremonyJob = null
        requestGeneration += 1
        val generation = requestGeneration
        val request = Job(viewModelScope.coroutineContext[Job])
        pendingRequestJob = request
        requestWalletGeneration = masterSeedProvider.sessionGeneration()
        pendingOptions = options
        ownsPendingOptions = false
        pendingDeferred = response
        pendingUserInteractionCompleted = userInteraction
        setState(AuthenticationState.Idle)
        previousJob?.cancel(CancellationException("Authentication request was replaced"))
        if (previousResponse !== response) previousResponse?.cancel()
        val completionOwned = AtomicBoolean(false)
        responseCompletionOwned = completionOwned
        response?.invokeOnCompletion { cause ->
            if (cause != null || !completionOwned.get()) {
                request.cancel(CancellationException("Authentication response ended", cause))
                viewModelScope.launch { terminateRequest(generation, request) }
            }
        }
        if (!isLiveRequest(generation)) return
        startPendingForegroundPrompt()
        CoroutineScope(viewModelScope.coroutineContext + request).launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (!isLiveRequest(generation)) return@launch
            setState(
                AuthenticationState.AwaitingUserConsent(
                    rpId = options.copyRpIdOwner(),
                    // RP name resolved from repo in a future pass
                    rpName = options.copyRpIdOwner(),
                    availableMethod = availability.getBestAvailableMethod(),
                    credentialCount = options.allowCredentials?.size ?: 0,
                ),
            )

            // Fix F — Fallback auto-confirm for UV=NONE capability.
            // If the device has no biometric/PIN and UV is not strictly required,
            // we auto-confirm to skip the redundant "Sign in" button click.
            // Note: This only fires if we have 0 (discoverable) or 1 specific credential.
            // If multiple credentials exist, we must still show SelectingCredential.
            if (availability.getBestAvailableMethod() == VerificationMethod.NONE &&
                options.userVerification != com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED &&
                (options.allowCredentials?.size ?: 0) <= 1
            ) {
                Logger.d { "Auto-confirming authentication (UV capability: NONE)" }
                confirmAuthentication()
            }
        }
    }

    private fun confirmAuthentication() {
        if (_state.value !is AuthenticationState.AwaitingUserConsent &&
            _state.value !is AuthenticationState.SelectingCredential
        ) {
            return
        }
        val generation = requestGeneration
        val request = pendingRequestJob ?: return
        if (!isLiveRequest(generation)) return
        val options =
            pendingOptions ?: run {
                setState(AuthenticationState.Error("No pending authentication request", false))
                return
            }
        CoroutineScope(viewModelScope.coroutineContext + request).launch {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (!isLiveRequest(generation) || pendingOptions !== options) return@launch

            if (availability.getBestAvailableMethod() == VerificationMethod.NONE) {
                performAuthentication(options)
            } else {
                startSystemVerification()
            }
        }
    }

    private fun startSystemVerification() {
        val options = pendingOptions ?: return
        if (_state.value !is AuthenticationState.AwaitingUserConsent &&
            _state.value !is AuthenticationState.SelectingCredential
        ) {
            return
        }
        if (activeVerificationPromptId != null || _state.value == AuthenticationState.Processing) return
        val promptId = ++verificationPromptSequence
        activeVerificationPromptId = promptId
        setState(AuthenticationState.AwaitingUserVerification)
        viewModelScope.launch {
            val promptTitle = "Sign in"
            val promptSubtitle = options.rpId.value
            if (activeVerificationPromptId == promptId &&
                _state.value == AuthenticationState.AwaitingUserVerification
            ) {
                emit(AuthenticationEffect.LaunchSystemPrompt(promptId, promptTitle, promptSubtitle))
            }
        }
    }

    private fun consumeVerificationResult(promptId: Long): Boolean {
        if (!isLiveRequest(requestGeneration) ||
            activeVerificationPromptId != promptId ||
            _state.value != AuthenticationState.AwaitingUserVerification
        ) {
            return false
        }
        activeVerificationPromptId = null
        return true
    }

    private fun onCredentialSelected(credential: PasskeyCredential) {
        // Re-run with updated allow-list that only contains selected credential
        val options = pendingOptions ?: return
        val filtered =
            listOf(
                com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
                    .create(id = credential.id),
            )
        val filteredOptions = options.copy(allowCredentials = filtered)
        clearOwnedPendingOptions()
        pendingOptions = filteredOptions
        ownsPendingOptions = true
        confirmAuthentication()
    }

    private fun cancel() {
        val request = pendingRequestJob
        pendingRequestJob = null
        requestGeneration += 1
        finishUserInteraction()
        ceremonyJob?.cancel(CancellationException("Authentication was cancelled by the user"))
        ceremonyJob = null
        finishPendingForegroundPrompt(authorized = false)
        completeResponse(pendingDeferred, Outcome.Error(DomainError.OperationCanceled("Cancelled by user")))
        uiEventBus.clearAuthenticationRequest(pendingDeferred)
        clearOwnedPendingOptions()
        pendingDeferred = null
        setState(AuthenticationState.Cancelled)
        request?.cancel(CancellationException("Authentication was cancelled by the user"))
        viewModelScope.launch { emit(AuthenticationEffect.NavigateBack) }
    }

    /** A terminal CTAP request can only be retried through a newly admitted transport request. */
    private fun retry() {
        if (isLiveRequest(requestGeneration)) return
        val next = uiEventBus.currentAuthenticationRequest
        if (next != null && next.deferred.isActive) {
            uiEventBus.clearAuthenticationRequest(next.deferred)
            initAuthentication(next.options, next.deferred, next.userInteractionCompleted)
        } else {
            clearOwnedPendingOptions()
            setState(AuthenticationState.Error("Start a new request to try again", false))
        }
    }

    private fun performAuthentication(
        options: GetAssertionOptions,
        userVerificationPerformed: Boolean = false,
    ) {
        val generation = requestGeneration
        val admittedWalletGeneration = requestWalletGeneration
        val request = pendingRequestJob ?: return
        if (!isLiveRequest(generation)) return
        finishUserInteraction()
        finishPendingForegroundPrompt(authorized = true)
        setState(AuthenticationState.Processing)
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
                        if (!isLiveRequest(generation)) throw CancellationException("Authentication request ended")
                        getAssertionUseCase(options, userVerificationPerformed)
                    }
                if (!isLiveRequest(generation)) return@launch
                when (result) {
                    is Outcome.Success -> {
                        val assertion = result.data
                        if (!completeResponse(
                                responseDeferred,
                                Outcome.Success(assertion),
                            )
                        ) {
                            return@launch
                        }
                        uiEventBus.clearAuthenticationRequest(responseDeferred)
                        setState(AuthenticationState.Success(assertion))
                        emit(AuthenticationEffect.NavigateToSuccess(assertion))
                        clearOwnedPendingOptions()
                        pendingDeferred = null
                        pendingUserInteractionCompleted = null
                    }
                    is Outcome.Error -> {
                        // Fix B — Null deferred + navigate back on error so the retry storm stops.
                        // Previously: pendingDeferred was completed but NOT nulled, and NavigateBack
                        // was never emitted.  The screen stayed open, the CTAP2 host retried,
                        // the next AuthenticationRequested overwrote pendingDeferred, and a new
                        // biometric prompt appeared.  Repeat until auto-select fired on attempt 5+.
                        val error = result.error
                        if (!completeResponse(
                                responseDeferred,
                                result,
                            )
                        ) {
                            return@launch
                        }
                        pendingDeferred = null
                        pendingUserInteractionCompleted = null
                        clearOwnedPendingOptions()
                        Logger.e { "Authentication process failed" }
                        val ui = Fido2ErrorHandler.handle(error)
                        setState(AuthenticationState.Error(ui.message, ui.isRetryable))
                        viewModelScope.launch { emit(AuthenticationEffect.NavigateBack) }
                    }
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
        clearOwnedPendingOptions()
        finishUserInteraction()
        ceremonyJob?.cancel()
        ceremonyJob = null
        finishPendingForegroundPrompt(authorized = false)
        eraseStateSensitiveText(_state.value)
        super.onCleared()
    }

    /** Foreground-bound verification closes before the separate authorized assertion operation starts. */

    private fun startPendingForegroundPrompt() {
        cancelActiveSystemPrompt()
        pendingPromptGeneration += 1
        val promptGeneration = pendingPromptGeneration
        pendingPromptAuthorized = false
        pendingForegroundJob?.cancel(CancellationException("Authentication prompt was replaced"))
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
                    clearOwnedPendingOptions()
                    uiEventBus.clearAuthenticationRequest(response)
                    completeResponse(
                        response,
                        Outcome.Error(DomainError.OperationCanceled("Authentication prompt left the foreground")),
                    )
                    setState(AuthenticationState.Cancelled)
                }
            }
    }

    private fun finishPendingForegroundPrompt(authorized: Boolean) {
        cancelActiveSystemPrompt()
        pendingPromptGeneration += 1
        pendingPromptAuthorized = authorized
        pendingForegroundJob?.cancel(CancellationException("Authentication prompt ownership ended"))
        pendingForegroundJob = null
    }

    private fun cancelActiveSystemPrompt() {
        activeVerificationPromptId?.let { _effects.trySend(AuthenticationEffect.CancelSystemPrompt(it)) }
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
        clearOwnedPendingOptions()
        pendingDeferred = null
        pendingUserInteractionCompleted = null
        uiEventBus.clearAuthenticationRequest(response)
        setState(AuthenticationState.Cancelled)
        _effects.trySend(AuthenticationEffect.NavigateBack)
    }

    /** Marks presenter-owned completion before synchronous deferred listeners can run. */
    private fun completeResponse(
        response: CompletableDeferred<Outcome<AssertionObject, DomainError>>?,
        result: Outcome<AssertionObject, DomainError>,
    ): Boolean {
        responseCompletionOwned?.set(true)
        val completed = response?.complete(result) ?: true
        if (!completed) {
            pendingRequestJob?.let { request ->
                request.cancel(CancellationException("Authentication response already ended"))
                terminateRequest(requestGeneration, request)
            }
        }
        return completed
    }

    private fun finishUserInteraction() {
        pendingUserInteractionCompleted?.complete(Unit)
    }

    /** Erases a presenter-created options copy while leaving the CTAP-owned source request untouched. */
    private fun clearOwnedPendingOptions() {
        if (ownsPendingOptions) pendingOptions?.eraseSensitiveText()
        pendingOptions = null
        ownsPendingOptions = false
    }

    private fun setState(next: AuthenticationState) {
        if (_state.value !== next) eraseStateSensitiveText(_state.value)
        _state.value = next
    }

    private fun eraseStateSensitiveText(state: AuthenticationState) {
        when (state) {
            is AuthenticationState.AwaitingUserConsent -> state.eraseSensitiveText()
            is AuthenticationState.SelectingCredential -> state.rpId.erase()
            AuthenticationState.AwaitingUserVerification,
            AuthenticationState.Cancelled,
            AuthenticationState.Idle,
            AuthenticationState.Processing,
            is AuthenticationState.Error,
            is AuthenticationState.Success,
            -> Unit
        }
    }

    private suspend fun emit(effect: AuthenticationEffect) = _effects.send(effect)
}
