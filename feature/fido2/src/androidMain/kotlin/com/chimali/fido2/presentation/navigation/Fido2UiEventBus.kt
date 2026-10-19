package com.chimali.fido2.presentation.navigation

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single

/**
 * Bridges low-level CTAP2 events (from transport) to the Compose UI layer.
 * Allows the transport to wait for user interaction results.
 */
@Single
class Fido2UiEventBus {
    // Fix C: Increased buffer from 1 → 4 so rapid CTAP2 retries from the host don't silently drop
    // events while the first deferred is still in-flight. Log dropped events for diagnosability.
    private val _events = MutableSharedFlow<Fido2UiEvent>(replay = 0, extraBufferCapacity = 4)
    val events: SharedFlow<Fido2UiEvent> = _events.asSharedFlow()

    private val registrationRequest = MutableStateFlow<Fido2UiEvent.RegistrationRequested?>(null)
    private val authenticationRequest = MutableStateFlow<Fido2UiEvent.AuthenticationRequested?>(null)

    /** Pending request available before its presenter subscribes; terminal requests are removed. */
    val currentRegistrationRequest: Fido2UiEvent.RegistrationRequested? get() = registrationRequest.value

    /** Pending assertion request, independent of the registration cache. */
    val currentAuthenticationRequest: Fido2UiEvent.AuthenticationRequested? get() = authenticationRequest.value

    /** Publishes live requests and binds each cached reference to its own completion. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun dispatch(event: Fido2UiEvent) {
        when (event) {
            is Fido2UiEvent.RegistrationRequested -> {
                if (!event.deferred.isActive) return
                registrationRequest.value = event
                event.deferred.invokeOnCompletion {
                    clearRegistrationRequest(event.deferred)
                    event.userInteractionCompleted.complete(Unit)
                }
                if (!event.deferred.isActive) return
            }
            is Fido2UiEvent.AuthenticationRequested -> {
                if (!event.deferred.isActive) return
                authenticationRequest.value = event
                event.deferred.invokeOnCompletion {
                    clearAuthenticationRequest(event.deferred)
                    event.userInteractionCompleted.complete(Unit)
                }
                if (!event.deferred.isActive) return
            }
        }
        if (!_events.tryEmit(event)) {
            co.touchlab.kermit.Logger
                .w { "[EventBus] Event dropped (buffer full): ${event::class.simpleName}" }
        }
    }

    /** Removes only the supplied request; obsolete cleanup cannot clear a successor. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun clearRegistrationRequest(response: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>?) {
        val current = registrationRequest.value ?: return
        if (current.deferred === response) registrationRequest.compareAndSet(current, null)
    }

    /** Removes only the supplied assertion request, preserving unrelated work. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun clearAuthenticationRequest(response: CompletableDeferred<Outcome<AssertionObject, DomainError>>?) {
        val current = authenticationRequest.value ?: return
        if (current.deferred === response) authenticationRequest.compareAndSet(current, null)
    }
}

sealed interface Fido2UiEvent {
    data class RegistrationRequested(
        val options: MakeCredentialOptions,
        val deferred: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>,
        val userInteractionCompleted: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : Fido2UiEvent

    data class AuthenticationRequested(
        val options: com.chimali.fido2.domain.model.GetAssertionOptions,
        val deferred: CompletableDeferred<Outcome<com.chimali.fido2.domain.model.AssertionObject, DomainError>>,
        val userInteractionCompleted: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : Fido2UiEvent
}
