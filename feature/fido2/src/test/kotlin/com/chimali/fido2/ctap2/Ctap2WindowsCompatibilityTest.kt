package com.chimali.fido2.ctap2

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Ctap2WindowsCompatibilityTest {
    private val cborCodec = CborCodec()
    private val realHidParser = HidReportParser()

    companion object {
        private const val CTAP2_OK = 0x00.toByte()
        private const val HASH_SIZE_32 = 32
        private const val SIG_SIZE_64 = 64
        private const val AUTH_DATA_SIZE_37 = 37
    }

    @Test
    fun `test Ctap2GetAssertionHandler serializes authData and signature as raw CBOR byte strings`() =
        runTest {
            val mockHmacProcessor = mockk<HmacSecretProcessor>()
            val mockEventBus = mockk<Fido2UiEventBus>()
            val mockPrfDerivation = mockk<PrfKeyDerivation>()

            val handler =
                Ctap2GetAssertionHandler(
                    cborCodec = cborCodec,
                    hmacSecretProcessor = mockHmacProcessor,
                    prfKeyDerivation = mockPrfDerivation,
                    hidReportParser = realHidParser,
                    uiEventBus = mockEventBus,
                )

            val testCid = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            val dummyCredIdBytes = ByteArray(16) { 0x0A.toByte() }
            val rawAuthData = ByteArray(AUTH_DATA_SIZE_37) { 0xBB.toByte() }
            val rawSignature = ByteArray(SIG_SIZE_64) { 0xCC.toByte() }

            // Windows-style Base64 inputs
            val base64AuthData = Base64.getEncoder().encode(rawAuthData)
            val base64Signature = Base64.getEncoder().encode(rawSignature)

            // Construct a real-looking CBOR request for GetAssertion (0x02)
            val requestCbor =
                cborCodec.encodeToFido2Format(
                    mapOf(
                        "1" to "webauthn.io",
                        "2" to ByteArray(HASH_SIZE_32) { 0x01 },
                        "3" to
                            listOf(
                                mapOf(
                                    "type" to "public-key",
                                    "id" to dummyCredIdBytes,
                                ),
                            ),
                    ),
                )

            // Mock EventBus dispatch to complete the deferred result
            val dummyAssertion = createDummyAssertion(dummyCredIdBytes, rawAuthData, rawSignature)
            coEvery { mockEventBus.dispatch(any()) } answers {
                val event = it.invocation.args[0] as? Fido2UiEvent
                if (event is Fido2UiEvent.AuthenticationRequested) {
                    event.deferred.complete(Outcome.Success(dummyAssertion))
                }
            }

            // Mock HmacProcessor to pass through
            every { mockHmacProcessor.isPresent(any()) } returns false
            coEvery { mockHmacProcessor.process(any(), any()) } returns null

            // Execute handler
            val realPackets = handler.handle(testCid, requestCbor)
            assertTrue(realPackets.isNotEmpty(), "Handler should produce at least one packet")

            // Reassemble the response (could be multiple packets)
            var reassembled: com.chimali.fido2.bluetooth.CtapHidMessage? = null
            for (packet in realPackets) {
                reassembled = realHidParser.processReport(packet).getOrThrow()
                if (reassembled != null) break
            }
            assertNotNull(reassembled, "Reassembled message was null")
            val responseBytes = reassembled!!.payload

            // Status code 0x00 is first byte, followed by actual CBOR map
            assertEquals(CTAP2_OK, responseBytes[0], "Response should start with CTAP2_OK (0x00)")

            val cborPayload = responseBytes.copyOfRange(1, responseBytes.size)
            val responseMap = cborCodec.decodeFromFido2Format(cborPayload)

            // Verify the map contains the expected binary keys (as strings "1", "2", "3")
            assertTrue(responseMap.containsKey("2"), "Response must contain authData (key 2)")
            assertTrue(responseMap.containsKey("3"), "Response must contain signature (key 3)")

            val outAuthData = responseMap["2"] as ByteArray
            val outSignature = responseMap["3"] as ByteArray

            // CRITICAL: They MUST be the raw bytes, NOT Base64 text
            assertEquals(AUTH_DATA_SIZE_37, outAuthData.size, "authData must be raw binary (37 bytes)")
            assertEquals(SIG_SIZE_64, outSignature.size, "signature must be raw binary (64 bytes)")

            // Double check that the CBOR doesn't contain the Base64 strings anywhere
            assertTrue(containsSubArray(cborPayload, rawAuthData), "CBOR must contain raw authData bytes")
            assertTrue(containsSubArray(cborPayload, rawSignature), "CBOR must contain raw signature bytes")
            assertTrue(containsSubArray(cborPayload, dummyCredIdBytes), "CBOR must contain raw credential ID bytes")

            // Verify Base64 textual representations DO NOT exist in the CBOR
            assertFalse(
                containsSubArray(cborPayload, base64AuthData),
                "CBOR MUST NOT contain Base64 text representations (Windows E_INVALIDARG)",
            )
            assertFalse(
                containsSubArray(cborPayload, base64Signature),
                "CBOR MUST NOT contain Base64 text representations of signature",
            )
        }

    private fun createDummyAssertion(
        credIdBytes: ByteArray,
        authData: ByteArray,
        signature: ByteArray,
    ): AssertionObject {
        return AssertionObject(
            credential =
                PublicKeyCredentialDescriptor.create(
                    id = CredentialId.fromByteArray(credIdBytes),
                ),
            authData = authData,
            signature = signature,
            user = null,
        )
    }

    // Helper to search for subarray
    private fun containsSubArray(
        haystack: ByteArray,
        needle: ByteArray,
    ): Boolean {
        if (needle.isEmpty()) return true
        if (needle.size > haystack.size) return false
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }
}
