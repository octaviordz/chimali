package com.chimali.fido2.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.service.Fido2TransportService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * T156a — ViewModel for the FIDO2 Authenticator Dashboard.
 * Manages the lifecycle of the foreground HID transport service and exposes its state.
 *
 * The actual Bluetooth work is delegated to [Fido2TransportService], a foreground
 * Service, so the HID connection survives the Activity going to background.
 */
@KoinViewModel
class Fido2HomeViewModel(
    private val context: Context,
    fido2Transport: com.chimali.fido2.data.transport.Fido2Transport,
    private val repository: com.chimali.fido2.domain.repository.PairedDeviceRepository,
    private val uiEventBus: Fido2UiEventBus,
    private val cryptoService: com.chimali.fido2.data.crypto.Fido2CryptoService,
) : ViewModel() {
    val connectionState: StateFlow<HidConnectionState> = fido2Transport.connectionState
    val uiEvents: SharedFlow<Fido2UiEvent> = uiEventBus.events

    init {
        viewModelScope.launch {
            cryptoService.warmUpMasterSeed()
        }
    }

    /**
     * Resolves the display name for the connected host, prioritizing user-defined aliases.
     */
    val connectedDeviceDisplayName: StateFlow<String?> =
        combine(
            connectionState,
            repository.getAllPairedDevices(),
        ) { state, devices ->
            when (state) {
                is HidConnectionState.Connected -> {
                    val match = devices.find { it.macAddress == state.device.address }
                    match?.alias ?: state.device.name ?: "Unknown PC"
                }
                is HidConnectionState.Connecting -> {
                    state.device.name ?: "Connecting..."
                }
                else -> null
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBED_STOP_TIMEOUT_MS),
            initialValue = null,
        )

    /**
     * Starts the foreground [Fido2TransportService] when idle/errored, or stops
     * it when connected/advertising. The service keeps the HID transport alive
     * even when the user navigates away from this screen.
     */
    fun toggleTransport() {
        val currentState = connectionState.value
        if (currentState is HidConnectionState.Idle || currentState is HidConnectionState.Error) {
            context.startForegroundService(Fido2TransportService.startIntent(context))
        } else {
            context.startService(Fido2TransportService.stopIntent(context))
        }
    }

    fun testRegistration(options: MakeCredentialOptions) {
        // Mock a registration request as if it came from Bluetooth
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, CompletableDeferred()))
    }

    fun getPendingRegistration(): Fido2UiEvent.RegistrationRequested? = uiEventBus.currentRegistrationRequest

    companion object {
        private const val SUBSCRIBED_STOP_TIMEOUT_MS = 5000L
    }
}
