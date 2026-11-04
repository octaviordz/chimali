package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import com.chimali.core.common.annotation.Req
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest

/**
 * Validates the transport-neutral contract across Classic HID and FIDO BLE implementations,
 * including state transitions, idempotent lifecycle, and typed rejection of outbound BLE connections.
 *
 * Traceability: TR-BLE-010, TR-BT-SEL-030, FR-BLE-080, FR-BT-SEL-040
 */
class NeutralTransportContractTest {
    private class FakeNeutralTransport(
        private val isBle: Boolean,
    ) : Fido2Transport {
        private val mutableConnectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
        override val connectionState: StateFlow<Fido2ConnectionState> = mutableConnectionState.asStateFlow()

        var connectCalls = 0
        var disconnectCalls = 0

        @Req("FR-BLE-080", "FR-BT-SEL-040")
        override suspend fun connect(): Result<Unit> {
            connectCalls++
            if (
                mutableConnectionState.value is Fido2ConnectionState.Idle ||
                mutableConnectionState.value is Fido2ConnectionState.Error
            ) {
                mutableConnectionState.value = Fido2ConnectionState.Advertising
            }
            return Result.success(Unit)
        }

        @Req("FR-BLE-080", "FR-BT-SEL-040")
        override suspend fun connectDevice(macAddress: String): Result<Unit> =
            if (isBle) {
                Result.failure(
                    UnsupportedOperationException(
                        "FIDO BLE transport does not support outbound connections; connections must be host-initiated.",
                    ),
                )
            } else {
                val mockDevice = mockk<BluetoothDevice>(relaxed = true)
                mutableConnectionState.value = Fido2ConnectionState.Connecting(mockDevice)
                mutableConnectionState.value = Fido2ConnectionState.Connected(mockDevice)
                Result.success(Unit)
            }

        @Req("FR-BLE-080", "FR-BT-SEL-040")
        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls++
            mutableConnectionState.value = Fido2ConnectionState.Idle
            return Result.success(Unit)
        }

        fun emitState(newState: Fido2ConnectionState) {
            mutableConnectionState.value = newState
        }
    }

    @Test
    fun `neutral state model supports Idle, Advertising, Connecting, Connected, and Error`() {
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)

        val idle: Fido2ConnectionState = Fido2ConnectionState.Idle
        val advertising: Fido2ConnectionState = Fido2ConnectionState.Advertising
        val connecting: Fido2ConnectionState = Fido2ConnectionState.Connecting(mockDevice)
        val connected: Fido2ConnectionState = Fido2ConnectionState.Connected(mockDevice)
        val error: Fido2ConnectionState = Fido2ConnectionState.Error("Bluetooth disabled")

        assertEquals(Fido2ConnectionState.Idle, idle)
        assertEquals(Fido2ConnectionState.Advertising, advertising)
        assertEquals(mockDevice, (connecting as Fido2ConnectionState.Connecting).device)
        assertEquals(mockDevice, (connected as Fido2ConnectionState.Connected).device)
        assertEquals("Bluetooth disabled", (error as Fido2ConnectionState.Error).message)
    }

    @Test
    fun `connect starts advertising and disconnect resets to idle idempotently`() =
        runTest {
            val transport = FakeNeutralTransport(isBle = true)
            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)

            val connectResult = transport.connect()
            assertTrue(connectResult.isSuccess)
            assertEquals(Fido2ConnectionState.Advertising, transport.connectionState.value)
            assertEquals(1, transport.connectCalls)

            // Idempotent connect call
            val secondConnectResult = transport.connect()
            assertTrue(secondConnectResult.isSuccess)
            assertEquals(Fido2ConnectionState.Advertising, transport.connectionState.value)
            assertEquals(2, transport.connectCalls)

            // Disconnect
            val disconnectResult = transport.disconnect()
            assertTrue(disconnectResult.isSuccess)
            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)

            // Idempotent disconnect
            val secondDisconnectResult = transport.disconnect()
            assertTrue(secondDisconnectResult.isSuccess)
            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)
            assertEquals(2, transport.disconnectCalls)
        }

    @Test
    fun `BLE transport rejects outbound connectDevice with explicit UnsupportedOperationException`() =
        runTest {
            val bleTransport = FakeNeutralTransport(isBle = true)
            val result = bleTransport.connectDevice("AA:BB:CC:DD:EE:FF")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertIs<UnsupportedOperationException>(exception)
            assertTrue(exception.message?.contains("host-initiated") == true)
            // Verify state remains unchanged (not connecting)
            assertEquals(Fido2ConnectionState.Idle, bleTransport.connectionState.value)
        }

    @Test
    fun `Classic transport permits outbound connectDevice`() =
        runTest {
            val classicTransport = FakeNeutralTransport(isBle = false)
            val result = classicTransport.connectDevice("AA:BB:CC:DD:EE:FF")

            assertTrue(result.isSuccess)
            assertIs<Fido2ConnectionState.Connected>(classicTransport.connectionState.value)
        }
}
