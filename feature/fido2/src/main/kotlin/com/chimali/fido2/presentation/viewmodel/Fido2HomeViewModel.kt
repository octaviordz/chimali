package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T156a — ViewModel for the FIDO2 Authenticator Dashboard.
 * Manages the lifecycle of the HID transport and exposes its state.
 */
@HiltViewModel
class Fido2HomeViewModel @Inject constructor(
    private val fido2Transport: Fido2Transport,
    private val uiEventBus: Fido2UiEventBus
) : ViewModel() {

    val connectionState: StateFlow<HidConnectionState> = fido2Transport.connectionState
    val uiEvents: SharedFlow<Fido2UiEvent> = uiEventBus.events

    fun toggleTransport() {
        viewModelScope.launch {
            val currentState = connectionState.value
            if (currentState is HidConnectionState.Idle || currentState is HidConnectionState.Error) {
                fido2Transport.connect()
            } else {
                fido2Transport.disconnect()
            }
        }
    }

    fun testRegistration(options: MakeCredentialOptions) {
        // Mock a registration request as if it came from Bluetooth
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, CompletableDeferred()))
    }
}
