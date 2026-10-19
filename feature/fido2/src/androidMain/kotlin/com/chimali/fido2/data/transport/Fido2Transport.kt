package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.HidConnectionState
import kotlinx.coroutines.flow.StateFlow

/** Contract for the Bluetooth HID authenticator session and connection lifecycle. */
interface Fido2Transport {
    /** Current connection state; observers do not own or close the flow. */
    val connectionState: StateFlow<HidConnectionState>

    /**
     * Starts advertising for a HID session.
     * @return Success when started, or failure when the transport cannot start.
     */
    @Req("FR-HID-010", "FR-HID-020")
    suspend fun connect(): Result<Unit>

    /**
     * Connects to a host device.
     * @param macAddress Address of the host to connect.
     * @return Success when connected, or the connection failure.
     */
    @Req("FR-HID-010", "FR-HID-020")
    suspend fun connectDevice(macAddress: String): Result<Unit>

    /**
     * Ends the current transport session and releases its connection owners.
     * @return Success when disconnected, or the transport failure.
     */
    @Req("FR-HID-010", "FR-HID-020")
    suspend fun disconnect(): Result<Unit>
}
