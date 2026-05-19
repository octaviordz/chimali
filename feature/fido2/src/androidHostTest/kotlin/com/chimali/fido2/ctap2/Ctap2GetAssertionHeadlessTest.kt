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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class Ctap2GetAssertionHeadlessTest {
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

    @Test
    fun `handle returns success via headless path when uv is preferred and one candidate exists`() =
        runTest {
            // Arrange
            val cid = byteArrayOf(1, 2, 3, 4)
            val requestBytes = byteArrayOf(0x01, 0x02) // Dummy CBOR
            val candidate = mockk<CredentialSummary>()
            val assertionObject = mockk<AssertionObject>(relaxed = true)

            every { cborCodec.decodeFromFido2Format(requestBytes) } returns
                mapOf("1" to "webauthn.io", "2" to ByteArray(32))
            every { ceremonyLock.tryLock() } returns true
            coEvery { getAssertionUseCase.findCandidateSummaries(any()) } returns listOf(candidate)
            coEvery { getAssertionUseCase(any()) } returns Outcome.Success(assertionObject)
            coEvery { hmacSecretProcessor.isPresent(any()) } returns false
            coEvery { cborCodec.encodeToFido2Format(any<Map<String, Any>>()) } returns
                byteArrayOf(0x00) // Dummy response
            every { hidReportParser.encodeResponse(any()) } returns listOf(byteArrayOf(0x00))

            // Act
            val result = handler.handle(cid, requestBytes)

            // Assert
            assertEquals(1, result.size)
            verify(exactly = 0) { uiEventBus.dispatch(any()) }
        }
}
