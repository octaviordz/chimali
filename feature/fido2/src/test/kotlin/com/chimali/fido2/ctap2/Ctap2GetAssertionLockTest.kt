package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class Ctap2GetAssertionLockTest {
    private val getAssertionUseCase = mockk<GetAssertionUseCase>()
    private val cborCodec = mockk<CborCodec>()
    private val hmacSecretProcessor = mockk<HmacSecretProcessor>()
    private val prfKeyDerivation = mockk<PrfKeyDerivation>()
    private val hidReportParser = mockk<HidReportParser>()
    private val uiEventBus = mockk<Fido2UiEventBus>(relaxed = true)
    private val ceremonyLock = mockk<CeremonyLock>()

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
    fun `handle returns CHANNEL_BUSY when lock is already held`() =
        runTest {
            // Arrange
            val cid = byteArrayOf(1, 2, 3, 4)
            val requestBytes = byteArrayOf(0x01, 0x02)

            every { ceremonyLock.tryLock() } returns false
            every { hidReportParser.encodeResponse(any()) } returns listOf(byteArrayOf(0x06)) // 0x06 = CHANNEL_BUSY

            // Act
            val result = handler.handle(cid, requestBytes)

            // Assert
            assertEquals(1, result.size)
            assertEquals(0x06.toByte(), result[0][0])
            coVerify(exactly = 0) { getAssertionUseCase.findCandidateSummaries(any()) }
        }
}
