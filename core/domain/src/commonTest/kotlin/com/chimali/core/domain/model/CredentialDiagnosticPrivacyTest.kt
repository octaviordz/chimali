package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialCategory
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.EncryptedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.Instant

class CredentialDiagnosticPrivacyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `generic credential representation omits identifying and payload fields`() {
        val credentialId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 0x31 })
        val credential =
            Credential(
                id = credentialId,
                title = "synthetic-credential-title-marker",
                username = "synthetic-credential-user-marker",
                password = EncryptedString("synthetic-encrypted-password-marker"),
                url = "https://synthetic-credential-url-marker.invalid",
                notes = "synthetic-credential-notes-marker",
                category = CredentialCategory.PERSONAL,
                tags = listOf("synthetic-credential-tag-marker"),
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                lastModified = Instant.parse("2025-01-01T00:00:00Z"),
                lastUsed = null,
                isFavorite = false,
            )

        val diagnostic = credential.toString()
        val encryptedDiagnostic = credential.password.toString()

        assertEquals("Credential([redacted])", diagnostic)
        assertEquals("EncryptedString([redacted])", encryptedDiagnostic)
        listOf(
            credentialId.encoded,
            "synthetic-credential-title-marker",
            "synthetic-credential-user-marker",
            "synthetic-encrypted-password-marker",
            "synthetic-credential-url-marker",
            "synthetic-credential-notes-marker",
            "synthetic-credential-tag-marker",
        ).forEach { marker -> assertFalse(diagnostic.contains(marker), marker) }
        assertFalse(encryptedDiagnostic.contains("synthetic-encrypted-password-marker"))
    }
}
