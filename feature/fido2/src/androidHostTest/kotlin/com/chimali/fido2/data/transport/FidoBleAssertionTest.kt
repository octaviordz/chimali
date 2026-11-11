package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import com.chimali.core.common.annotation.Req
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        private const val CMD_MAKE_CREDENTIAL: Byte = 0x01
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
            val assertionCbor = validGetAssertionResponse()
            assertEquals(116, assertionCbor.size)
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

            // Valid GetAssertion CBOR with a single allow-list credential and a 32-byte clientDataHash.
            val getAssertionRequest = validGetAssertionRequest()
            assertEquals(89, getAssertionRequest.size)
            val requestFrames =
                listOf(
                    byteArrayOf(CMD_BLE_MSG, 0x00, getAssertionRequest.size.toByte()) +
                        getAssertionRequest.copyOfRange(0, 17),
                    byteArrayOf(0x00) + getAssertionRequest.copyOfRange(17, 36),
                    byteArrayOf(0x01) + getAssertionRequest.copyOfRange(36, 55),
                    byteArrayOf(0x02) + getAssertionRequest.copyOfRange(55, 74),
                    byteArrayOf(0x03) + getAssertionRequest.copyOfRange(74, getAssertionRequest.size),
                )

            requestFrames.forEach(transport::onPacketReceived)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { rawCtapOperation.handle(match { it.contentEquals(getAssertionRequest) }, any()) }

            // Total response payload is status byte (0x00) + 116 bytes CBOR = 117 bytes
            // With notifyCapacity 20 (default ATT_MTU 23):
            // Initial fragment: 1 cmd + 2 len + 17 data = 20 bytes
            // Cont 0: 1 seq + 19 data = 20 bytes
            // Cont 1: 1 seq + 19 data = 20 bytes
            // Cont 0–4: 1 seq + 19 data = 20 bytes each
            // Cont 5: 1 seq + 5 data = 6 bytes
            // Total 7 notification packets in FIFO order
            assertEquals(7, fakeSender.sentNotifications.size)

            val expectedResponse = byteArrayOf(CTAP2_OK) + assertionCbor
            val expectedNotifications =
                listOf(
                    byteArrayOf(CMD_BLE_MSG, 0x00, expectedResponse.size.toByte()) +
                        expectedResponse.copyOfRange(0, 17),
                    byteArrayOf(0x00) + expectedResponse.copyOfRange(17, 36),
                    byteArrayOf(0x01) + expectedResponse.copyOfRange(36, 55),
                    byteArrayOf(0x02) + expectedResponse.copyOfRange(55, 74),
                    byteArrayOf(0x03) + expectedResponse.copyOfRange(74, 93),
                    byteArrayOf(0x04) + expectedResponse.copyOfRange(93, 112),
                    byteArrayOf(0x05) + expectedResponse.copyOfRange(112, expectedResponse.size),
                )
            assertEquals(expectedNotifications.size, fakeSender.sentNotifications.size)
            expectedNotifications.zip(fakeSender.sentNotifications).forEachIndexed { index, (expected, actual) ->
                assertContentEquals(
                    expected,
                    actual,
                    "Notification fragment $index must match the complete expected bytes",
                )
            }

            transport.disconnect()
        }

    @Test
    @Req("FR-BLE-060", "TR-BLE-010")
    fun `fragmented MakeCredential corpus preserves request and attestation response bytes`() =
        runTest {
            val request = validMakeCredentialRequest()
            val expectedResponse = validMakeCredentialResponse()
            assertEquals(150, request.size)
            assertEquals(161, expectedResponse.size)
            coEvery { rawCtapOperation.handle(any(), any()) } returns RawCtapResult(responseBytes = expectedResponse)

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

            val requestFrames = fragmentExpectedMessage(CMD_BLE_MSG, request, sessionOwner.writeCapacity)
            assertEquals(8, requestFrames.size)
            assertTrue(requestFrames.size > 2, "The MakeCredential request corpus case must use continuations")
            requestFrames.forEach(transport::onPacketReceived)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { rawCtapOperation.handle(match { it.contentEquals(request) }, any()) }
            val expectedFrames = fragmentExpectedMessage(CMD_BLE_MSG, expectedResponse, sessionOwner.notifyCapacity)
            assertEquals(9, expectedFrames.size)
            assertTrue(expectedFrames.size > 2, "The MakeCredential response corpus case must use continuations")
            assertEquals(expectedFrames.size, fakeSender.sentNotifications.size)
            expectedFrames.zip(fakeSender.sentNotifications).forEachIndexed { index, (expected, actual) ->
                assertContentEquals(expected, actual, "Attestation packet $index must preserve all bytes and order")
            }
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

    private fun validMakeCredentialRequest(): ByteArray {
        val clientDataHash = ByteArray(32) { (it + 1).toByte() }
        val rpId = "example.com".encodeToByteArray()
        val rpName = "Example".encodeToByteArray()
        val userId = ByteArray(16) { (it + 1).toByte() }
        val userName = "alice".encodeToByteArray()
        val displayName = "Alice Example".encodeToByteArray()
        return byteArrayOf(CMD_MAKE_CREDENTIAL, 0xA4.toByte(), 0x01, 0x58, 0x20) +
            clientDataHash +
            byteArrayOf(0x02, 0xA2.toByte(), 0x62, 0x69, 0x64, 0x6B) +
            rpId +
            byteArrayOf(0x64, 0x6E, 0x61, 0x6D, 0x65, 0x67) +
            rpName +
            byteArrayOf(0x03, 0xA3.toByte(), 0x62, 0x69, 0x64, 0x50) +
            userId +
            byteArrayOf(0x64, 0x6E, 0x61, 0x6D, 0x65, 0x65) +
            userName +
            byteArrayOf(0x6B, 0x64, 0x69, 0x73, 0x70, 0x6C, 0x61, 0x79, 0x4E, 0x61, 0x6D, 0x65, 0x6D) +
            displayName +
            byteArrayOf(0x04, 0x81.toByte(), 0xA2.toByte(), 0x64, 0x74, 0x79, 0x70, 0x65, 0x6A) +
            "public-key".encodeToByteArray() +
            byteArrayOf(0x63, 0x61, 0x6C, 0x67, 0x26)
    }

    private fun validGetAssertionResponse(): ByteArray {
        val rpIdHash = MessageDigest.getInstance("SHA-256").digest("example.com".encodeToByteArray())
        val authenticatorData = rpIdHash + byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00)
        val clientDataHash = ByteArray(32) { (it + 1).toByte() }
        val signature =
            (
                "3046022100E1468C2B5215BD30923C5AAFDDE652A4E393EB8D0D0FAA848D7B2BA46C5AD17B" +
                    "022100C0DDDCC31808E08280BDAAF5CCE375552FB139DED9DB8408ACDE599EAE49EDE2"
            ).hexBytes()
        val ecParameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val publicKey =
            KeyFactory.getInstance("EC").generatePublic(
                ECPublicKeySpec(
                    java.security.spec.ECPoint(
                        BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
                        BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
                    ),
                    ecParameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java),
                ),
            )
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(authenticatorData + clientDataHash)
        assertTrue(verifier.verify(signature), "The GetAssertion response fixture must contain a valid signature")
        // The request has one allow-list credential, so the response may omit its descriptor.
        return byteArrayOf(0xA2.toByte(), 0x02, 0x58, authenticatorData.size.toByte()) +
            authenticatorData +
            byteArrayOf(0x03, 0x58, signature.size.toByte()) +
            signature
    }

    private fun validGetAssertionRequest(): ByteArray {
        val credentialId = ByteArray(16) { (0xA0 + it).toByte() }
        return byteArrayOf(CMD_GET_ASSERTION, 0xA3.toByte(), 0x01, 0x6B) +
            "example.com".encodeToByteArray() +
            byteArrayOf(0x02, 0x58, 0x20) +
            ByteArray(32) { (it + 1).toByte() } +
            byteArrayOf(0x03, 0x81.toByte(), 0xA2.toByte(), 0x64, 0x74, 0x79, 0x70, 0x65, 0x6A) +
            "public-key".encodeToByteArray() +
            byteArrayOf(0x62, 0x69, 0x64, 0x50) +
            credentialId
    }

    private fun validMakeCredentialResponse(): ByteArray {
        val rpIdHash = MessageDigest.getInstance("SHA-256").digest("example.com".encodeToByteArray())
        val credentialId = ByteArray(16) { (0xA0 + it).toByte() }
        val cosePublicKey =
            byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20) +
                "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296".hexBytes() +
                byteArrayOf(0x22, 0x58, 0x20) +
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5".hexBytes()
        val authenticatorData =
            rpIdHash +
                byteArrayOf(0x41, 0x00, 0x00, 0x00, 0x00) + // UP + AT flags and zero signature counter.
                ByteArray(16) { it.toByte() } +
                byteArrayOf(0x00, credentialId.size.toByte()) +
                credentialId +
                cosePublicKey
        assertEquals(148, authenticatorData.size)
        val attestationObject =
            byteArrayOf(0xA3.toByte(), 0x01, 0x64) +
                "none".encodeToByteArray() +
                byteArrayOf(0x02, 0x58, authenticatorData.size.toByte()) +
                authenticatorData +
                byteArrayOf(0x03, 0xA0.toByte())
        return byteArrayOf(CTAP2_OK) + attestationObject
    }

    private fun fragmentExpectedMessage(
        command: Byte,
        payload: ByteArray,
        frameCapacity: Int,
    ): List<ByteArray> {
        require(payload.size <= 0xFFFF)
        val frames = mutableListOf<ByteArray>()
        val initialDataLength = minOf(payload.size, frameCapacity - 3)
        frames +=
            byteArrayOf(command, (payload.size ushr 8).toByte(), payload.size.toByte()) +
            payload.copyOfRange(0, initialDataLength)
        var offset = initialDataLength
        var sequence = 0
        while (offset < payload.size) {
            val continuationDataLength = minOf(payload.size - offset, frameCapacity - 1)
            frames += byteArrayOf(sequence.toByte()) + payload.copyOfRange(offset, offset + continuationDataLength)
            offset += continuationDataLength
            sequence = (sequence + 1) and 0x7F
        }
        return frames
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
