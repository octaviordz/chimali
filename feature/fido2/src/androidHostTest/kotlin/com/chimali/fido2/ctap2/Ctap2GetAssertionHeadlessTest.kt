package com.chimali.fido2.ctap2

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.testing.stubSensitiveRequestCompatibility
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class Ctap2GetAssertionHeadlessTest {
    private val ownedBuffers = mutableListOf<ByteArray>()

    private fun own(buffer: ByteArray): ByteArray {
        ownedBuffers += buffer
        return buffer
    }

    private val getAssertionUseCase = mockk<GetAssertionUseCase>()
    private val cborCodec = mockk<CborCodec>()
    private val hmacSecretProcessor = mockk<HmacSecretProcessor>()
    private val prfKeyDerivation = mockk<PrfKeyDerivation>()
    private val hidReportParser = mockk<HidReportParser>()
    private val uiEventBus = mockk<Fido2UiEventBus>(relaxed = true)
    private val ceremonyLock = mockk<CeremonyLock>(relaxed = true)

    private val handler =
        Ctap2GetAssertionHandler(
            cborCodec = cborCodec,
            hmacSecretProcessor = hmacSecretProcessor,
            prfKeyDerivation = prfKeyDerivation,
            hidReportParser = hidReportParser,
            uiEventBus = uiEventBus,
            getAssertionUseCase = getAssertionUseCase,
            ceremonyLock = ceremonyLock,
        )

    @BeforeTest
    fun setUp() {
        cborCodec.stubSensitiveRequestCompatibility()
    }

    @AfterTest
    fun tearDown() {
        ownedBuffers.forEach { it.fill(0) }
        ownedBuffers.clear()
        clearMocks(
            getAssertionUseCase,
            cborCodec,
            hmacSecretProcessor,
            prfKeyDerivation,
            hidReportParser,
            uiEventBus,
            ceremonyLock,
        )
    }

    @Test
    fun fixtureCleanup_erasesOwnedAssertionBuffers() {
        val fixture = own(byteArrayOf(0x2A))

        tearDown()

        assertTrue(fixture.all { it == 0.toByte() })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `handle returns success via headless path when uv is preferred and one candidate exists`() =
        runTest {
            // Arrange
            val cid = own(byteArrayOf(1, 2, 3, 4))
            val requestBytes = own(byteArrayOf(0x01, 0x02)) // Dummy CBOR
            val clientDataHash = own(ByteArray(32))
            val candidate =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 1
                }
            val assertionObject = mockk<AssertionObject>(relaxed = true)

            every { cborCodec.decodeFromFido2Format(requestBytes) } returns
                mapOf("1" to "webauthn.io", "2" to clientDataHash)
            every { ceremonyLock.tryLock() } returns true
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(candidate)
            coEvery { getAssertionUseCase.invokeHeadless(any(), listOf(candidate)) } returns
                Outcome.Success(assertionObject)
            coEvery { hmacSecretProcessor.isPresent(any()) } returns false
            coEvery { cborCodec.encodeToFido2Format(any<Map<String, Any>>()) } returns
                own(byteArrayOf(0x00)) // Dummy response
            every { hidReportParser.encodeResponse(any()) } returns listOf(own(byteArrayOf(0x00)))

            // Act
            val result = handler.handle(cid, requestBytes)
            result.forEach(::own)

            // Assert
            assertEquals(1, result.size)
            coVerify(exactly = 1) { getAssertionUseCase.invokeHeadless(any(), listOf(candidate)) }
            verify(exactly = 0) { uiEventBus.dispatch(any()) }
        }
}
