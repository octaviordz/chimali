package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import com.chimali.core.common.annotation.Req
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents the transport-neutral authenticator connection and advertising lifecycle.
 *
 * Supported states:
 * - [Idle]: Transport is inactive and not broadcasting.
 * - [Advertising]: Transport is broadcasting and awaiting incoming host connections.
 * - [Connecting]: A host has initiated or is establishing a connection.
 * - [Connected]: A host is connected and ready to exchange CTAP2 commands.
 * - [Error]: An unrecoverable hardware, permission, or transport error occurred.
 */
@Req("FR-BLE-080", "FR-BT-SEL-040", "TR-BLE-010")
sealed class Fido2ConnectionState {
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    object Idle : Fido2ConnectionState()

    @Req("FR-BLE-080", "FR-BT-SEL-040")
    object Advertising : Fido2ConnectionState()

    @Req("FR-BLE-080", "FR-BT-SEL-040")
    data class Connecting(
        @Req("FR-BLE-080", "FR-BT-SEL-040")
        val device: BluetoothDevice? = null,
    ) : Fido2ConnectionState()

    @Req("FR-BLE-080", "FR-BT-SEL-040")
    data class Connected(
        @Req("FR-BLE-080", "FR-BT-SEL-040")
        val device: BluetoothDevice? = null,
    ) : Fido2ConnectionState()

    @Req("FR-BLE-080", "FR-BT-SEL-040")
    data class Error(
        @Req("FR-BLE-080", "FR-BT-SEL-040")
        val message: String,
    ) : Fido2ConnectionState()
}

/** Exception returned when BLE transport is asked to initiate an outbound host connection. */
class UnsupportedOutboundConnectionException(
    message: String =
        "FIDO BLE peripheral transport does not support outbound host connections; connections must be host-initiated.",
) : UnsupportedOperationException(message)

/** Unified contract for FIDO2 transports (Classic HID and FIDO BLE). */
interface Fido2Transport {
    /** Current connection state; observers do not own or close the flow. */
    val connectionState: StateFlow<Fido2ConnectionState>

    /**
     * Starts advertising or listening for host connections.
     * @return Success when started, or failure when the transport cannot start.
     */
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    suspend fun connect(): Result<Unit>

    /**
     * Connects to a host device (Classic HID only).
     * Returns an explicit [UnsupportedOutboundConnectionException] on BLE transport.
     * @param macAddress Address of the host to connect.
     * @return Success when connected, or the connection failure.
     */
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    suspend fun connectDevice(macAddress: String): Result<Unit>

    /**
     * Ends the current transport session and releases its connection resources.
     * @return Success when disconnected, or the transport failure.
     */
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    suspend fun disconnect(): Result<Unit>
}
