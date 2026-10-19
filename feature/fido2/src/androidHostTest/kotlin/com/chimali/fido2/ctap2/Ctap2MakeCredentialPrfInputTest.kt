package com.chimali.fido2.ctap2

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Req("NFR-SEC-041", "TR-AUTH-031")
class Ctap2MakeCredentialPrfInputTest {
    private val reportParser = mockk<HidReportParser>(relaxed = true)
    private val uiEventBus = mockk<Fido2UiEventBus>(relaxed = true)
    private val ceremonyLock = mockk<CeremonyLock>(relaxed = true)
    private val channelId = byteArrayOf(1, 2, 3, 4)

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @ParameterizedTest
    @ValueSource(strings = ["one-salt-failure", "two-salt-cancellation"])
    fun `registration does not derive unused PRF output and erases decoded salts on terminal paths`(mode: String) =
        runTest {
            val realCodec = CborCodec()
            val decodedByteStrings = mutableListOf<ByteArray>()
            val decodedTextOwners = mutableListOf<SensitiveCredentialText>()
            realCodec.onDecodedByteStringForTest = decodedByteStrings::add
            realCodec.onDecodedTextOwnerForTest = decodedTextOwners::add
            val realHandler = Ctap2MakeCredentialHandler(realCodec, reportParser, uiEventBus, ceremonyLock)
            val hmacSecret =
                buildMap<Long, ByteArray> {
                    put(1L, ByteArray(32) { 0x31 })
                    if (mode.startsWith("two")) put(2L, ByteArray(32) { 0x52 })
                }
            val request =
                mapOf(
                    "1" to ByteArray(32),
                    "2" to mapOf("id" to "example.com", "name" to "Example"),
                    "3" to mapOf("id" to byteArrayOf(1), "name" to "user", "displayName" to "User"),
                    "4" to listOf(mapOf("alg" to -7L)),
                    "6" to mapOf("credProtect" to 2L, "hmac-secret" to hmacSecret),
                )
            val encoded = realCodec.encodeToFido2Format(request)
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            every { uiEventBus.dispatch(any()) } answers {
                val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                assertEquals(mapOf("credProtect" to 2), event.options.extensions)
                if (mode.endsWith("cancellation")) {
                    event.deferred.cancel(CancellationException("synthetic T156 cancellation"))
                } else {
                    event.deferred.complete(Outcome.Error(DomainError.OperationDenied("synthetic T156 failure")))
                }
                Unit
            }

            if (mode.endsWith("cancellation")) {
                assertFailsWith<CancellationException> {
                    realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))
                }
            } else {
                realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))
            }

            assertTrue(decodedByteStrings.isNotEmpty())
            decodedByteStrings.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
            assertTrue(decodedTextOwners.all { owner -> owner.copyCharacters().all { it == '\u0000' } })
        }
}
