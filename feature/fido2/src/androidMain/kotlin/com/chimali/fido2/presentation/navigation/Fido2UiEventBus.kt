package com.chimali.fido2.presentation.navigation

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
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

    var currentRegistrationRequest: Fido2UiEvent.RegistrationRequested? = null
    var currentAuthenticationRequest: Fido2UiEvent.AuthenticationRequested? = null

    fun dispatch(event: Fido2UiEvent) {
        when (event) {
            is Fido2UiEvent.RegistrationRequested -> currentRegistrationRequest = event
            is Fido2UiEvent.AuthenticationRequested -> currentAuthenticationRequest = event
        }
        if (!_events.tryEmit(event)) {
            co.touchlab.kermit.Logger.w { "[EventBus] Event dropped (buffer full): ${event::class.simpleName}" }
        }
    }

    fun clearRegistrationRequest() {
        currentRegistrationRequest = null
    }

    fun clearAuthenticationRequest() {
        currentAuthenticationRequest = null
    }

    /** Atomically clears both caches. Call after any ceremony is fully complete. */
    fun clearAll() {
        currentRegistrationRequest = null
        currentAuthenticationRequest = null
    }
}

sealed interface Fido2UiEvent {
    data class RegistrationRequested(
        val options: MakeCredentialOptions,
        val deferred: CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>,
    ) : Fido2UiEvent

    data class AuthenticationRequested(
        val options: com.chimali.fido2.domain.model.GetAssertionOptions,
        val deferred: CompletableDeferred<Outcome<com.chimali.fido2.domain.model.AssertionObject, DomainError>>,
    ) : Fido2UiEvent
}
