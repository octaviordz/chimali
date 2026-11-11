package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.chimali.core.common.annotation.Req
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@Req("FR-BLE-010", "FR-BLE-040", "TR-BLE-010", "TR-BLE-020")
class FidoBleLifecycleTest {
    private lateinit var sessionOwner: BleSessionOwner
    private lateinit var fakeAdvertiser: FakeBleAdvertiserController
    private lateinit var fakeSender: FakeResponseSender
    private lateinit var rawCtapOperation: RawCtapOperation
    private lateinit var transport: BleGattTransport

    private class FakeBleAdvertiserController(
        var failOnStart: Boolean = false,
    ) : BleAdvertiserController {
        var isAdvertising = false
        var startCount = 0
        var stopCount = 0
        var beforeStart: (() -> Unit)? = null

        override suspend fun startAdvertising(
            serviceUuid: UUID,
            onUnexpectedFailure: (Throwable) -> Unit,
        ): Result<Unit> {
            beforeStart?.invoke()
            startCount++
            return if (failOnStart) {
                Result.failure(IllegalStateException("Bluetooth adapter disabled"))
            } else {
                isAdvertising = true
                Result.success(Unit)
            }
        }

        override fun stopAdvertising() {
            stopCount++
            isAdvertising = false
        }
    }

    private class FakeGattServerHost : BleGattServerHost {
        var isRegistered = false
        var startCount = 0
        var stopCount = 0
        private var notificationSentListener: ((BluetoothDevice, Int) -> Unit)? = null
        private var connectionStateListener: ((BluetoothDevice, Int, Int) -> Unit)? = null
        private var authenticationFailureListener: ((BluetoothDevice) -> Unit)? = null

        override suspend fun start(
            onPacketReceived: (ByteArray) -> Unit,
            onNotificationSent: (BluetoothDevice, Int) -> Unit,
            onConnectionStateChanged: (BluetoothDevice, Int, Int) -> Unit,
            onAuthenticationFailure: (BluetoothDevice) -> Unit,
        ): Result<Unit> {
            startCount++
            isRegistered = true
            notificationSentListener = onNotificationSent
            connectionStateListener = onConnectionStateChanged
            authenticationFailureListener = onAuthenticationFailure
            return Result.success(Unit)
        }

        override fun stop() {
            stopCount++
            isRegistered = false
        }

        fun reportConnectionState(
            device: BluetoothDevice,
            status: Int,
            newState: Int,
        ) {
            checkNotNull(connectionStateListener).invoke(device, status, newState)
        }

        fun reportAuthenticationFailure(device: BluetoothDevice) {
            checkNotNull(authenticationFailureListener).invoke(device)
        }

        fun reportNotificationSent(
            device: BluetoothDevice,
            status: Int,
        ) {
            checkNotNull(notificationSentListener).invoke(device, status)
        }
    }

    private class FakeResponseSender : GattResponseSender {
        val sentNotifications = mutableListOf<ByteArray>()

        override fun sendResponse(
            device: BluetoothDevice,
            requestId: Int,
            status: Int,
            offset: Int,
            value: ByteArray?,
        ): Boolean = true

        override fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: BluetoothGattCharacteristic,
            confirm: Boolean,
            value: ByteArray,
        ): Boolean {
            sentNotifications.add(value.copyOf())
            return true
        }
    }

    @BeforeTest
    fun setUp() {
        sessionOwner = BleSessionOwner()
        fakeAdvertiser = FakeBleAdvertiserController()
        fakeSender = FakeResponseSender()
        rawCtapOperation = mockk(relaxed = true)
    }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `connect starts advertising and transitions connection state to Advertising`() =
        runTest {
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)

            val result = transport.connect()
            assertTrue(result.isSuccess)
            assertTrue(fakeAdvertiser.isAdvertising)
            assertEquals(1, fakeAdvertiser.startCount)
            assertEquals(Fido2ConnectionState.Advertising, transport.connectionState.value)

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `incomplete request autonomously sends timeout error after three seconds`() =
        runTest {
            val host = mockk<BluetoothDevice>()
            val address = "11:22:33:44:55:66"
            every { host.address } returns address
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { host },
                )
            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(address))
            assertTrue(sessionOwner.selectRevision(BleSessionOwner.REVISION_FIDO2_0x20))
            sessionOwner.setNotificationsEnabled(true)

            transport.onPacketReceived(byteArrayOf(BleGattTransport.CMD_MSG, 0x00, 30) + ByteArray(17))
            advanceTimeBy(BleSessionOwner.RECEIVE_TIMEOUT_NANOS / 1_000_000L)
            runCurrent()

            assertEquals(1, fakeSender.sentNotifications.size)
            assertContentEquals(
                byteArrayOf(
                    BleGattTransport.CMD_ERROR,
                    0x00,
                    0x01,
                    FidoBleFraming.ERR_REQ_TIMEOUT,
                ),
                fakeSender.sentNotifications.single(),
            )
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `connect registers FIDO service before advertising and closes server on stop`() =
        runTest {
            val gattHost = FakeGattServerHost()
            fakeAdvertiser.beforeStart = { assertTrue(gattHost.isRegistered) }
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                )

            assertTrue(transport.connect().isSuccess)
            assertEquals(1, gattHost.startCount)
            assertTrue(fakeAdvertiser.isAdvertising)

            transport.disconnect()
            assertEquals(1, gattHost.stopCount)
            assertFalse(gattHost.isRegistered)
        }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `duplicate connect calls are idempotent and do not restart advertiser`() =
        runTest {
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            transport.connect()
            transport.connect()
            transport.connect()

            assertEquals(1, fakeAdvertiser.startCount)
            assertEquals(Fido2ConnectionState.Advertising, transport.connectionState.value)

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `connect failure transitions to Error state and returns failure`() =
        runTest {
            fakeAdvertiser.failOnStart = true
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            val result = transport.connect()
            assertTrue(result.isFailure)
            assertFalse(fakeAdvertiser.isAdvertising)
            assertIs<Fido2ConnectionState.Error>(transport.connectionState.value)
        }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `disconnect stops advertising, clears active host, and transitions to Idle`() =
        runTest {
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            transport.connect()
            sessionOwner.connectHost("AA:BB:CC:DD:EE:FF")
            assertEquals("AA:BB:CC:DD:EE:FF", sessionOwner.activeDeviceAddress)

            val result = transport.disconnect()
            assertTrue(result.isSuccess)
            assertFalse(fakeAdvertiser.isAdvertising)
            assertEquals(1, fakeAdvertiser.stopCount)
            assertEquals(null, sessionOwner.activeDeviceAddress)
            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)
        }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `disconnect is idempotent when called repeatedly`() =
        runTest {
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            transport.connect()
            transport.disconnect()
            val second = transport.disconnect()

            assertTrue(second.isSuccess)
            assertEquals(Fido2ConnectionState.Idle, transport.connectionState.value)
        }

    @Test
    @Req("FR-BLE-070", "TR-BLE-040")
    fun `failed BLE link surfaces pairing recovery error after session cleanup`() =
        runTest {
            val gattHost = FakeGattServerHost()
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                )
            val host = mockk<BluetoothDevice>()
            every { host.address } returns STALE_BOND_HOST_ADDRESS

            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(host.address))
            gattHost.reportConnectionState(host, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            assertEquals(Fido2ConnectionState.Connected(host), transport.connectionState.value)

            sessionOwner.disconnectHost(host.address)
            gattHost.reportConnectionState(
                host,
                BluetoothGatt.GATT_FAILURE,
                BluetoothProfile.STATE_DISCONNECTED,
            )

            val error = assertIs<Fido2ConnectionState.Error>(transport.connectionState.value)
            assertEquals("Bluetooth connection or pairing failed", error.message)
            assertEquals(null, sessionOwner.activeDeviceAddress)
            assertFalse(fakeAdvertiser.isAdvertising)
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-070", "TR-BLE-040")
    fun `authentication failure stops advertising until explicit retry`() =
        runTest {
            val gattHost = FakeGattServerHost()
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                )
            val host = mockk<BluetoothDevice>()
            every { host.address } returns STALE_BOND_HOST_ADDRESS

            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(host.address))
            gattHost.reportConnectionState(host, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            sessionOwner.disconnectHost(host.address)
            gattHost.reportAuthenticationFailure(host)

            val error = assertIs<Fido2ConnectionState.Error>(transport.connectionState.value)
            assertEquals("Bluetooth pairing authentication failed", error.message)
            assertEquals(null, sessionOwner.activeDeviceAddress)
            assertFalse(fakeAdvertiser.isAdvertising)

            assertTrue(transport.disconnect().isSuccess)
            assertTrue(transport.connect().isSuccess)
            assertTrue(fakeAdvertiser.isAdvertising)
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "FR-BLE-070", "TR-BLE-040")
    fun `unexpected peer disconnect cancels the active CTAP operation and drops its response`() =
        runTest {
            val gattHost = FakeGattServerHost()
            val host = mockk<BluetoothDevice>()
            every { host.address } returns STALE_BOND_HOST_ADDRESS
            val operation = CompletableDeferred<RawCtapResult>()
            var operationStarted = false
            var operationCancelled = false
            var operationCount = 0
            coEvery { rawCtapOperation.handle(any(), any()) } coAnswers {
                operationCount++
                if (operationCount == 1) {
                    operationStarted = true
                    try {
                        operation.await()
                    } catch (failure: CancellationException) {
                        operationCancelled = true
                        throw failure
                    }
                } else {
                    RawCtapResult(responseBytes = byteArrayOf(0x00))
                }
            }
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                    deviceLookup = { address -> if (address == host.address) host else null },
                )

            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(host.address))
            gattHost.reportConnectionState(host, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            assertTrue(sessionOwner.selectRevision(BleSessionOwner.REVISION_FIDO2_0x20))
            sessionOwner.setNotificationsEnabled(true)

            transport.onPacketReceived(
                byteArrayOf(
                    BleGattTransport.CMD_MSG,
                    0,
                    1,
                    RawCtapOperationImpl.CMD_GET_INFO,
                ),
            )
            runCurrent()
            assertTrue(operationStarted)

            // Match the physical interruption case: a processing keepalive has been sent,
            // but its completion callback may still be pending when the peer disconnects.
            advanceTimeBy(101)
            runCurrent()
            assertEquals(1, fakeSender.sentNotifications.size)
            assertEquals(BleGattTransport.CMD_KEEPALIVE, fakeSender.sentNotifications.single()[0])
            fakeSender.sentNotifications.clear()

            sessionOwner.disconnectHost(host.address)
            gattHost.reportConnectionState(host, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
            runCurrent()

            assertTrue(operationCancelled, "A peer disconnect must cancel in-flight CTAP work")
            assertTrue(fakeSender.sentNotifications.isEmpty(), "A disconnected peer must not receive a late response")
            assertEquals(Fido2ConnectionState.Advertising, transport.connectionState.value)
            assertTrue(fakeAdvertiser.isAdvertising)

            // The same bonded peer reconnects and starts a fresh request after its prior CTAP owner was cancelled.
            assertTrue(sessionOwner.connectHost(host.address))
            gattHost.reportConnectionState(host, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            assertTrue(sessionOwner.selectRevision(BleSessionOwner.REVISION_FIDO2_0x20))
            sessionOwner.setNotificationsEnabled(true)
            transport.onPacketReceived(
                byteArrayOf(
                    BleGattTransport.CMD_MSG,
                    0,
                    1,
                    RawCtapOperationImpl.CMD_GET_INFO,
                ),
            )
            runCurrent()
            gattHost.reportNotificationSent(host, BluetoothGatt.GATT_SUCCESS)
            runCurrent()

            assertEquals(2, operationCount, "The successor request must reach a fresh CTAP operation")
            assertEquals(1, fakeSender.sentNotifications.size, "Only the successor request may produce a response")
            assertContentEquals(
                byteArrayOf(BleGattTransport.CMD_MSG, 0, 1, 0x00),
                fakeSender.sentNotifications.single(),
            )
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-040", "FR-BLE-070", "TR-BLE-020", "TR-BLE-040")
    fun `late disconnect callback from previous host does not disturb the successor session`() =
        runTest {
            val gattHost = FakeGattServerHost()
            val previousHost = mockk<BluetoothDevice>()
            val successorHost = mockk<BluetoothDevice>()
            every { previousHost.address } returns STALE_BOND_HOST_ADDRESS
            every { successorHost.address } returns SUCCESSOR_HOST_ADDRESS
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                )

            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(previousHost.address))
            gattHost.reportConnectionState(previousHost, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            sessionOwner.disconnectHost(previousHost.address)
            gattHost.reportConnectionState(
                previousHost,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED,
            )
            runCurrent()
            assertTrue(fakeAdvertiser.isAdvertising)

            assertTrue(sessionOwner.connectHost(successorHost.address))
            gattHost.reportConnectionState(successorHost, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            assertEquals(Fido2ConnectionState.Connected(successorHost), transport.connectionState.value)
            assertFalse(fakeAdvertiser.isAdvertising)

            // The platform may deliver an old peer's terminal callback after the new peer is active.
            sessionOwner.disconnectHost(previousHost.address)
            gattHost.reportConnectionState(
                previousHost,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED,
            )
            runCurrent()

            assertEquals(Fido2ConnectionState.Connected(successorHost), transport.connectionState.value)
            assertEquals(successorHost.address, sessionOwner.activeDeviceAddress)
            assertFalse(fakeAdvertiser.isAdvertising)
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-050", "FR-BLE-070", "TR-BLE-020", "TR-BLE-040")
    fun `notification completion from previous host does not advance successor queue`() =
        runTest {
            val gattHost = FakeGattServerHost()
            val previousHost = mockk<BluetoothDevice>()
            val successorHost = mockk<BluetoothDevice>()
            every { previousHost.address } returns STALE_BOND_HOST_ADDRESS
            every { successorHost.address } returns SUCCESSOR_HOST_ADDRESS
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    gattServerHost = gattHost,
                    deviceLookup = { address -> if (address == successorHost.address) successorHost else null },
                )

            assertTrue(transport.connect().isSuccess)
            assertTrue(sessionOwner.connectHost(previousHost.address))
            gattHost.reportConnectionState(previousHost, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            sessionOwner.disconnectHost(previousHost.address)
            gattHost.reportConnectionState(
                previousHost,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED,
            )
            runCurrent()

            assertTrue(sessionOwner.connectHost(successorHost.address))
            gattHost.reportConnectionState(successorHost, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            assertTrue(sessionOwner.selectRevision(BleSessionOwner.REVISION_FIDO2_0x20))
            sessionOwner.setNotificationsEnabled(true)

            transport.onPacketReceived(byteArrayOf(BleGattTransport.CMD_PING, 0, 1, 0x11))
            runCurrent()
            transport.onPacketReceived(byteArrayOf(BleGattTransport.CMD_PING, 0, 1, 0x22))
            runCurrent()
            assertEquals(1, fakeSender.sentNotifications.size)

            gattHost.reportNotificationSent(previousHost, BluetoothGatt.GATT_SUCCESS)
            runCurrent()
            assertEquals(
                1,
                fakeSender.sentNotifications.size,
                "An old peer callback must not advance the successor queue",
            )

            gattHost.reportNotificationSent(successorHost, BluetoothGatt.GATT_SUCCESS)
            runCurrent()
            assertEquals(2, fakeSender.sentNotifications.size)
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `single host exclusivity rejects second peer while first peer is active`() =
        runTest {
            val host1 = "11:22:33:44:55:66"
            val host2 = "99:88:77:66:55:44"

            assertTrue(sessionOwner.connectHost(host1))
            assertEquals(host1, sessionOwner.activeDeviceAddress)

            // Attempting to connect a second host while host1 is connected must be rejected
            assertFalse(sessionOwner.connectHost(host2))
            assertEquals(host1, sessionOwner.activeDeviceAddress)

            // Disconnecting host1 frees the session
            sessionOwner.disconnectHost(host1)
            assertEquals(null, sessionOwner.activeDeviceAddress)

            // Now host2 is accepted
            assertTrue(sessionOwner.connectHost(host2))
            assertEquals(host2, sessionOwner.activeDeviceAddress)
        }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `disconnect increments session generation to fence late incoming packets`() =
        runTest {
            val host = "11:22:33:44:55:66"
            sessionOwner.connectHost(host)
            val gen1 = sessionOwner.currentSessionGeneration

            sessionOwner.disconnectHost(host)
            val gen2 = sessionOwner.currentSessionGeneration

            assertTrue(gen2 > gen1, "Session generation must increment on host disconnect to fence late packets")
            assertFalse(sessionOwner.isSessionCurrent(gen1), "Old session generation must no longer be current")
            assertTrue(sessionOwner.isSessionCurrent(gen2), "New session generation must be current")
        }

    @Test
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    fun `outbound connectDevice fails immediately with UnsupportedOutboundConnectionException`() =
        runTest {
            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                )

            val result = transport.connectDevice("AA:BB:CC:DD:EE:FF")
            assertTrue(result.isFailure)
            assertIs<UnsupportedOutboundConnectionException>(result.exceptionOrNull())
        }

    private companion object {
        const val STALE_BOND_HOST_ADDRESS = "11:22:33:44:55:66"
        const val SUCCESSOR_HOST_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
