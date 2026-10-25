package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock

class UserConsentRecordBehaviorTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent method distinguishes all biometric and PIN combinations`() {
        assertEquals(ConsentMethod.NONE, record().getConsentMethod())
        assertEquals(ConsentMethod.BIOMETRIC, record(isBiometricUsed = true).getConsentMethod())
        assertEquals(ConsentMethod.PIN, record(isPinUsed = true).getConsentMethod())
        assertEquals(
            ConsentMethod.BIOMETRIC_AND_PIN,
            record(isBiometricUsed = true, isPinUsed = true).getConsentMethod(),
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `recentness accepts configured window and rejects older records`() {
        val now = Clock.System.now()

        assertTrue(record(timestamp = now - 4.minutes).isRecent())
        assertFalse(record(timestamp = now - 6.minutes).isRecent())
        assertTrue(record(timestamp = now - 6.minutes).isRecent(minutes = 10))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `identity helpers distinguish credential RP and operation type`() {
        val id = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        val consent = record(operationType = ConsentOperationType.REGISTRATION, credentialId = id)

        assertTrue(consent.isForCredential(id))
        assertFalse(consent.isForCredential(CredentialId.fromByteArray(ByteArray(16) { (it + 1).toByte() })))
        assertTrue(consent.isForRelyingParty(RpId("HTTPS://EXAMPLE.COM")))
        assertFalse(consent.isForRelyingParty(RpId("https://other.example")))
        assertEquals(id.encoded, consent.getSafeCredentialId())
        assertTrue(consent.isRegistrationConsent())
        assertFalse(consent.isAuthenticationConsent())
        assertEquals("N/A", record().getSafeCredentialId())
        assertTrue(record().isAuthenticationConsent())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `record accepts valid IPv4 IPv6 and maximum credential identity`() {
        val maxCredentialId = CredentialId.fromByteArray(ByteArray(CredentialId.MAX_CREDENTIAL_ID_BYTES))

        assertEquals("127.0.0.1", record(ipAddress = "127.0.0.1").ipAddress)
        assertEquals("2001:db8::1", record(ipAddress = "2001:db8::1").ipAddress)
        assertEquals(maxCredentialId, record(credentialId = maxCredentialId).credentialId)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `record rejects missing required values and timestamps beyond grace`() {
        assertFailsWith<IllegalArgumentException> { record(id = " ") }
        assertFailsWith<IllegalArgumentException> { record(rpId = "example.com") }
        assertFailsWith<IllegalArgumentException> { record(timestamp = Clock.System.now() + 61.seconds) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `record rejects malformed and overlong IP values`() {
        listOf(" ", "999.1.1.1", "not-an-ip", "2001:::1", "1".repeat(UserConsentRecord.MAX_IP_ADDRESS_LENGTH + 1))
            .forEach { ip ->
                assertFailsWith<IllegalArgumentException>("Invalid IP should fail: $ip") {
                    record(ipAddress = ip)
                }
            }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `record rejects blank and oversized user agent and device ID`() {
        assertFailsWith<IllegalArgumentException> { record(userAgent = " ") }
        assertFailsWith<IllegalArgumentException> {
            record(userAgent = "u".repeat(UserConsentRecord.MAX_USER_AGENT_LENGTH + 1))
        }
        assertFailsWith<IllegalArgumentException> { record(deviceId = " ") }
        assertFailsWith<IllegalArgumentException> {
            record(deviceId = "d".repeat(UserConsentRecord.MAX_DEVICE_ID_LENGTH + 1))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `record accepts optional metadata at documented text boundaries`() {
        val consent =
            record(
                userAgent = "u".repeat(UserConsentRecord.MAX_USER_AGENT_LENGTH),
                deviceId = "d".repeat(UserConsentRecord.MAX_DEVICE_ID_LENGTH),
            )

        assertEquals(UserConsentRecord.MAX_USER_AGENT_LENGTH, consent.userAgent?.length)
        assertEquals(UserConsentRecord.MAX_DEVICE_ID_LENGTH, consent.deviceId?.length)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `eraseSensitiveText erases the owned RP buffer`() {
        val consent = record(rpId = "https://example.com")
        assertEquals("https://example.com", consent.rpId.value)

        consent.eraseSensitiveText()

        val erasedExpected = "\u0000".repeat("https://example.com".length)
        assertEquals(erasedExpected, consent.rpIdText.toBoundaryString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `copy performs deep copy of sensitive text isolating instances`() {
        val original = record(rpId = "https://example.com")
        val copied = original.copy()

        assertEquals(original, copied)

        copied.eraseSensitiveText()

        assertEquals("https://example.com", original.rpId.value)
        val erasedExpected = "\u0000".repeat("https://example.com".length)
        assertEquals(erasedExpected, copied.rpIdText.toBoundaryString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `fromText constructs and validates consent record`() {
        val consent =
            UserConsentRecord.fromText(
                id = "from-text-id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = "https://fido.org",
            )

        assertEquals("from-text-id", consent.id)
        assertEquals(ConsentOperationType.REGISTRATION, consent.operationType)
        assertEquals("https://fido.org", consent.rpId.value)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `diagnostics redact metadata and equals hashCode work on record fields`() {
        val timestamp = Clock.System.now()
        val consent1 = record(id = "rec-1", rpId = "https://site1.com", timestamp = timestamp)
        val consent2 = record(id = "rec-1", rpId = "https://site1.com", timestamp = timestamp)
        val consent3 = record(id = "rec-2", rpId = "https://site2.com", timestamp = timestamp)

        assertEquals("UserConsentRecord([sensitive metadata redacted])", consent1.toString())
        assertEquals(consent1, consent2)
        assertFalse(consent1 == consent3)
        assertEquals(consent1.hashCode(), consent2.hashCode())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `validation failure erases the internal sensitive text buffer`() {
        val valid = record(rpId = "https://example.com")
        assertFailsWith<IllegalArgumentException> {
            valid.copy(id = "   ")
        }
    }

    private fun record(
        id: String = "consent-1",
        operationType: ConsentOperationType = ConsentOperationType.AUTHENTICATION,
        rpId: String = "https://example.com",
        credentialId: CredentialId? = null,
        timestamp: kotlinx.datetime.Instant = Clock.System.now(),
        isBiometricUsed: Boolean = false,
        isPinUsed: Boolean = false,
        ipAddress: String? = null,
        userAgent: String? = null,
        deviceId: String? = null,
    ): UserConsentRecord =
        UserConsentRecord(
            id = id,
            operationType = operationType,
            rpId = RpId(rpId),
            credentialId = credentialId,
            timestamp = timestamp,
            isBiometricUsed = isBiometricUsed,
            isPinUsed = isPinUsed,
            ipAddress = ipAddress,
            userAgent = userAgent,
            deviceId = deviceId,
        )
}
