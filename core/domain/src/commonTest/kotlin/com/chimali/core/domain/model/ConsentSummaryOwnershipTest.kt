package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

class ConsentSummaryOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent owns an independent RP copy and erases it without changing its borrowed input`() {
        val source = SensitiveText.copyOf("https://private.example")
        val record = UserConsentRecord.fromText("consent", ConsentOperationType.REGISTRATION, source)
        val borrowedRecordText = record.rpIdText
        val copy = record.copy(isBiometricUsed = true)
        record.eraseSensitiveText()
        try {
            assertTrue(borrowedRecordText.all { it == '\u0000' })
            assertTrue(source.contentEquals("https://private.example"))
            assertTrue(copy.rpIdText.contentEquals(source))
            assertTrue(copy.isBiometricUsed)
            assertFalse(copy.toString().contains("private.example"))
        } finally {
            source.erase()
            copy.eraseSensitiveText()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `summary copies RP ownership without altering public identity on disposal`() {
        val input = SensitiveText.copyOf("https://private.example")
        val id = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        val summary =
            CredentialSummary.fromText(
                "summary",
                input,
                id,
                Clock.System.now(),
                -7,
                CredentialKeyDescriptor.P256_PROFILE,
                byteArrayOf(1, 2),
            )
        val copy = summary.copy(credProtectPolicy = 3)
        val borrowed = summary.rpIdText
        summary.eraseSensitiveText()
        try {
            assertTrue(borrowed.all { it == '\u0000' })
            assertTrue(copy.rpIdText.contentEquals(input))
            assertEquals(id, summary.credentialId)
            assertEquals(3, copy.credProtectPolicy)
            assertFalse(copy.toString().contains("private.example"))
        } finally {
            input.erase()
            copy.eraseSensitiveText()
        }
    }
}
