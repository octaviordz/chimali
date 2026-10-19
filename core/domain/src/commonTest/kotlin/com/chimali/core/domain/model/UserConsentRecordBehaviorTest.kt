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
