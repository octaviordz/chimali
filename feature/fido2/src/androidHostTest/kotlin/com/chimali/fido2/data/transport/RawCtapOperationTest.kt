package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfo
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * T005: Raw CTAP operation boundary tests.
 *
 * Verifies unframed CTAP command routing (MakeCredential, GetAssertion, GetInfo),
 * response status / CBOR payload extraction, master seed session ownership, keepalive
 * reporting, error handling, and Classic HID framing compatibility.
 */
@Req("FR-BLE-080", "TR-BLE-030")
class RawCtapOperationTest {
    private val ownedBuffers = mutableListOf<ByteArray>()
    private val makeCredentialHandler = mockk<Ctap2MakeCredentialHandler>()
    private val getAssertionHandler = mockk<Ctap2GetAssertionHandler>()
    private val authenticatorInfoProvider = mockk<AuthenticatorInfoProvider>()
    private val responseBuilder = mockk<Ctap2ResponseBuilder>()
    private val masterSeedProvider = mockk<MasterSeedProvider>()
    private val hidReportParser = HidReportParser()

    private lateinit var rawCtapOperation: RawCtapOperation

    companion object {
        private const val CMD_MAKE_CREDENTIAL: Byte = 0x01
        private const val CMD_GET_ASSERTION: Byte = 0x02
        private const val CMD_GET_INFO: Byte = 0x04
        private const val CMD_UNKNOWN: Byte = 0x99.toByte()

        private const val CTAP2_OK: Byte = 0x00
        private const val CTAP2_ERR_INVALID_CMD: Byte = 0x01
        private const val STATUS_PROCESSING: Byte = 0x01
        private const val STATUS_UPNEEDED: Byte = 0x02
    }

    private fun own(bytes: ByteArray): ByteArray = bytes.also(ownedBuffers::add)

    private fun concatBytes(vararg parts: ByteArray): ByteArray {
        val result = ByteArray(parts.sumOf(ByteArray::size))
        try {
            var offset = 0
            parts.forEach { part ->
                part.copyInto(result, destinationOffset = offset)
                offset += part.size
            }
            return own(result)
        } catch (failure: Throwable) {
            result.fill(0)
            throw failure
        } finally {
            parts.forEach { it.fill(0) }
        }
    }

    private fun expectedCborResponse(): ByteArray =
        concatBytes(byteArrayOf(CTAP2_OK), byteArrayOf(0xA1.toByte(), 0x01, 0x02))

    @BeforeTest
    fun setUp() {
        coEvery {
            masterSeedProvider.withSessionOwner<RawCtapResult>(
                initialize = any(),
                ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                operation = any(),
            )
        } coAnswers {
            val operation = thirdArg<suspend (Long) -> RawCtapResult>()
            operation.invoke(1L)
        }

        rawCtapOperation =
            RawCtapOperationImpl(
                makeCredentialHandler = makeCredentialHandler,
                getAssertionHandler = getAssertionHandler,
                authenticatorInfoProvider = authenticatorInfoProvider,
                responseBuilder = responseBuilder,
                masterSeedProvider = masterSeedProvider,
            )
    }

    @AfterTest
    fun tearDown() {
        ownedBuffers.forEach { it.fill(0) }
        ownedBuffers.clear()
        clearMocks(
            makeCredentialHandler,
            getAssertionHandler,
            authenticatorInfoProvider,
            responseBuilder,
            masterSeedProvider,
            answers = true,
            recordedCalls = true,
            verificationMarks = true,
            childMocks = true,
        )
    }

    @Test
    fun `teardown erases tracked request and response buffers`() {
        val request = own(byteArrayOf(CMD_GET_INFO))
        val response = own(byteArrayOf(CTAP2_OK, 0xA1.toByte()))

        tearDown()

        assertContentEquals(ByteArray(request.size), request)
        assertContentEquals(ByteArray(response.size), response)
    }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute routes MakeCredential to handler with master seed session ownership`() =
        runTest {
            val request = own(byteArrayOf(CMD_MAKE_CREDENTIAL, 0xA1.toByte(), 0x01, 0x05))
            val expectedResponse = expectedCborResponse()

            coEvery {
                makeCredentialHandler.handleRaw(request, any())
            } returns RawCtapResult(expectedResponse, presenceRequired = true)

            val result = rawCtapOperation.execute(request)
            own(result.responseBytes)

            assertEquals(CTAP2_OK, result.status)
            assertTrue(result.isSuccess)
            assertTrue(result.presenceRequired)
            assertContentEquals(expectedResponse, result.responseBytes)

            coVerify(exactly = 1) {
                masterSeedProvider.withSessionOwner<RawCtapResult>(
                    initialize = any(),
                    ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                    operation = any(),
                )
                makeCredentialHandler.handleRaw(request, any())
            }
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute routes GetAssertion to handler with master seed session ownership`() =
        runTest {
            val request = own(byteArrayOf(CMD_GET_ASSERTION, 0xA1.toByte(), 0x02, 0x06))
            val requestBody = own(byteArrayOf(0xA1.toByte(), 0x02, 0x06))
            val expectedResponse = expectedCborResponse()

            coEvery {
                getAssertionHandler.handleRaw(match { it.contentEquals(requestBody) }, any())
            } returns RawCtapResult(expectedResponse, presenceRequired = false)

            val result = rawCtapOperation.execute(request)
            own(result.responseBytes)

            assertEquals(CTAP2_OK, result.status)
            assertTrue(result.isSuccess)
            assertFalse(result.presenceRequired)
            assertContentEquals(expectedResponse, result.responseBytes)

            coVerify(exactly = 1) {
                masterSeedProvider.withSessionOwner<RawCtapResult>(
                    initialize = any(),
                    ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                    operation = any(),
                )
                getAssertionHandler.handleRaw(any(), any())
            }
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute routes GetInfo to responseBuilder without seed ownership`() =
        runTest {
            val request = own(byteArrayOf(CMD_GET_INFO))
            val dummyInfo = mockk<AuthenticatorInfo>()
            val expectedResponse = expectedCborResponse()

            coEvery { authenticatorInfoProvider.getAuthenticatorInfo() } returns dummyInfo
            every { responseBuilder.buildGetInfoRawPayload(dummyInfo, any()) } returns expectedResponse

            val result = rawCtapOperation.execute(request)
            own(result.responseBytes)

            assertEquals(CTAP2_OK, result.status)
            assertTrue(result.isSuccess)
            assertFalse(result.presenceRequired)
            assertContentEquals(expectedResponse, result.responseBytes)

            coVerify(exactly = 0) {
                masterSeedProvider.withSessionOwner<RawCtapResult>(any(), any(), any())
            }
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute returns error on unknown command byte`() =
        runTest {
            val request = own(byteArrayOf(CMD_UNKNOWN, 0x01, 0x02))

            val result = rawCtapOperation.execute(request)
            own(result.responseBytes)

            assertEquals(CTAP2_ERR_INVALID_CMD, result.status)
            assertFalse(result.isSuccess)
            assertEquals(1, result.responseBytes.size)
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute returns error on empty request`() =
        runTest {
            val request = own(ByteArray(0))

            val result = rawCtapOperation.execute(request)
            own(result.responseBytes)

            assertEquals(CTAP2_ERR_INVALID_CMD, result.status)
            assertFalse(result.isSuccess)
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `execute propagates keepalive callbacks during execution`() =
        runTest {
            val request = own(byteArrayOf(CMD_MAKE_CREDENTIAL, 0x01))
            val keepaliveStatuses = mutableListOf<Byte>()

            coEvery {
                makeCredentialHandler.handleRaw(request, any())
            } answers {
                val callback = secondArg<((Byte) -> Unit)?>()
                callback?.invoke(STATUS_PROCESSING)
                callback?.invoke(STATUS_UPNEEDED)
                RawCtapResult(own(byteArrayOf(CTAP2_OK)), presenceRequired = true)
            }

            val result =
                rawCtapOperation.execute(request) { status: Byte ->
                    keepaliveStatuses.add(status)
                }
            own(result.responseBytes)

            assertTrue(result.isSuccess)
            assertEquals(listOf(STATUS_PROCESSING, STATUS_UPNEEDED), keepaliveStatuses)
        }

    @Test
    @Req("FR-BLE-080", "TR-BLE-030")
    fun `classic HID framing regression verifies raw response can be encoded and decoded via HID`() =
        runTest {
            val cid = own(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val rawPayload = expectedCborResponse()

            val hidMessage =
                CtapHidMessage(
                    channelId = cid,
                    command = 0x10, // CTAPHID_CBOR
                    payload = rawPayload,
                )

            val encodedPackets = hidReportParser.encodeResponse(hidMessage)
            encodedPackets.forEach(::own)
            assertTrue(encodedPackets.isNotEmpty(), "HID packets must not be empty")
            assertEquals(62, encodedPackets.first().size, "HID packet must be 62 bytes")

            val parsedResult = hidReportParser.processReport(encodedPackets.first())
            val parsedMessage = parsedResult.getOrThrow()
            assertNotNull(parsedMessage)
            own(parsedMessage.channelId)
            own(parsedMessage.payload)
            assertContentEquals(cid, parsedMessage.channelId)
            assertEquals(0x10, parsedMessage.command)
            assertEquals(rawPayload.size, parsedMessage.payload.size)
            assertContentEquals(rawPayload, parsedMessage.payload)
        }

    @Test
    fun `get info failure diagnostic omits provider exception content`() =
        runTest {
            val marker = "synthetic-provider-secret-marker"
            coEvery { authenticatorInfoProvider.getAuthenticatorInfo() } throws
                Fido2Exception.CryptographicException(marker)
            val diagnostics = CapturedDiagnostics()
            try {
                val result = rawCtapOperation.execute(own(byteArrayOf(CMD_GET_INFO)))
                own(result.responseBytes)
                assertEquals(0x30.toByte(), result.status)
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(marker)
            } finally {
                diagnostics.close()
            }
        }
}
