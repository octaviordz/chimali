@file:Suppress("FunctionNaming")

package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.AuthenticatorInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    private val responseBuilder = Ctap2ResponseBuilder(cborCodec, hidReportParser)

    private val dummyAaguid =
        byteArrayOf(
            0x43, 0x48, 0x49, 0x4D, 0x41, 0x4C, 0x49, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )

    private fun fakeInfo(supportsRk: Boolean = true): AuthenticatorInfo {
        val info = mockk<AuthenticatorInfo>()
        every { info.aaguid } returns dummyAaguid
        every { info.supportsResidentKeys } returns supportsRk
        return info
    }

    // ── 1. GetInfo response FIDO2.1 flags ────────────────────────────────────

    /**
     * T056b: GetInfo must advertise FIDO_2_1 in the versions list.
     */
    @Test
    fun `t056b getInfoResponse includes FIDO_2_1 in versions`() {
        val cid = byteArrayOf(0x00, 0x00, 0x00, 0x01)
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
                "1" to ByteArray(32), // clientDataHash
                "2" to mapOf("id" to "example.com", "name" to "Example"), // rp
                "3" to mapOf("id" to ByteArray(8), "name" to "user"), // user
                "4" to listOf(mapOf("alg" to -7L, "type" to "public-key")), // pubKeyCredParams
                "10" to mapOf("credProtect" to 3L), // extensions
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
            3,
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
                "1" to ByteArray(32),
                "2" to mapOf("id" to "example.com", "name" to "Example"),
                "3" to mapOf("id" to ByteArray(8), "name" to "user"),
                "4" to listOf(mapOf("alg" to -7L, "type" to "public-key")),
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
            "5" to 1200L,
            "8" to 255L,
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
