package com.chimali.core.domain.valueobject

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@OptIn(ExperimentalEncodingApi::class)
class CredentialIdTest {
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

    @Test
    fun `init enforces non-empty value`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialId.fromEncoded("")
        }
    }

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

    @Test
    fun `equals correctly compares internal values`() {
        val bytes = ByteArray(CredentialId.CREDENTIAL_ID_SIZE_BYTES) { it.toByte() }
        val id1 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes))
        val id2 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes.clone()))

        assertEquals(id1, id2, "CredentialIds with identical encoded byte arrays should be equal")
    }
}
