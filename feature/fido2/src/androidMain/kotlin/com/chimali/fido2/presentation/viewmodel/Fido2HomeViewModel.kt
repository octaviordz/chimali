package com.chimali.fido2.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.data.transport.ProcessTransportSelection
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.service.Fido2TransportService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val fido2Transport: Fido2Transport,
    private val processTransportSelection: ProcessTransportSelection,
    private val repository: com.chimali.fido2.domain.repository.PairedDeviceRepository,
    private val uiEventBus: Fido2UiEventBus,
) : ViewModel() {
    val connectionState: StateFlow<Fido2ConnectionState> = fido2Transport.connectionState
    val processTransportState = processTransportSelection.state
    val uiEvents: SharedFlow<Fido2UiEvent> = uiEventBus.events
    val pendingRegistrationRequest = uiEventBus.pendingRegistrationRequest
    val pendingAuthenticationRequest = uiEventBus.pendingAuthenticationRequest
    private val _outboundBleUnsupported = MutableStateFlow(false)
    val outboundBleUnsupported = _outboundBleUnsupported.asStateFlow()

    /**
     * Resolves the display name for the connected host, prioritizing user-defined aliases.
     */
    val connectedDeviceDisplayName: StateFlow<String?> =
        combine(
            connectionState,
            repository.getAllPairedDevices(),
        ) { state, devices ->
            when (state) {
                is Fido2ConnectionState.Connected -> {
                    val address = state.device?.address
                    val match = address?.let { targetAddress -> devices.find { it.macAddress == targetAddress } }
                    match?.alias ?: state.device?.name ?: "Connected Host"
                }

                is Fido2ConnectionState.Connecting -> {
                    state.device?.name ?: "Connecting..."
                }

                else -> {
                    null
                }
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

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toggleTransport() {
        when (transportToggleAction(connectionState.value, processTransportState.value.availability)) {
            TransportToggleAction.Start -> context.startForegroundService(Fido2TransportService.startIntent(context))
            TransportToggleAction.Retry -> retryTransport()
            TransportToggleAction.Stop -> context.startService(Fido2TransportService.stopIntent(context))
        }
    }

    /**
     * Proactively initiates an outbound Bluetooth HID connection to a paired host.
     * Ensures the transport service is running first if currently idle.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun connectDevice(macAddress: String) {
        if (processTransportSelection.state.value.processChoice == BluetoothTransportMode.FIDO_BLE) {
            _outboundBleUnsupported.value = true
            return
        }
        val currentState = connectionState.value
        viewModelScope.launch {
            if (currentState is Fido2ConnectionState.Idle || currentState is Fido2ConnectionState.Error) {
                context.startForegroundService(Fido2TransportService.startIntent(context))
                // Wait for the transport service to initialize and start advertising
                withTimeoutOrNull(SERVICE_STARTUP_TIMEOUT_MS) {
                    connectionState.first { it is Fido2ConnectionState.Advertising }
                }
            }
            fido2Transport.connectDevice(macAddress)
        }
    }

    fun dismissOutboundBleUnsupported() {
        _outboundBleUnsupported.value = false
    }

    @Req("FR-BLE-070", "TR-BLE-040")
    fun retryTransport() {
        context.startForegroundService(Fido2TransportService.retryIntent(context))
    }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun testRegistration(options: MakeCredentialOptions) {
        // Mock a registration request as if it came from Bluetooth
        uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, CompletableDeferred()))
    }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getPendingRegistration(): Fido2UiEvent.RegistrationRequested? = uiEventBus.currentRegistrationRequest

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getPendingAuthentication(): Fido2UiEvent.AuthenticationRequested? = uiEventBus.currentAuthenticationRequest

    companion object {
        private const val SUBSCRIBED_STOP_TIMEOUT_MS = 5000L
        private const val SERVICE_STARTUP_TIMEOUT_MS = 10_000L
    }
}

internal enum class TransportToggleAction {
    Start,
    Retry,
    Stop,
}

internal fun transportToggleAction(
    connectionState: Fido2ConnectionState,
    availability: TransportAvailability,
): TransportToggleAction =
    when (connectionState) {
        Fido2ConnectionState.Idle -> {
            if (availability is TransportAvailability.Unavailable) {
                TransportToggleAction.Retry
            } else {
                TransportToggleAction.Start
            }
        }
        is Fido2ConnectionState.Error -> TransportToggleAction.Retry
        else -> TransportToggleAction.Stop
    }
