package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import com.chimali.core.common.annotation.Req
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Req("FR-BLE-050", "FR-BLE-060", "TR-BLE-020", "TR-BLE-040")
class FidoBleCancellationTest {
    private lateinit var sessionOwner: BleSessionOwner
    private lateinit var fakeAdvertiser: FakeBleAdvertiserController
    private lateinit var fakeSender: FakeResponseSender
    private lateinit var rawCtapOperation: RawCtapOperation
    private lateinit var testDevice: BluetoothDevice

    private class FakeBleAdvertiserController : BleAdvertiserController {
        var stopCalls = 0

        override suspend fun startAdvertising(
            serviceUuid: UUID,
            onUnexpectedFailure: (Throwable) -> Unit,
        ): Result<Unit> = Result.success(Unit)

        override fun stopAdvertising() {
            stopCalls++
        }
    }

    private class FakeResponseSender(
        var onNotificationSentCallback: (() -> Unit)? = null,
    ) : GattResponseSender {
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
            onNotificationSentCallback?.invoke()
            return true
        }
    }

    @BeforeTest
    fun setUp() {
        testDevice = mockk(relaxed = true)
        sessionOwner = BleSessionOwner()
        sessionOwner.connectHost("11:22:33:44:55:66")
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)

        fakeAdvertiser = FakeBleAdvertiserController()
        fakeSender = FakeResponseSender()
        rawCtapOperation = mockk()
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `valid CANCEL bypasses BUSY state, cancels in-flight operation, and sends no response`() =
        runTest {
            val deferred = CompletableDeferred<RawCtapResult>()
            var wasCancelled = false

            coEvery { rawCtapOperation.handle(any(), any()) } coAnswers {
                try {
                    deferred.await()
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                }
            }

            val transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            // 1. Send first CMD_MSG
            val msg1 = byteArrayOf(0x83.toByte(), 0x00, 0x02, 0x01, 0x02)
            transport.onPacketReceived(msg1)
            runCurrent()

            // 2. Overlapping CMD_MSG while operation is in-flight must be rejected with ERR_BUSY
            val msg2 = byteArrayOf(0x83.toByte(), 0x00, 0x02, 0x03, 0x04)
            transport.onPacketReceived(msg2)
            runCurrent()

            // Check ERR_BUSY (0xBF 0x00 0x01 0x06)
            assertEquals(1, fakeSender.sentNotifications.size)
            val busyPacket = fakeSender.sentNotifications[0]
            assertEquals(0xBF.toByte(), busyPacket[0]) // CMD_ERROR
            assertEquals(0x06.toByte(), busyPacket[3]) // ERR_BUSY

            // 3. Send valid CMD_CANCEL (empty payload) - must bypass BUSY and cancel operation
            val cancelPacket = byteArrayOf(0xBE.toByte(), 0x00, 0x00)
            transport.onPacketReceived(cancelPacket)
            runCurrent()

            assertTrue(wasCancelled, "In-flight operation must be cancelled upon valid CMD_CANCEL")
            // CANCEL receives NO response per CTAP 2.2 spec
            assertEquals(1, fakeSender.sentNotifications.size)

            transport.disconnect()
            assertEquals(1, fakeAdvertiser.stopCalls)
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `unexpected CTAP operation failure returns a CTAP error to the connected host`() =
        runTest {
            coEvery { rawCtapOperation.handle(any(), any()) } throws IllegalStateException("simulated failure")
            val transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            transport.onPacketReceived(byteArrayOf(0x83.toByte(), 0x00, 0x01, RawCtapOperationImpl.CMD_GET_INFO))
            runCurrent()

            assertEquals(1, fakeSender.sentNotifications.size)
            assertContentEquals(
                byteArrayOf(0x83.toByte(), 0x00, 0x01, RawCtapOperationImpl.CTAP2_ERR_OTHER),
                fakeSender.sentNotifications.single(),
            )
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `malformed CANCEL with non-zero payload is rejected with ERR_INVALID_LEN`() =
        runTest {
            val transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            // CMD_CANCEL with 1 byte payload (invalid length)
            val malformedCancel = byteArrayOf(0xBE.toByte(), 0x00, 0x01, 0xFF.toByte())
            transport.onPacketReceived(malformedCancel)
            runCurrent()

            assertEquals(1, fakeSender.sentNotifications.size)
            val errResponse = fakeSender.sentNotifications[0]
            assertEquals(0xBF.toByte(), errResponse[0]) // CMD_ERROR
            assertEquals(0x03.toByte(), errResponse[3]) // ERR_INVALID_LEN

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-040")
    fun `notification queue rejects packets exceeding maximum capacity`() =
        runTest {
            var stalled = false
            val writer =
                BleNotificationWriter(
                    scope = this,
                    maxMsgSize = 100, // maxCapacity = 2 * 100 + 1024 = 1224 bytes
                    sendGattNotification = { _, _ -> false },
                    onSessionStalled = { _ -> stalled = true },
                )

            // First packet is taken in-flight
            val largePayload1 = ByteArray(1000) { 0x01 }
            val accepted1 = writer.enqueue(largePayload1, isKeepalive = false, sessionGeneration = 1L)
            assertTrue(accepted1)

            // Second packet is retained in queue (1000 bytes queued <= 1224 max capacity)
            val largePayload2 = ByteArray(1000) { 0x02 }
            val accepted2 = writer.enqueue(largePayload2, isKeepalive = false, sessionGeneration = 1L)
            assertTrue(accepted2)

            // Third packet would push queued bytes to 1000 + 500 = 1500 > 1224 bytes
            val largePayload3 = ByteArray(500) { 0x03 }
            val accepted3 = writer.enqueue(largePayload3, isKeepalive = false, sessionGeneration = 1L)
            assertFalse(accepted3, "Enqueueing past maximum capacity must be rejected")
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-040")
    fun `missing notification callback triggers onSessionStalled at 5 seconds`() =
        runTest {
            var stalled = false
            val writer =
                BleNotificationWriter(
                    scope = this,
                    maxMsgSize = 512,
                    sendGattNotification = { _, _ -> true },
                    onSessionStalled = { _ -> stalled = true },
                )

            val packet = byteArrayOf(0x01, 0x02, 0x03)
            writer.enqueue(packet, isKeepalive = false, sessionGeneration = 1L)
            runCurrent()
            assertFalse(stalled, "Must not stall immediately upon send")

            // Advance 4999ms: still in-flight
            advanceTimeBy(4999L)
            runCurrent()
            assertFalse(stalled, "Must not stall at 4999ms")

            // Advance 1ms to reach 5000ms deadline
            advanceTimeBy(2L)
            runCurrent()
            assertTrue(stalled, "Must trigger onSessionStalled at 5000ms timeout")
        }
}
