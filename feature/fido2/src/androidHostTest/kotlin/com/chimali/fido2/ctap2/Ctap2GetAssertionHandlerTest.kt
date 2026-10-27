package com.chimali.fido2.ctap2

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PrfExtensionOutput
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.stubSensitiveRequestCompatibility
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * T050 — Unit tests for [Ctap2GetAssertionHandler] headless fast-path.
 * Fulfills FR-FIDO2-016.
 */
@Req("NFR-PERF-030", "NFR-SEC-041")
class Ctap2GetAssertionHandlerTest {
    private val cborCodec: CborCodec = mockk()
    private val hmacSecretProcessor: HmacSecretProcessor = mockk()
    private val prfKeyDerivation: PrfKeyDerivation = mockk()
    private val hidReportParser: HidReportParser = mockk()
    private val uiEventBus: Fido2UiEventBus = mockk(relaxed = true)
    private val getAssertionUseCase: GetAssertionUseCase = mockk()
    private val ceremonyLock: com.chimali.fido2.domain.service.CeremonyLock = mockk(relaxed = true)

    private val handler =
        Ctap2GetAssertionHandler(
            cborCodec,
            hmacSecretProcessor,
            prfKeyDerivation,
            hidReportParser,
            uiEventBus,
            getAssertionUseCase,
            ceremonyLock,
        )

    @BeforeTest
    fun setup() {
        every { ceremonyLock.tryLock() } returns true
        cborCodec.stubSensitiveRequestCompatibility()
    }

    private val cid = byteArrayOf(1, 2, 3, 4)
    private val requestBytes = byteArrayOf(0x01) // Dummy CBOR

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `no-credential response preserves protocol status without logging RP association`() =
        runTest {
            val marker = "synthetic-private-rp"
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf("1" to "$marker.example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns emptyList()
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }
            CapturedDiagnostics().use { logs ->
                assertEquals(0x2E.toByte(), handler.handle(cid, requestBytes).single().first())
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
            coVerify(exactly = 0) { getAssertionUseCase.invokeHeadless(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `decodes allow-list identifiers and required user verification from CTAP request`() =
        runTest {
            val encodedId = testCredentialIdFromLabel("ctap-allow-list").encoded
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to "example.com",
                    "2" to ByteArray(32),
                    "3" to listOf(mapOf("id" to encodedId)),
                    "4" to emptyMap<String, Any>(),
                    "5" to mapOf("uv" to true, "up" to false),
                )
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns emptyList()
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            assertEquals(0x2E.toByte(), handler.handle(cid, requestBytes).single().first())
            coVerify {
                getAssertionUseCase.findCandidateSummaries(
                    match { options ->
                        options.allowCredentials?.single()?.id == CredentialId.fromEncoded(encodedId) &&
                            options.userVerification == UserVerificationRequirement.REQUIRED
                    },
                )
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `decodes legacy sensitive text request values and erases their owners`() =
        runTest {
            val expectedHash = ByteArray(32) { (it + 1).toByte() }
            val rpIdOwner = SensitiveCredentialText.copyOf("example.com")
            val encodedHash = Base64.getEncoder().encodeToString(expectedHash)
            val hashOwner = SensitiveCredentialText.copyOf(encodedHash)
            var observedRpId: String? = null
            var observedHash: ByteArray? = null
            every { cborCodec.decodeSensitiveRequest(any()) } returns
                mapOf("1" to rpIdOwner, "2" to hashOwner)
            every { cborCodec.eraseDecodedRequestValues(any()) } answers {
                (firstArg<Any?>() as? Map<*, *>)
                    ?.values
                    ?.filterIsInstance<SensitiveCredentialText>()
                    ?.forEach(SensitiveCredentialText::erase)
            }
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } coAnswers {
                val options = firstArg<GetAssertionOptions>()
                observedRpId = options.rpId.value
                observedHash = options.clientDataHash.copyOf()
                emptyList()
            }
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            assertEquals(0x2E.toByte(), handler.handle(cid, requestBytes).single().first())
            assertEquals("example.com", observedRpId)
            assertContentEquals(expectedHash, observedHash)
            assertContentEquals(CharArray(rpIdOwner.length), rpIdOwner.copyCharacters())
            assertContentEquals(CharArray(hashOwner.length), hashOwner.copyCharacters())
            observedHash?.fill(0)
            expectedHash.fill(0)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `rejects malformed CTAP user-verification option with invalid-CBOR status`() =
        runTest {
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to "example.com",
                    "2" to ByteArray(32),
                    "5" to mapOf("uv" to "true"),
                )
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            assertEquals(0x12.toByte(), handler.handle(cid, requestBytes).single().first())
            coVerify(exactly = 0) { getAssertionUseCase.findCandidateSummaries(any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `rejects malformed allow-list containers and descriptors before credential lookup`() =
        runTest {
            val malformedRequests =
                listOf(
                    mapOf("1" to "example.com", "2" to ByteArray(32), "3" to "not-an-array"),
                    mapOf("1" to "example.com", "2" to ByteArray(32), "3" to listOf("not-a-map")),
                    mapOf(
                        "1" to "example.com",
                        "2" to ByteArray(32),
                        "3" to listOf(mapOf("id" to 42)),
                    ),
                )
            every { cborCodec.decodeFromFido2Format(any()) } returnsMany malformedRequests
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            val statuses = List(malformedRequests.size) { handler.handle(cid, requestBytes).single().first() }

            assertEquals(List(malformedRequests.size) { 0x12.toByte() }, statuses)
            coVerify(exactly = 0) { getAssertionUseCase.findCandidateSummaries(any()) }
            coVerify(exactly = malformedRequests.size) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `busy ceremony rejects request before decoding or credential lookup`() =
        runTest {
            every { ceremonyLock.tryLock() } returns false
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            assertEquals(0x06.toByte(), handler.handle(cid, requestBytes).single().first())
            coVerify(exactly = 0) { cborCodec.decodeSensitiveRequest(any()) }
            coVerify(exactly = 0) { getAssertionUseCase.findCandidateSummaries(any()) }
            coVerify(exactly = 0) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `maps domain failures to their protocol status and fail-closed fallback`() =
        runTest {
            val summary =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 1
                }
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf("1" to "example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary)
            coEvery { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) } returnsMany
                listOf(
                    Outcome.Error(DomainError.NotFound("synthetic missing credential")),
                    Outcome.Error(DomainError.OperationDenied("synthetic consent rejection")),
                    Outcome.Error(DomainError.ValidationError("synthetic unexpected failure")),
                )
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            val statuses = List(3) { handler.handle(cid, requestBytes).single().first() }

            assertEquals(listOf(0x2E.toByte(), 0x29.toByte(), 0x7F.toByte()), statuses)
            coVerify(exactly = 3) { summary.eraseSensitiveText() }
            coVerify(exactly = 3) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `maps decoder exceptions to invalid-CBOR or processing statuses`() =
        runTest {
            val exceptions =
                listOf(
                    Fido2Exception.InvalidFormatException("synthetic malformed CBOR"),
                    Fido2Exception.InvalidParameter("synthetic invalid parameter"),
                    Fido2Exception.InvalidFormatException("synthetic unexpected format"),
                    java.io.IOException("synthetic transport failure"),
                    IllegalArgumentException("synthetic argument failure"),
                    IllegalStateException("synthetic state failure"),
                )
            every { cborCodec.decodeSensitiveRequest(any()) } throwsMany exceptions
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload)
            }

            val statuses = List(exceptions.size) { handler.handle(cid, requestBytes).single().first() }

            assertEquals(
                listOf(0x12.toByte(), 0x12.toByte(), 0x12.toByte(), 0x7F.toByte(), 0x7F.toByte(), 0x7F.toByte()),
                statuses,
            )
            coVerify(exactly = exceptions.size) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `encodes credential user entity and matching credential count in assertion response`() =
        runTest {
            val summary =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 1
                }
            val credential =
                PublicKeyCredentialDescriptor.create(
                    id = testCredentialIdFromLabel("ctap-selected-credential"),
                )
            val user =
                mockk<PublicKeyCredentialUserEntity> {
                    every { id } returns UserId("synthetic-user")
                    every { name } returns "synthetic-name"
                    every { displayName } returns ""
                }
            val assertion =
                AssertionObject(
                    credential = credential,
                    authData = ByteArray(37) { 0x21 },
                    signature = byteArrayOf(0x31, 0x32),
                    user = user,
                    numberOfCredentials = 2,
                )
            val responseFields = mutableMapOf<String, Any>()
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf("1" to "example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary)
            coEvery { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) } returns Outcome.Success(assertion)
            every { cborCodec.encodeToFido2Format(any()) } answers {
                responseFields.putAll(firstArg<Map<String, Any>>())
                byteArrayOf(0x44)
            }
            every { hidReportParser.encodeResponse(any()) } answers {
                listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload.copyOf())
            }

            val response = handler.handle(cid, requestBytes).single()

            assertEquals(setOf("1", "2", "3", "4", "5"), responseFields.keys)
            assertEquals(2, responseFields["5"])
            assertEquals("public-key", (responseFields["1"] as Map<*, *>)["type"])
            assertEquals("synthetic-user", (responseFields["4"] as Map<*, *>)["id"])
            assertEquals("synthetic-name", (responseFields["4"] as Map<*, *>)["displayName"])
            assertEquals(0x00.toByte(), response.first())
            response.fill(0)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `handle executes headless fast-path when uv is PREFERRED and exactly one credential matches`() =
        runTest {
            // Setup
            val summary =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 1
                }
            val assertion =
                mockk<AssertionObject> {
                    every { credential } returns null
                    every { credentialId } returns "id"
                    every { authData } returns byteArrayOf(1)
                    every { signature } returns byteArrayOf(2)
                    every { user } returns null
                    every { numberOfCredentials } returns 1
                }

            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to "example.com", "2" to ByteArray(32))
            // Note: we need to mock decodeOptions logic or just mock the whole options decoding
            // In this test, we'll assume decodeOptions returns our mocked options.
            // Actually, Ctap2GetAssertionHandler calls decodeOptions internally.
            // So we should mock the return of cborCodec.decodeFromFido2Format to produce what decodeOptions expects.

            // Mocking the UseCase methods
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary)
            coEvery { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) } returns Outcome.Success(assertion)

            every { cborCodec.encodeToFido2Format(any()) } returns byteArrayOf(0x00)
            every { hidReportParser.encodeResponse(any()) } returns emptyList()

            // Act
            handler.handle(cid, requestBytes)

            // Assert
            // Verify that AuthenticationRequested was NEVER dispatched
            coVerify(exactly = 0) { uiEventBus.dispatch(any<Fido2UiEvent.AuthenticationRequested>()) }
            // The prefetched snapshot avoids a redundant credential summary query.
            coVerify(exactly = 1) { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `handle dispatches UI event when multiple credentials match`() =
        runTest {
            // Setup
            val summary1 = mockk<CredentialSummary> { every { eraseSensitiveText() } returns Unit }
            val summary2 = mockk<CredentialSummary> { every { eraseSensitiveText() } returns Unit }

            val assertion = mockk<AssertionObject>(relaxed = true)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to "example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary1, summary2)

            coEvery { uiEventBus.dispatch(any()) } answers {
                val event = it.invocation.args[0] as Fido2UiEvent.AuthenticationRequested
                event.deferred.complete(Outcome.Success(assertion))
            }

            every { cborCodec.encodeToFido2Format(any()) } returns byteArrayOf(0x00)
            every { hidReportParser.encodeResponse(any()) } returns emptyList()

            // Act
            handler.handle(cid, requestBytes)

            // Assert
            coVerify(exactly = 1) { uiEventBus.dispatch(any<Fido2UiEvent.AuthenticationRequested>()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `handle routes a single credProtect UV-required credential through the UI prompt`() =
        runTest {
            val protectedSummary =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 3
                }
            val assertion = mockk<AssertionObject>(relaxed = true)
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf("1" to "example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(protectedSummary)
            every { uiEventBus.dispatch(any()) } answers {
                val event = it.invocation.args[0] as Fido2UiEvent.AuthenticationRequested
                event.deferred.complete(Outcome.Success(assertion))
            }
            every { cborCodec.encodeToFido2Format(any()) } returns byteArrayOf(0x00)
            every { hidReportParser.encodeResponse(any()) } returns emptyList()

            handler.handle(cid, requestBytes)

            coVerify(exactly = 1) { uiEventBus.dispatch(any<Fido2UiEvent.AuthenticationRequested>()) }
            coVerify(exactly = 0) { getAssertionUseCase.invokeHeadless(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `handle clears derived outputs after framing while returned packet retains authorized bytes`() =
        runTest {
            val firstOutput = ByteArray(32) { 0x31 }
            val secondOutput = ByteArray(32) { 0x52 }
            val firstExpected = firstOutput.copyOf()
            val secondExpected = secondOutput.copyOf()
            val authData = ByteArray(37) { 0x20 }
            authData[32] = 0x01
            val signature = ByteArray(64) { 0x63 }
            val summary = configurePrfResponse(firstOutput, secondOutput, authData, signature)
            val packet = handler.handle(cid, requestBytes).single()

            assertTrue(firstOutput.all { it == 0.toByte() })
            assertTrue(secondOutput.all { it == 0.toByte() })
            assertTrue(authData.all { it == 0.toByte() })
            assertTrue(signature.all { it == 0.toByte() })
            assertContainsBytes(packet, firstExpected)
            assertContainsBytes(packet, secondExpected)
            coVerify(exactly = 1) { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) }
            packet.fill(0)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `handle clears PRF and assertion buffers when response framing throws`() =
        runTest {
            val firstOutput = ByteArray(32) { 0x14 }
            val secondOutput = ByteArray(32) { 0x27 }
            val authData = ByteArray(37) { 0x35 }
            val signature = ByteArray(64) { 0x48 }
            configurePrfResponse(firstOutput, secondOutput, authData, signature)
            var firstFrame = true
            every { hidReportParser.encodeResponse(any()) } answers {
                val payload = firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload
                if (firstFrame) {
                    firstFrame = false
                    error("synthetic frame failure")
                }
                listOf(payload.copyOf())
            }

            handler.handle(cid, requestBytes)

            assertTrue(firstOutput.all { it == 0.toByte() })
            assertTrue(secondOutput.all { it == 0.toByte() })
            assertTrue(authData.all { it == 0.toByte() })
            assertTrue(signature.all { it == 0.toByte() })
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `typed hmac-secret extension is omitted when credential seed is unavailable`() =
        runTest {
            val authData = ByteArray(37) { 0x24 }
            authData[32] = 0x01
            val signature = ByteArray(64) { 0x35 }
            val firstOutput = ByteArray(32) { 0x46 }
            val secondOutput = ByteArray(32) { 0x57 }
            configurePrfResponse(firstOutput, secondOutput, authData, signature, derivedOutput = null)
            var encodedKeys: Set<String>? = null
            every { cborCodec.encodeToFido2Format(any()) } answers {
                val fields = firstArg<Map<String, Any>>()
                encodedKeys = fields.keys
                (fields["2"] as ByteArray).copyOf()
            }

            val packet = handler.handle(cid, requestBytes).single()

            assertEquals(setOf("2", "3"), encodedKeys)
            assertTrue(firstOutput.all { it == 0x46.toByte() })
            assertTrue(secondOutput.all { it == 0x57.toByte() })
            coVerify(exactly = 1) { prfKeyDerivation.deriveAll(any(), "synthetic-credential") }
            coVerify(exactly = 0) { hmacSecretProcessor.buildAuthDataExtensions(any()) }
            packet.fill(0)
            firstOutput.fill(0)
            secondOutput.fill(0)
            authData.fill(0)
            signature.fill(0)
        }

    private fun configurePrfResponse(
        firstOutput: ByteArray,
        secondOutput: ByteArray,
        assertionAuthData: ByteArray,
        assertionSignature: ByteArray,
        derivedOutput: PrfExtensionOutput? = PrfExtensionOutput(firstOutput, secondOutput),
    ): CredentialSummary {
        val salt1 = ByteArray(32) { 0x11 }
        val salt2 = ByteArray(32) { 0x22 }
        val summary =
            mockk<CredentialSummary> {
                every { eraseSensitiveText() } returns Unit
                every { credProtectPolicy } returns 1
            }
        val assertion =
            mockk<AssertionObject> {
                every { credential } returns null
                every { credentialId } returns "synthetic-credential"
                every { authData } returns assertionAuthData
                every { signature } returns assertionSignature
                every { user } returns null
                every { numberOfCredentials } returns 1
            }

        every { cborCodec.decodeFromFido2Format(any()) } returns
            mapOf(
                "1" to "example.com",
                "2" to ByteArray(32),
                "4" to mapOf(HmacSecretProcessor.EXTENSION_KEY to mapOf(1 to salt1, 2 to salt2)),
            )
        coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary)
        coEvery { getAssertionUseCase.invokeHeadless(any(), listOf(summary)) } returns Outcome.Success(assertion)
        coEvery { prfKeyDerivation.deriveAll(any(), "synthetic-credential") } returns derivedOutput
        every { hmacSecretProcessor.buildAuthDataExtensions(any()) } answers {
            mapOf(HmacSecretProcessor.EXTENSION_KEY to firstArg())
        }
        every { cborCodec.encodeToFido2Format(any()) } answers {
            val values = firstArg<Map<String, Any>>()
            if (HmacSecretProcessor.EXTENSION_KEY in values) {
                val output = values[HmacSecretProcessor.EXTENSION_KEY] as ByteArray
                output.copyOf()
            } else {
                (values["2"] as ByteArray).copyOf()
            }
        }
        every { hidReportParser.encodeResponse(any()) } answers {
            listOf(firstArg<com.chimali.fido2.bluetooth.CtapHidMessage>().payload.copyOf())
        }
        return summary
    }

    private fun assertContainsBytes(
        haystack: ByteArray,
        needle: ByteArray,
    ) {
        assertTrue(
            haystack.toList().windowed(needle.size).any { it == needle.toList() },
            "Framed response must preserve the derived output until the caller consumes it",
        )
    }
}
