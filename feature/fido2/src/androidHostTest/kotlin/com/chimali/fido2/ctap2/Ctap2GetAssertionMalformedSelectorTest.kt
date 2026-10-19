package com.chimali.fido2.ctap2

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

@Req("NFR-SEC-041", "TR-AUTH-031")
class Ctap2GetAssertionMalformedSelectorTest {
    private val codec = CborCodec()
    private val parser = mockk<HidReportParser>()
    private val ui = mockk<Fido2UiEventBus>(relaxed = true)
    private val useCase = mockk<GetAssertionUseCase>()
    private val lock = mockk<CeremonyLock>(relaxed = true)
    private val handler =
        Ctap2GetAssertionHandler(
            codec,
            mockk<HmacSecretProcessor>(),
            mockk<PrfKeyDerivation>(),
            parser,
            ui,
            useCase,
            lock,
        )
    private val cid = byteArrayOf(1, 2, 3, 4)

    private suspend fun send(parameters: Map<String, Any>): Byte =
        handler.handle(cid, codec.encodeToFido2Format(parameters)).single().first()

    private fun request(vararg entries: Pair<String, Any>): Map<String, Any> =
        mapOf("1" to "example.com", "2" to ByteArray(32)) + entries

    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `malformed present allow list and UV option reject before candidate lookup`() =
        runTest {
            every { lock.tryLock() } returns true
            every { parser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            val decodedIds = mutableListOf<ByteArray>()
            codec.onDecodedByteStringForTest = decodedIds::add
            val malformedAllowLists: List<Any> =
                listOf(
                    CborCodec.SimpleValue.NULL,
                    CborCodec.SimpleValue.UNDEFINED,
                    "not-an-array",
                    listOf("not-a-descriptor"),
                    listOf(mapOf("id" to "%%%")),
                    listOf(mapOf("id" to ByteArray(16)), "bad-entry"),
                    listOf(mapOf("id" to ByteArray(15))),
                )
            malformedAllowLists.forEach { selector ->
                assertEquals(0x12.toByte(), send(request("3" to selector)))
                decodedIds.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
                decodedIds.clear()
            }

            val malformedOptions: List<Any> =
                listOf(
                    CborCodec.SimpleValue.NULL,
                    CborCodec.SimpleValue.UNDEFINED,
                    1L,
                    mapOf("uv" to "true"),
                    mapOf("uv" to CborCodec.SimpleValue.NULL),
                    mapOf("uv" to CborCodec.SimpleValue.UNDEFINED),
                )
            malformedOptions.forEach { options ->
                assertEquals(0x12.toByte(), send(request("5" to options)))
            }
            coVerify(exactly = 0) { useCase.findCandidateSummaries(any()) }
            verify(exactly = 0) { ui.dispatch(any()) }
        }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `valid absent empty and byte-string selectors preserve their distinct meanings`() =
        runTest {
            every { lock.tryLock() } returns true
            every { parser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            val optionSlot = slot<GetAssertionOptions>()
            coEvery { useCase.findCandidateSummaries(capture(optionSlot)) } returns emptyList()

            assertEquals(0x2E.toByte(), send(request()))
            assertEquals(null, optionSlot.captured.allowCredentials)
            assertEquals(0x2E.toByte(), send(request("3" to emptyList<Any>())))
            assertEquals(emptyList(), optionSlot.captured.allowCredentials)
            val credentialBytes = ByteArray(16) { it.toByte() }
            assertEquals(
                0x2E.toByte(),
                send(request("3" to listOf(mapOf("id" to credentialBytes)))),
            )
            assertEquals(1, optionSlot.captured.allowCredentials?.size)
            assertEquals(
                credentialBytes.toList(),
                optionSlot.captured.allowCredentials
                    ?.single()
                    ?.id
                    ?.toByteArray()
                    ?.toList(),
            )
            assertEquals(0x2E.toByte(), send(request("5" to mapOf("uv" to true))))
            assertEquals(UserVerificationRequirement.REQUIRED, optionSlot.captured.userVerification)
            assertEquals(0x2E.toByte(), send(request("5" to mapOf("uv" to false))))
            assertEquals(UserVerificationRequirement.PREFERRED, optionSlot.captured.userVerification)
        }
}
