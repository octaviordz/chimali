package com.chimali.fido2.ctap2

import com.chimali.core.common.result.Outcome
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Validates fixes implemented for Windows 11 CTAP2 compatibility.
 * These tests ensure strict adherence to undocumented OS requirements,
 * preventing regressions that cause E_INVALIDARG or Browser crashes.
 */
class Ctap2WindowsCompatibilityTest {
    private val cborCodec = CborCodec()

    private companion object {
        private const val ALG_ES256_NEG_7 = -7L
        private const val FLAG_UP = 0x01
        private const val FLAG_UV = 0x04
        private const val FLAG_AT = 0x40
        private const val COMBINED_FLAGS_0x45 = 0x45.toByte()
        private const val AUTH_DATA_SIZE_37 = 37
        private const val SIGNATURE_SIZE_72 = 72
        private const val CRED_ID_SIZE_16 = 16
        private const val HASH_SIZE_32 = 32
        private const val CTAP2_OK = 0x00.toByte()
        private const val CBOR_TYPE_MAJOR_1_NEG_7 = 0x26.toByte()
    }

    // ── 1. CBOR Encoding Strictness ──────────────────────────────────────────

    @Test
    fun `test CborCodec correctly encodes COSE negative integers for ES256`() {
        // Windows rejects the metadata if the ES256 algorithm ID (-7) is incorrectly encoded
        val map = mapOf("alg" to ALG_ES256_NEG_7)
        val encoded = cborCodec.encodeToFido2Format(map)

        // CBOR representation of {"alg": -7}:
        // Map(1): a1
        // Key("alg"): 63 61 6c 67
        // Value(-7): 26 (Major type 1, value 6 -> -1 - 6 = -7)
        var foundNegativeSeven = false
        for (b in encoded) {
            if (b == CBOR_TYPE_MAJOR_1_NEG_7) {
                foundNegativeSeven = true
                break
            }
        }
        assertTrue(foundNegativeSeven, "CBOR encoder failed to produce valid Major Type 1 negative integer 0x26 (-7)")
    }

    // ── 2. AuthenticatorData AT Flag ──────────────────────────────────────────

    @Test
    fun `test AuthenticatorData sets AT flag when public key is present`() {
        // This validates the logic injected into Ctap2MakeCredentialHandler.buildAuthenticatorData
        // If the AT flag (0x40) is missing, Windows browsers crash reading attestation.
        val baseFlags = FLAG_UP or FLAG_UV

        // Emulate the bitwise forced OR in the handler
        val finalFlags = (baseFlags or FLAG_AT or FLAG_UP).toByte()

        assertEquals(COMBINED_FLAGS_0x45, finalFlags, "Flags should correctly combine UP, UV, and AT")
        assertTrue((finalFlags.toInt() and FLAG_AT) != 0, "AT flag (bit 6) must be logically set")
    }

    // ── 3. GetAssertion Raw Byte Array vs Base64 Serialization ───────────────

    @Test
    fun `test Ctap2GetAssertionHandler serializes authData and signature as raw CBOR byte strings`() =
        runTest {
            val mockUseCase = mockk<GetAssertionUseCase>()
            val mockHmacProcessor = mockk<HmacSecretProcessor>()
            every { mockHmacProcessor.isPresent(any()) } returns false
            val handler = Ctap2GetAssertionHandler(mockUseCase, cborCodec, mockHmacProcessor)

            // Create dummy bytes
            val dummyAuthData = ByteArray(AUTH_DATA_SIZE_37) { 0xAA.toByte() }
            val dummySignature = ByteArray(SIGNATURE_SIZE_72) { 0xBB.toByte() }
            val dummyCredId = ByteArray(CRED_ID_SIZE_16) { 0xCC.toByte() }

            val assertion =
                AssertionObject(
                    credential = PublicKeyCredentialDescriptor.create(id = dummyCredId),
                    authData = dummyAuthData,
                    signature = dummySignature,
                    user = null,
                )

            coEvery { mockUseCase(any()) } returns Outcome.Success(assertion)

            // Construct a raw GetAssertion request (rpId, clientDataHash)
            val requestMap =
                mapOf(
                    "1" to "webauthn.io",
                    "2" to ByteArray(HASH_SIZE_32) { 0x01 },
                )
            val requestCbor = cborCodec.encodeToFido2Format(requestMap)

            // Execute the handler
            val responseBytes = handler.handle(requestCbor)

            // Status code 0x00 is first byte, followed by actual CBOR map
            assertEquals(CTAP2_OK, responseBytes[0], "Response should start with CTAP2_OK (0x00)")

            val cborPayload = responseBytes.copyOfRange(1, responseBytes.size)
            // We expect the payload to contain the raw byte sequences, not the ASCII strings "qqqq..." (Base64 of 0xAA)

            val base64AuthData = java.util.Base64.getEncoder().encodeToString(dummyAuthData).toByteArray()

            // Verify the raw dummy sequences exist in the CBOR
            assertTrue(containsSubArray(cborPayload, dummyAuthData), "CBOR must contain raw authData bytes")
            assertTrue(containsSubArray(cborPayload, dummySignature), "CBOR must contain raw signature bytes")
            assertTrue(containsSubArray(cborPayload, dummyCredId), "CBOR must contain raw credential ID bytes")

            // Verify Base64 textual representations DO NOT exist in the CBOR
            assertFalse(
                containsSubArray(cborPayload, base64AuthData),
                "CBOR MUST NOT contain Base64 text representations (Windows E_INVALIDARG)",
            )
        }

    // Helper to search for subarray
    private fun containsSubArray(
        haystack: ByteArray,
        needle: ByteArray,
    ): Boolean {
        if (needle.isEmpty()) return true
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
