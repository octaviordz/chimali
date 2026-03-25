package com.chimali.fido2.presentation.navigation

import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges low-level CTAP2 events (from transport) to the Compose UI layer.
 * Allows the transport to wait for user interaction results.
 */
@Singleton
class Fido2UiEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<Fido2UiEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<Fido2UiEvent> = _events.asSharedFlow()

    var currentRegistrationRequest: Fido2UiEvent.RegistrationRequested? = null
    var currentAuthenticationRequest: Fido2UiEvent.AuthenticationRequested? = null

    fun dispatch(event: Fido2UiEvent) {
        when (event) {
            is Fido2UiEvent.RegistrationRequested -> currentRegistrationRequest = event
            is Fido2UiEvent.AuthenticationRequested -> currentAuthenticationRequest = event
        }
        _events.tryEmit(event)
    }

    fun clearRegistrationRequest() {
        currentRegistrationRequest = null
    }

    fun clearAuthenticationRequest() {
        currentAuthenticationRequest = null
    }
}

sealed interface Fido2UiEvent {
    data class RegistrationRequested(
        val options: MakeCredentialOptions,
        val deferred: CompletableDeferred<Result<MakeCredentialResult>>
    ) : Fido2UiEvent

    data class AuthenticationRequested(
        val rpId: String,
        val deferred: CompletableDeferred<Result<String>> // Returning credential ID or error
    ) : Fido2UiEvent
}
