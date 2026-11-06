package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import com.chimali.core.common.annotation.Req
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

@Req("FR-BLE-060", "FR-BLE-080", "TR-BLE-010", "TR-BLE-030")
class FidoBleAssertionTest {
    private lateinit var transport: BleGattTransport
    private lateinit var rawCtapOperation: RawCtapOperation
    private lateinit var sessionOwner: BleSessionOwner
    private lateinit var fakeAdvertiser: FakeBleAdvertiserController
    private lateinit var fakeSender: FakeResponseSender
    private lateinit var testDevice: BluetoothDevice

    private class FakeBleAdvertiserController : BleAdvertiserController {
        var isAdvertising = false
        var startCount = 0
        var stopCount = 0

        override suspend fun startAdvertising(
            serviceUuid: java.util.UUID,
            onUnexpectedFailure: (Throwable) -> Unit,
        ): Result<Unit> {
            isAdvertising = true
            startCount++
            return Result.success(Unit)
        }

        override fun stopAdvertising() {
            isAdvertising = false
            stopCount++
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

    companion object {
        private const val CTAP2_OK: Byte = 0x00
        private const val CTAP2_ERR_OPERATION_DENIED: Byte = 0x27
        private const val CTAP2_ERR_UNAUTHORIZED_PERMISSION: Byte = 0x28
        private const val CMD_GET_ASSERTION: Byte = 0x02
        private const val CMD_BLE_MSG: Byte = 0x83.toByte()
        private const val CMD_BLE_KEEPALIVE: Byte = 0x82.toByte()
    }

    @BeforeTest
    fun setUp() {
        testDevice = mockk(relaxed = true)
        every { testDevice.address } returns "11:22:33:44:55:66"
        every { testDevice.bondState } returns BluetoothDevice.BOND_BONDED

        sessionOwner = BleSessionOwner()
        sessionOwner.connectHost(testDevice.address)
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)

        fakeAdvertiser = FakeBleAdvertiserController()
        fakeSender = FakeResponseSender()
        rawCtapOperation = mockk()
    }

    @Test
    @Req("FR-BLE-060", "TR-BLE-010")
    fun `successful assertion returns CTAP2_OK and fragmented CBOR response in FIFO order`() =
        runTest {
            val assertionCbor = ByteArray(60) { it.toByte() }
            val rawResult =
                RawCtapResult(
                    responseBytes = byteArrayOf(CTAP2_OK) + assertionCbor,
                )

            coEvery { rawCtapOperation.handle(any(), any()) } returns rawResult

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }

            transport.connect()

            // Build BLE MSG with CMD_GET_ASSERTION payload
            val getAssertionPayload = byteArrayOf(CMD_GET_ASSERTION, 0xA1.toByte(), 0x01, 0x02)
            val bleMsgFragments =
                FidoBleFraming.frameResponse(
                    command = CMD_BLE_MSG,
                    payload = getAssertionPayload,
                    maxFragmentSize = sessionOwner.writeCapacity,
                )

            // Feed fragment to transport
            transport.onPacketReceived(bleMsgFragments[0])
            testScheduler.advanceUntilIdle()

            // Total response payload is status byte (0x00) + 60 bytes CBOR = 61 bytes
            // With notifyCapacity 20 (default ATT_MTU 23):
            // Initial fragment: 1 cmd + 2 len + 17 data = 20 bytes
            // Cont 0: 1 seq + 19 data = 20 bytes
            // Cont 1: 1 seq + 19 data = 20 bytes
            // Cont 2: 1 seq + 6 data = 7 bytes
            // Total 4 notification packets in FIFO order
            assertEquals(4, fakeSender.sentNotifications.size)

            val p0 = fakeSender.sentNotifications[0]
            assertEquals(CMD_BLE_MSG, p0[0])
            assertEquals(0x00, p0[1])
            assertEquals(61.toByte(), p0[2])
            assertEquals(CTAP2_OK, p0[3]) // status byte is first byte of CTAP response

            val p1 = fakeSender.sentNotifications[1]
            assertEquals(0x00.toByte(), p1[0]) // SEQ 0

            val p2 = fakeSender.sentNotifications[2]
            assertEquals(0x01.toByte(), p2[0]) // SEQ 1

            val p3 = fakeSender.sentNotifications[3]
            assertEquals(0x02.toByte(), p3[0]) // SEQ 2

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-010")
    fun `assertion with UV rejection returns CTAP2_ERR_OPERATION_DENIED`() =
        runTest {
            val rawResult =
                RawCtapResult(
                    responseBytes = byteArrayOf(CTAP2_ERR_OPERATION_DENIED),
                )
            coEvery { rawCtapOperation.handle(any(), any()) } returns rawResult

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            val getAssertionPayload = byteArrayOf(CMD_GET_ASSERTION, 0x00)
            val packet = FidoBleFraming.frameResponse(CMD_BLE_MSG, getAssertionPayload, sessionOwner.writeCapacity)[0]

            transport.onPacketReceived(packet)
            testScheduler.advanceUntilIdle()

            assertEquals(1, fakeSender.sentNotifications.size)
            val response = fakeSender.sentNotifications[0]
            assertEquals(CMD_BLE_MSG, response[0])
            assertEquals(1.toByte(), response[2]) // length 1
            assertEquals(CTAP2_ERR_OPERATION_DENIED, response[3]) // error status

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-010")
    fun `unauthorized assertion request returns CTAP2_ERR_UNAUTHORIZED_PERMISSION`() =
        runTest {
            val rawResult =
                RawCtapResult(
                    responseBytes = byteArrayOf(CTAP2_ERR_UNAUTHORIZED_PERMISSION),
                )
            coEvery { rawCtapOperation.handle(any(), any()) } returns rawResult

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            val getAssertionPayload = byteArrayOf(CMD_GET_ASSERTION, 0x00)
            val packet = FidoBleFraming.frameResponse(CMD_BLE_MSG, getAssertionPayload, sessionOwner.writeCapacity)[0]

            transport.onPacketReceived(packet)
            testScheduler.advanceUntilIdle()

            assertEquals(1, fakeSender.sentNotifications.size)
            val response = fakeSender.sentNotifications[0]
            assertEquals(CMD_BLE_MSG, response[0])
            assertEquals(CTAP2_ERR_UNAUTHORIZED_PERMISSION, response[3])

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `keepalives emitted during assertion processing stop before terminal response fragments`() =
        runTest {
            val assertionCbor = byteArrayOf(0x01, 0x02, 0x03)
            val rawResult =
                RawCtapResult(
                    responseBytes = byteArrayOf(CTAP2_OK) + assertionCbor,
                )

            coEvery { rawCtapOperation.handle(any(), any()) } coAnswers {
                val onKeepalive = secondArg<(Byte) -> Unit>()
                onKeepalive(0x01) // PROCESSING keepalive
                kotlinx.coroutines.yield()
                onKeepalive(0x02) // UP_NEEDED keepalive
                kotlinx.coroutines.yield()
                rawResult
            }

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            val getAssertionPayload = byteArrayOf(CMD_GET_ASSERTION, 0x00)
            val packet = FidoBleFraming.frameResponse(CMD_BLE_MSG, getAssertionPayload, sessionOwner.writeCapacity)[0]

            transport.onPacketReceived(packet)
            testScheduler.advanceUntilIdle()

            // 2 keepalives sent + 1 terminal response packet
            assertEquals(3, fakeSender.sentNotifications.size)
            assertEquals(CMD_BLE_KEEPALIVE, fakeSender.sentNotifications[0][0])
            assertEquals(0x01.toByte(), fakeSender.sentNotifications[0][3])

            assertEquals(CMD_BLE_KEEPALIVE, fakeSender.sentNotifications[1][0])
            assertEquals(0x02.toByte(), fakeSender.sentNotifications[1][3])

            assertEquals(CMD_BLE_MSG, fakeSender.sentNotifications[2][0])
            assertEquals(CTAP2_OK, fakeSender.sentNotifications[2][3])

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-020")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `keepalive cadence continues through processing and user presence then stops before response`() =
        runTest {
            val result = RawCtapResult(responseBytes = byteArrayOf(CTAP2_OK, 0x01))
            val finishUserPresence = CompletableDeferred<Unit>()
            val notificationTimes = mutableListOf<Long>()

            coEvery { rawCtapOperation.handle(any(), any()) } coAnswers {
                val onKeepalive = secondArg<(Byte) -> Unit>()
                delay(700L)
                onKeepalive(0x02)
                finishUserPresence.await()
                result
            }

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = {
                if (fakeSender.sentNotifications.lastOrNull()?.firstOrNull() == CMD_BLE_KEEPALIVE) {
                    notificationTimes += testScheduler.currentTime
                }
                transport.onNotificationSent(true)
            }
            transport.connect()

            val packet =
                FidoBleFraming.frameResponse(
                    CMD_BLE_MSG,
                    byteArrayOf(CMD_GET_ASSERTION, 0x00),
                    sessionOwner.writeCapacity,
                )[0]
            transport.onPacketReceived(packet)

            testScheduler.advanceTimeBy(750L)
            testScheduler.runCurrent()
            val processingPackets =
                fakeSender.sentNotifications.filter { it[0] == CMD_BLE_KEEPALIVE && it[3] == 0x01.toByte() }
            assertEquals(3, processingPackets.size)

            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()
            val allKeepalives = fakeSender.sentNotifications.filter { it[0] == CMD_BLE_KEEPALIVE }
            assertEquals(6, allKeepalives.size)
            assertEquals(3, allKeepalives.count { it[3] == 0x02.toByte() })
            assertTrue(notificationTimes.zipWithNext().all { (first, second) -> second - first <= 500L })

            finishUserPresence.complete(Unit)
            testScheduler.runCurrent()
            val keepaliveCountAtResponse = fakeSender.sentNotifications.count { it[0] == CMD_BLE_KEEPALIVE }
            assertEquals(CMD_BLE_MSG, fakeSender.sentNotifications.last()[0])

            testScheduler.advanceTimeBy(1000L)
            testScheduler.runCurrent()
            assertEquals(
                keepaliveCountAtResponse,
                fakeSender.sentNotifications.count { it[0] == CMD_BLE_KEEPALIVE },
            )
            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-040")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `keepalives resume for the next request after terminal response delivery`() =
        runTest {
            coEvery { rawCtapOperation.handle(any(), any()) } coAnswers {
                delay(300L)
                RawCtapResult(responseBytes = byteArrayOf(CTAP2_OK))
            }

            transport =
                BleGattTransport(
                    rawCtapOperation = rawCtapOperation,
                    sessionOwner = sessionOwner,
                    advertiserController = fakeAdvertiser,
                    responseSender = fakeSender,
                    coroutineScope = this,
                    deviceLookup = { _ -> testDevice },
                )
            fakeSender.onNotificationSentCallback = { transport.onNotificationSent(true) }
            transport.connect()

            val request =
                FidoBleFraming.frameResponse(
                    CMD_BLE_MSG,
                    byteArrayOf(CMD_GET_ASSERTION, 0x00),
                    sessionOwner.writeCapacity,
                )[0]

            transport.onPacketReceived(request)
            testScheduler.advanceTimeBy(300L)
            testScheduler.runCurrent()
            assertEquals(1, fakeSender.sentNotifications.count { it[0] == CMD_BLE_MSG })

            transport.onPacketReceived(request)
            testScheduler.advanceTimeBy(300L)
            testScheduler.runCurrent()

            assertEquals(2, fakeSender.sentNotifications.count { it[0] == CMD_BLE_MSG })
            assertTrue(fakeSender.sentNotifications.any { it[0] == CMD_BLE_KEEPALIVE })
            transport.disconnect()
        }
}
