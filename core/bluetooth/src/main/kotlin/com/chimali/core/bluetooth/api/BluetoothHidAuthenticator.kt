package com.chimali.core.bluetooth.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing the Bluetooth HID Virtual Authenticator.
 */
interface BluetoothHidAuthenticator {
    /**
     * Starts the Bluetooth HID advertising and waits for a host to connect.
     * The HID app is registered with the system, making the device visible
     * to a host looking for a FIDO security key.
     */
    fun startAdvertising()

    /**
     * Stops advertising and disconnects any connected host.
     * Unregisters the HID app from the Bluetooth stack.
     */
    fun stop()

    /**
     * Sends a FIDO2/WebAuthn confirmation wink to the connected host.
     */
    fun sendConfirmation()

    /**
     * Current status of the Bluetooth HID device as a [StateFlow] for reactive UI.
     */
    val stateFlow: StateFlow<AuthenticatorState>

    /**
     * Current snapshot of the status.
     */
    val state: AuthenticatorState
}

enum class AuthenticatorState {
    IDLE,
    ADVERTISING,
    CONNECTED,
    ERROR
}
