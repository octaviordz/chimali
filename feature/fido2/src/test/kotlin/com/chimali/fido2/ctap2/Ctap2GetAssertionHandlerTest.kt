package com.chimali.fido2.ctap2

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * T050 — Unit tests for [Ctap2GetAssertionHandler] headless fast-path.
 * Fulfills FR-FIDO2-016.
 */
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
    }

    private val cid = byteArrayOf(1, 2, 3, 4)
    private val requestBytes = byteArrayOf(0x01) // Dummy CBOR

    @Test
    fun `handle executes headless fast-path when uv is PREFERRED and exactly one credential matches`() =
        runTest {
            // Setup
            val summary = mockk<CredentialSummary>()
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
            coEvery { getAssertionUseCase(any()) } returns Outcome.Success(assertion)

            every { cborCodec.encodeToFido2Format(any()) } returns byteArrayOf(0x00)
            every { hidReportParser.encodeResponse(any()) } returns emptyList()

            // Act
            handler.handle(cid, requestBytes)

            // Assert
            // Verify that AuthenticationRequested was NEVER dispatched
            coVerify(exactly = 0) { uiEventBus.dispatch(any<Fido2UiEvent.AuthenticationRequested>()) }
            // Verify that use case was called directly
            coVerify(exactly = 1) { getAssertionUseCase(any()) }
        }

    @Test
    fun `handle dispatches UI event when multiple credentials match`() =
        runTest {
            // Setup
            val summary1 = mockk<CredentialSummary>()
            val summary2 = mockk<CredentialSummary>()

            val assertion = mockk<AssertionObject>(relaxed = true)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to "example.com", "2" to ByteArray(32))
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(summary1, summary2)

            coEvery { uiEventBus.dispatch(any()) } answers {
                val event = it.invocation.args[0] as Fido2UiEvent.AuthenticationRequested
                event.deferred.complete(Outcome.Success(assertion))
            }

            // Act
            handler.handle(cid, requestBytes)

            // Assert
            coVerify(exactly = 1) { uiEventBus.dispatch(any<Fido2UiEvent.AuthenticationRequested>()) }
        }
}
