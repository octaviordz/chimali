package com.chimali.fido2.data.transport

import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfo
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CtapHidCommandDispatcherPrivacyTest {
    @Test
    fun `channel assignment diagnostics omit the generated channel identifier`() =
        runTest {
            val markerCid = byteArrayOf(0x21, 0x43, 0x65, 0x07)
            val marker = "21436507"
            val dispatcher =
                CtapHidCommandDispatcher(
                    hidReportParser = HidReportParser(),
                    responseBuilder = mockk(relaxed = true),
                    getAssertionHandler = mockk<Ctap2GetAssertionHandler>(relaxed = true),
                    authenticatorInfoProvider = mockk<AuthenticatorInfoProvider>(relaxed = true),
                    cborRequestOwner = mockk<CtapHidRequestOwner>(relaxed = true),
                    u2fApduCommandHandler = mockk<U2fApduCommandHandler>(relaxed = true),
                    isCurrentSession = { true },
                    isChannelRegistered = { _, _ -> true },
                    allocateCid = { markerCid.copyOf() },
                    sendPackets = { _, _ -> },
                )
            val message =
                CtapHidMessage(
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                    CTAPHID_INIT,
                    ByteArray(8),
                )

            CapturedDiagnostics().use { diagnostics ->
                dispatcher.dispatch(message, 1L)

                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(marker)
            }
            markerCid.fill(0)
        }

    @Test
    fun `U2F report diagnostics omit the channel identifier`() =
        runTest {
            val markerCid = byteArrayOf(0x21, 0x43, 0x65, 0x07)
            val marker = "21436507"
            val payload = byteArrayOf(0x00)
            val handler =
                U2fApduCommandHandler(
                    hidReportParser = HidReportParser(),
                    responseBuilder = mockk(relaxed = true),
                    enqueuePackets = { _, _ -> },
                    launchCborOperation = { _, _ -> },
                )
            try {
                CapturedDiagnostics().use { diagnostics ->
                    handler.handle(CtapHidMessage(markerCid, CTAPHID_MSG, payload), 1L)

                    assertTrue(diagnostics.size > 0)
                    diagnostics.assertNoSensitiveValues(marker)
                }
            } finally {
                markerCid.fill(0)
                payload.fill(0)
            }
        }

    @Test
    fun `get info diagnostics do not expose response packet bytes`() =
        runTest {
            val cid = byteArrayOf(1, 2, 3, 4)
            val info = mockk<AuthenticatorInfo>()
            val responsePacket = byteArrayOf(0x41, 0x42, 0x43)
            val provider = mockk<AuthenticatorInfoProvider>()
            val responseBuilder = mockk<Ctap2ResponseBuilder>()
            coEvery { provider.getAuthenticatorInfo() } returns info
            every { responseBuilder.getInfoResponse(cid, info) } returns listOf(responsePacket)
            val dispatcher =
                CtapHidCommandDispatcher(
                    hidReportParser = HidReportParser(),
                    responseBuilder = responseBuilder,
                    getAssertionHandler = mockk<Ctap2GetAssertionHandler>(relaxed = true),
                    authenticatorInfoProvider = provider,
                    cborRequestOwner = mockk<CtapHidRequestOwner>(relaxed = true),
                    u2fApduCommandHandler = mockk<U2fApduCommandHandler>(relaxed = true),
                    isCurrentSession = { true },
                    isChannelRegistered = { _, _ -> true },
                    allocateCid = { null },
                    sendPackets = { _, _ -> },
                )
            val diagnostics = CapturedDiagnostics()
            try {
                val result = dispatcher.handleGetInfo(cid)
                assertContentEquals(responsePacket, result.single())
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues("414243")
            } finally {
                diagnostics.close()
            }
        }

    @Test
    fun `get info failure diagnostics omit provider exception content`() =
        runTest {
            val cid = byteArrayOf(1, 2, 3, 4)
            val marker = "synthetic-private-get-info-provider-error"
            val errorPacket = byteArrayOf(9, 8, 7)
            val provider = mockk<AuthenticatorInfoProvider>()
            val responseBuilder = mockk<Ctap2ResponseBuilder>()
            coEvery { provider.getAuthenticatorInfo() } throws Fido2Exception.CryptographicException(marker)
            every { responseBuilder.errorResponse(cid, any()) } returns listOf(errorPacket)
            val dispatcher =
                CtapHidCommandDispatcher(
                    hidReportParser = HidReportParser(),
                    responseBuilder = responseBuilder,
                    getAssertionHandler = mockk<Ctap2GetAssertionHandler>(relaxed = true),
                    authenticatorInfoProvider = provider,
                    cborRequestOwner = mockk<CtapHidRequestOwner>(relaxed = true),
                    u2fApduCommandHandler = mockk<U2fApduCommandHandler>(relaxed = true),
                    isCurrentSession = { true },
                    isChannelRegistered = { _, _ -> true },
                    allocateCid = { null },
                    sendPackets = { _, _ -> },
                )
            val diagnostics = CapturedDiagnostics()
            try {
                assertContentEquals(errorPacket, dispatcher.handleGetInfo(cid).single())
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(marker)
            } finally {
                diagnostics.close()
            }
        }
}
