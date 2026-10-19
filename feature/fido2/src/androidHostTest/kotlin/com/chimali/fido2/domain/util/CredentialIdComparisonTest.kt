package com.chimali.fido2.domain.util

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies credential identity behavior; it deliberately makes no wall-clock timing claim. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialIdComparisonTest {
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `comparison accepts equal bytes and canonical padded input`() {
        val bytes = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { it.toByte() }
        val unpadded = CredentialId.fromByteArray(bytes)
        val padded = CredentialId.fromEncoded(unpadded.encoded + "==")

        assertTrue(unpadded.matchesCredentialId(padded))
        assertTrue(padded.matchesCredentialId(unpadded))
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `comparison rejects first byte last byte and length mismatches`() {
        val referenceBytes = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { it.toByte() }
        val firstByteMismatch = referenceBytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val lastByteMismatch = referenceBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val longer = referenceBytes + 0x7f.toByte()
        val reference = CredentialId.fromByteArray(referenceBytes)

        assertFalse(reference.matchesCredentialId(CredentialId.fromByteArray(firstByteMismatch)))
        assertFalse(reference.matchesCredentialId(CredentialId.fromByteArray(lastByteMismatch)))
        assertFalse(reference.matchesCredentialId(CredentialId.fromByteArray(longer)))
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `comparison supports maximum valid credential ID length`() {
        val bytes = ByteArray(CredentialId.MAX_CREDENTIAL_ID_BYTES) { (it * 31).toByte() }

        assertTrue(CredentialId.fromByteArray(bytes).matchesCredentialId(CredentialId.fromByteArray(bytes.copyOf())))
    }
}
