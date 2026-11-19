package com.chimali.core.domain.valueobject

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class CredentialIdTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `generate() creates unique identifiers of correct length`() {
        // T010a: Verify 256-bit entropy contract (FR-FIDO2-002)
        val id1 = CredentialId.generate()
        val id2 = CredentialId.generate()

        assertNotEquals(id1.encoded, id2.encoded, "Generated IDs should be unique (SecureRandom)")
        assertEquals(
            expected = CredentialId.CREDENTIAL_ID_SIZE_BYTES,
            actual = id1.toByteArray().size,
            message = "Generated ID should be ${CredentialId.CREDENTIAL_ID_SIZE_BYTES} bytes (256 bits) long",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `init enforces non-empty value`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialId.fromEncoded("")
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `fromByteArray() enforces size boundaries (16-1023 bytes)`() {
        // Lower boundary
        assertFailsWith<IllegalArgumentException>(message = "Should reject 15 bytes") {
            CredentialId.fromByteArray(ByteArray(15))
        }
        CredentialId.fromByteArray(ByteArray(16)) // Should pass

        // Support for encrypted-blob IDs (FR-FIDO2-003 / T010b)
        val opaqueId = ByteArray(256) { it.toByte() }
        val credId = CredentialId.fromByteArray(opaqueId)
        kotlin.test.assertTrue(credId.toByteArray().contentEquals(opaqueId), "256-byte ID must round-trip faithfully")

        // Upper boundary
        CredentialId.fromByteArray(ByteArray(1023)) // Should pass
        assertFailsWith<IllegalArgumentException>(message = "Should reject 1024 bytes") {
            CredentialId.fromByteArray(ByteArray(1024))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `fromEncoded canonicalizes equivalent padded and unpadded base64url`() {
        val bytes = ByteArray(16) { it.toByte() }
        val unpadded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        val padded = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT).encode(bytes)

        val fromUnpadded = CredentialId.fromEncoded(unpadded)
        val fromPadded = CredentialId.fromEncoded(padded)

        assertEquals(fromUnpadded, fromPadded)
        assertEquals(unpadded, fromPadded.encoded)
        assertEquals(16, fromPadded.toByteArray().size)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `decoded credential identifier bytes are borrowed only within scoped callback`() {
        val bytes = ByteArray(16) { it.toByte() }
        val encoded = Base64.UrlSafe.encode(bytes)
        var observed: ByteArray? = null

        val decodedLength =
            CredentialId.withDecodedBytes(encoded) { decoded ->
                observed = decoded
                assertContentEquals(bytes, decoded)
                decoded.size
            }

        assertEquals(bytes.size, decodedLength)
        assertContentEquals(ByteArray(bytes.size), observed)

        assertFailsWith<IllegalStateException> {
            CredentialId.withDecodedBytes(encoded) { decoded ->
                observed = decoded
                error("synthetic borrower failure")
            }
        }
        assertContentEquals(ByteArray(bytes.size), observed)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `fromEncoded rejects malformed base64url and decoded lengths outside the contract`() {
        assertFailsWith<IllegalArgumentException> { CredentialId.fromEncoded("not+base64") }
        assertFailsWith<IllegalArgumentException> {
            CredentialId.fromEncoded(Base64.UrlSafe.encode(ByteArray(15)))
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialId.fromEncoded(Base64.UrlSafe.encode(ByteArray(1024)))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `malformed encoded credential identifier does not retain decoder failure`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                CredentialId.fromEncoded("synthetic-invalid-credential-marker+")
            }

        assertEquals("CredentialId must be valid Base64URL", failure.message)
        assertNull(failure.cause)
        assertFalse("synthetic-invalid-credential-marker" in failure.toString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `equals correctly compares internal values`() {
        val bytes = ByteArray(CredentialId.CREDENTIAL_ID_SIZE_BYTES) { it.toByte() }
        val id1 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes))
        val id2 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes.clone()))

        assertEquals(id1, id2, "CredentialIds with identical encoded byte arrays should be equal")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `diagnostic representation does not disclose encoded credential identifier`() {
        val encoded = Base64.UrlSafe.encode(ByteArray(16) { (it + 1).toByte() })
        val id = CredentialId.fromEncoded(encoded)

        assertFalse(id.toString().contains(encoded))
        assertEquals("CredentialId([redacted])", id.toString())
    }
}
