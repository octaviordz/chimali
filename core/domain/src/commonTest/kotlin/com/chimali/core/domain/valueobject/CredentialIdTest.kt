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
        val id1 = CredentialId.generate()
        val id2 = CredentialId.generate()

        assertNotEquals(id1.encoded, id2.encoded, "Generated IDs should be unique")
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
    fun `equals correctly compares internal values`() {
        val bytes = ByteArray(CredentialId.CREDENTIAL_ID_SIZE_BYTES) { it.toByte() }
        val id1 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes))
        val id2 = CredentialId.fromEncoded(Base64.UrlSafe.encode(bytes.clone()))

        assertEquals(id1, id2, "CredentialIds with identical encoded byte arrays should be equal")
    }
}
