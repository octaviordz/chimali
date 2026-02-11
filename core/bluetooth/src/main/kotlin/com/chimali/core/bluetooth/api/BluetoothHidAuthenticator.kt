package com.chimali.core.bluetooth.api

/**
 * Interface for managing the Bluetooth HID Virtual Authenticator.
 */
interface BluetoothHidAuthenticator {
    /**
     * Starts the Bluetooth HID advertising and waits for a host to connect.
     */
    fun startAdvertising()

    /**
     * Stops advertising and disconnects any connected host.
     */
    fun stop()

    /**
     * Sends a FIDO2/WebAuthn confirmation to the connected host.
     * This mimics a button press on a hardware security key.
     */
    fun sendConfirmation()

    /**
     * Status of the Bluetooth HID device.
     */
    val state: AuthenticatorState
}

enum class AuthenticatorState {
    IDLE,
    ADVERTISING,
    CONNECTED,
    ERROR
}
