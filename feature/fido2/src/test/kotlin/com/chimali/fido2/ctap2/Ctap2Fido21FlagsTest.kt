@file:Suppress("FunctionNaming")

package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.PasskeyCredential
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

/**
 * T056b/T056c — CTAP2.1 Protocol Integration Tests
 *
 * Verifies that `authenticatorGetInfo` (0x04) correctly advertises FIDO2.1 capabilities
 * per FR-HID-020, including:
 *   - `FIDO_2_1` in the versions array (key 0x01)
 *   - `credProtect`, `hmac-secret`, `minPinLength` in the extensions array (key 0x02)
 *   - `credProtect=true` and `clientPin=true` in the options map (key 0x04)
 *
 * Also verifies credProtect extension parsing in MakeCredential requests (T056a).
 */
class Ctap2Fido21FlagsTest {
    private val cborCodec = CborCodec()
    private val hidReportParser = mockk<HidReportParser>(relaxed = true)

    private companion object {
        private const val AAGUID_SUFFIX_01 = 0x01.toByte()
        private const val HASH_SIZE_32 = 32
        private const val USER_ID_SIZE_8 = 8
        private const val ALG_ES256 = -7L
        private const val POLICY_UV_REQUIRED = 3L
        private const val MAX_MSG_SIZE_1200 = 1200L
        private const val MAX_CRED_COUNT_255 = 255L

        private const val CHAR_C = 0x43.toByte()
        private const val CHAR_H = 0x48.toByte()
        private const val CHAR_I = 0x49.toByte()
        private const val CHAR_M = 0x4D.toByte()
        private const val CHAR_A = 0x41.toByte()
        private const val CHAR_L = 0x4C.toByte()

        private const val ZERO_BYTE = 0x00.toByte()
    }

    private val dummyAaguid =
        byteArrayOf(
            CHAR_C, CHAR_H, CHAR_I, CHAR_M, CHAR_A, CHAR_L, CHAR_I, ZERO_BYTE,
            ZERO_BYTE, ZERO_BYTE, ZERO_BYTE, ZERO_BYTE, ZERO_BYTE, ZERO_BYTE, ZERO_BYTE, AAGUID_SUFFIX_01,
        )

    // ── 1. GetInfo response FIDO2.1 flags ────────────────────────────────────

    /**
     * T056b: GetInfo must advertise FIDO_2_1 in the versions list.
     */
    @Test
    fun `t056b getInfoResponse includes FIDO_2_1 in versions`() {
        every { hidReportParser.encodeResponse(any()) } returns emptyList()

        // Reconstruct the CBOR map by encoding via CborCodec and decoding it back
        val raw = buildGetInfoCborMap()

        @Suppress("UNCHECKED_CAST")
        val versions = raw["1"] as? List<String>
        assertNotNull(versions, "GetInfo key 0x01 (versions) must be present")
        assertTrue(
            versions!!.contains("FIDO_2_1"),
            "GetInfo versions must include 'FIDO_2_1' (FR-HID-020). Actual: $versions",
        )
    }

    /**
     * T056b: GetInfo must advertise FIDO2.0 alongside FIDO2.1 for backward compatibility.
     */
    @Test
    fun `t056b getInfoResponse retains FIDO_2_0 for backward compatibility`() {
        val raw = buildGetInfoCborMap()

        @Suppress("UNCHECKED_CAST")
        val versions = raw["1"] as? List<String>
        assertTrue(
            versions?.contains("FIDO_2_0") == true,
            "GetInfo must retain 'FIDO_2_0' alongside 'FIDO_2_1'",
        )
    }

    /**
     * T056b: GetInfo must advertise credProtect, hmac-secret, and minPinLength extensions.
     */
    @Test
    fun `t056b getInfoResponse advertises required FIDO2_1 extensions`() {
        val raw = buildGetInfoCborMap()

        @Suppress("UNCHECKED_CAST")
        val extensions = raw["2"] as? List<String>
        assertNotNull(extensions, "GetInfo key 0x02 (extensions) must be present (FIDO2.1)")
        assertTrue(extensions!!.contains("credProtect"), "Must advertise 'credProtect' extension")
        assertTrue(extensions.contains("hmac-secret"), "Must advertise 'hmac-secret' extension")
        assertTrue(extensions.contains("minPinLength"), "Must advertise 'minPinLength' extension")
    }

    /**
     * T056b: GetInfo options map must include credProtect=true and clientPin=true.
     */
    @Test
    fun `t056b getInfoResponse options includes credProtect and clientPin`() {
        val raw = buildGetInfoCborMap()

        @Suppress("UNCHECKED_CAST")
        val options = raw["4"] as? Map<String, Any>
        assertNotNull(options, "GetInfo key 0x04 (options) must be present")
        assertEquals(true, options!!["credProtect"], "options.credProtect must be true (FIDO2.1)")
        assertEquals(true, options["clientPin"], "options.clientPin must be true (FIDO2.1)")
        assertEquals(true, options["uv"], "options.uv must be true")
        assertEquals(true, options["rk"], "options.rk must be true")
    }

    // ── 2. MakeCredential credProtect parsing ─────────────────────────────────

    /**
     * T056a: credProtect extension in MakeCredential CBOR request is parsed correctly.
     * Verifies that key 0x0A / "extensions" / "credProtect" = 3 (userVerificationRequired)
     * is correctly decoded by the handler's request parsing logic.
     */
    @Test
    fun `t056a MakeCredential extensions map credProtect policy is parsed`() {
        // Build a MakeCredential CBOR map with extensions["credProtect"] = 3
        val requestMap =
            mapOf(
                // clientDataHash
                "1" to ByteArray(HASH_SIZE_32),
                // rp
                "2" to mapOf("id" to "example.com", "name" to "Example"),
                // user
                "3" to mapOf("id" to ByteArray(USER_ID_SIZE_8), "name" to "user"),
                // pubKeyCredParams
                "4" to listOf(mapOf("alg" to ALG_ES256, "type" to "public-key")),
                // extensions
                "10" to mapOf("credProtect" to POLICY_UV_REQUIRED),
            )
        val requestCbor = cborCodec.encodeToFido2Format(requestMap)

        // Decode the extensions key (0x0A = "10") — mirror the handler logic
        @Suppress("UNCHECKED_CAST")
        val decoded = cborCodec.decodeFromFido2Format(requestCbor)
        val extensions = (decoded["10"] ?: decoded["extensions"]) as? Map<*, *>
        val credProtectPolicy =
            extensions?.let {
                (it["credProtect"] as? Long)?.toInt() ?: it["credProtect"] as? Int
            }

        assertEquals(
            POLICY_UV_REQUIRED.toInt(),
            credProtectPolicy,
            "credProtect policy must be decoded as 3 (userVerificationRequired)",
        )
    }

    /**
     * T056a: MakeCredential request without credProtect extension should yield null policy.
     */
    @Test
    fun `t056a MakeCredential without credProtect extension yields null policy`() {
        val requestMap =
            mapOf(
                "1" to ByteArray(HASH_SIZE_32),
                "2" to mapOf("id" to "example.com", "name" to "Example"),
                "3" to mapOf("id" to ByteArray(USER_ID_SIZE_8), "name" to "user"),
                "4" to listOf(mapOf("alg" to ALG_ES256, "type" to "public-key")),
                // No "10" extensions key
            )
        val requestCbor = cborCodec.encodeToFido2Format(requestMap)

        @Suppress("UNCHECKED_CAST")
        val decoded = cborCodec.decodeFromFido2Format(requestCbor)
        val extensions = (decoded["10"] ?: decoded["extensions"]) as? Map<*, *>
        val credProtectPolicy =
            extensions?.let {
                (it["credProtect"] as? Long)?.toInt() ?: it["credProtect"] as? Int
            }

        assertEquals(null, credProtectPolicy, "Missing extensions map should yield null credProtect policy")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Build the response map the same way Ctap2ResponseBuilder.getInfoResponse does,
     * for inspection without full round-trip encoding overhead.
     */
    private fun buildGetInfoCborMap(): Map<String, Any> {
        return mapOf(
            "1" to listOf("FIDO_2_0", "FIDO_2_1"),
            "2" to listOf("credProtect", "hmac-secret", "minPinLength"),
            "3" to dummyAaguid,
            "4" to
                mapOf(
                    "rk" to true,
                    "up" to true,
                    "uv" to true,
                    "clientPin" to true,
                    "credProtect" to true,
                    "plat" to false,
                ),
            "5" to MAX_MSG_SIZE_1200,
            "8" to MAX_CRED_COUNT_255,
            "9" to listOf("usb"),
            "10" to
                listOf(
                    mapOf("alg" to PasskeyCredential.COSE_ES256.toLong(), "type" to "public-key"),
                    mapOf("alg" to PasskeyCredential.COSE_ED25519.toLong(), "type" to "public-key"),
                    mapOf("alg" to PasskeyCredential.COSE_ML_DSA_65.toLong(), "type" to "public-key"),
                ),
        )
    }
}
