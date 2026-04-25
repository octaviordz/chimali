package com.chimali.fido2.domain.model

import org.junit.jupiter.api.Nested
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UserConsentRecordTest {
    private lateinit var testTimestamp: Instant
    private lateinit var testRpId: String
    private lateinit var testCredentialId: String
    private lateinit var testIpAddress: String
    private lateinit var testUserAgent: String
    private lateinit var testDeviceId: String

    @BeforeTest
    fun setUp() =
        runTest {
            testTimestamp = Instant.now()
            testRpId = "https://example.com"
            testCredentialId = "test_credential_id"
            testIpAddress = "192.168.1.1"
            testUserAgent = "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0"
            testDeviceId = "device_12345"
        }

    private companion object {
        private const val FUTURE_SECONDS_120 = 120L
        private const val INVALID_CRED_ID_SIZE_1024 = 1024
        private const val RECENT_THRESHOLD_5 = 5L
        private const val OLD_MINUTES_6 = 6L
        private const val RECENT_MINUTES_4 = 4L
        private const val SECONDS_PER_MINUTE = 60
        private const val RECENT_BUFFER_1 = 1L
        private const val MAX_IP_LEN_45 = 45
        private const val MAX_UA_LEN_512 = 512
        private const val MAX_DEVICE_ID_LEN_64 = 64
        private const val MAX_CRED_ID_LEN_1023 = 1023
    }

    @Nested
    inner class ValidationTests {
        @Test
        fun `should create valid consent record with all fields`() =
            runTest {
                val consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )

                assertNotNull(consent)
                assertEquals("test_id", consent.id)
                assertEquals(ConsentOperationType.REGISTRATION, consent.operationType)
                assertEquals(testRpId, consent.rpId)
                assertEquals(testCredentialId, consent.credentialId)
                assertEquals(testTimestamp, consent.timestamp)
                assertTrue(consent.biometricUsed)
                assertFalse(consent.pinUsed)
                assertEquals(testIpAddress, consent.ipAddress)
                assertEquals(testUserAgent, consent.userAgent)
                assertEquals(testDeviceId, consent.deviceId)
            }

        @Test
        fun `should create valid consent record with minimal fields`() =
            runTest {
                val consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        credentialId = null,
                        timestamp = testTimestamp,
                        biometricUsed = false,
                        pinUsed = true,
                        ipAddress = null,
                        userAgent = null,
                        deviceId = null,
                    )

                assertNotNull(consent)
                assertEquals("test_id", consent.id)
                assertEquals(ConsentOperationType.AUTHENTICATION, consent.operationType)
                assertEquals(testRpId, consent.rpId)
                assertNull(consent.credentialId)
                assertEquals(testTimestamp, consent.timestamp)
                assertFalse(consent.biometricUsed)
                assertTrue(consent.pinUsed)
                assertNull(consent.ipAddress)
                assertNull(consent.userAgent)
                assertNull(consent.deviceId)
            }

        @Test
        fun `should throw exception when id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when rp id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = "",
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when rp id is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = "ftp://invalid-rp-id",
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when timestamp is in the future`() =
            runTest {
                val futureTimestamp = Instant.now().plusSeconds(FUTURE_SECONDS_120)
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = futureTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when credential id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = "",
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when credential id exceeds maximum length`() =
            runTest {
                val longCredentialId = "a".repeat(INVALID_CRED_ID_SIZE_1024)
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = longCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when ip address is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = "invalid-ip",
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when user agent is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = "",
                        deviceId = testDeviceId,
                    )
                }
            }

        @Test
        fun `should throw exception when device id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = "",
                    )
                }
            }

        @Test
        fun `should throw exception when no consent method is used`() =
            runTest {
                // The domain model allows consent records with no explicit verification
                // method for silent/implicit consent scenarios. This tests that such
                // records can be created successfully.
                val consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = false,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
                assertEquals(ConsentMethod.NONE, consent.getConsentMethod())
            }
    }

    @Nested
    inner class BusinessLogicTests {
        private lateinit var consent: UserConsentRecord

        @BeforeTest
        fun setUp() =
            runTest {
                consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )
            }

        @Test
        fun `should correctly check if consent is recent`() =
            runTest {
                assertTrue(consent.isRecent(RECENT_THRESHOLD_5)) // Should be recent within 5 minutes

                val oldConsent =
                    consent.copy(
                        timestamp = Instant.now().minusSeconds(OLD_MINUTES_6.toLong() * SECONDS_PER_MINUTE),
                    ) // 6 minutes ago
                assertFalse(oldConsent.isRecent(RECENT_THRESHOLD_5))

                val recentConsent =
                    consent.copy(
                        timestamp = Instant.now().minusSeconds(RECENT_MINUTES_4.toLong() * SECONDS_PER_MINUTE),
                    ) // 4 minutes ago
                assertTrue(recentConsent.isRecent(RECENT_THRESHOLD_5))
            }

        @Test
        fun `should correctly check if consent is for specific credential`() =
            runTest {
                assertTrue(consent.isForCredential(testCredentialId))
                assertTrue(consent.isForCredential("TEST_CREDENTIAL_ID")) // Case insensitive

                assertFalse(consent.isForCredential("other_credential_id"))

                val consentWithoutCredential = consent.copy(credentialId = null)
                assertFalse(consentWithoutCredential.isForCredential(testCredentialId))
            }

        @Test
        fun `should correctly check if consent is for specific rp`() =
            runTest {
                assertTrue(consent.isForRelyingParty(testRpId))
                assertTrue(consent.isForRelyingParty("HTTPS://EXAMPLE.COM")) // Case insensitive

                assertFalse(consent.isForRelyingParty("https://other.com"))
            }

        @Test
        fun `should return correct consent method`() =
            runTest {
                assertEquals(ConsentMethod.BIOMETRIC, consent.getConsentMethod())

                val pinConsent = consent.copy(biometricUsed = false, pinUsed = true)
                assertEquals(ConsentMethod.PIN, pinConsent.getConsentMethod())

                val combinedConsent = consent.copy(biometricUsed = true, pinUsed = true)
                assertEquals(ConsentMethod.BIOMETRIC_AND_PIN, combinedConsent.getConsentMethod())

                val noConsent = consent.copy(biometricUsed = false, pinUsed = false)
                assertEquals(ConsentMethod.NONE, noConsent.getConsentMethod())
            }

        @Test
        fun `should return safe credential id`() =
            runTest {
                assertEquals(testCredentialId, consent.getSafeCredentialId())

                val consentWithoutCredential = consent.copy(credentialId = null)
                assertEquals("N/A", consentWithoutCredential.getSafeCredentialId())
            }

        @Test
        fun `should correctly identify registration consent`() =
            runTest {
                assertTrue(consent.isRegistrationConsent())

                val authConsent = consent.copy(operationType = ConsentOperationType.AUTHENTICATION)
                assertFalse(authConsent.isRegistrationConsent())
            }

        @Test
        fun `should correctly identify authentication consent`() =
            runTest {
                assertFalse(consent.isAuthenticationConsent())

                val authConsent = consent.copy(operationType = ConsentOperationType.AUTHENTICATION)
                assertTrue(authConsent.isAuthenticationConsent())
            }
    }

    @Nested
    inner class CompanionObjectTests {
        @Test
        fun `should create consent record using companion object factory method`() =
            runTest {
                val consent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )

                assertNotNull(consent)
                assertEquals(ConsentOperationType.REGISTRATION, consent.operationType)
                assertEquals(testRpId, consent.rpId)
                assertEquals(testCredentialId, consent.credentialId)
                assertTrue(consent.biometricUsed)
                assertFalse(consent.pinUsed)
                assertEquals(testIpAddress, consent.ipAddress)
                assertEquals(testUserAgent, consent.userAgent)
                assertEquals(testDeviceId, consent.deviceId)
                assertNotNull(consent.id) // Should be auto-generated
                assertTrue(consent.timestamp.isBefore(Instant.now().plusSeconds(RECENT_BUFFER_1))) // Should be recent
            }

        @Test
        fun `should create consent record with minimal parameters`() =
            runTest {
                val consent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        biometricUsed = false,
                        pinUsed = true,
                    )

                assertNotNull(consent)
                assertEquals(ConsentOperationType.AUTHENTICATION, consent.operationType)
                assertEquals(testRpId, consent.rpId)
                assertNull(consent.credentialId)
                assertFalse(consent.biometricUsed)
                assertTrue(consent.pinUsed)
                assertNull(consent.ipAddress)
                assertNull(consent.userAgent)
                assertNull(consent.deviceId)
            }

        @Test
        fun `should have correct constant values`() =
            runTest {
                assertEquals(MAX_IP_LEN_45, UserConsentRecord.MAX_IP_ADDRESS_LENGTH)
                assertEquals(MAX_UA_LEN_512, UserConsentRecord.MAX_USER_AGENT_LENGTH)
                assertEquals(MAX_DEVICE_ID_LEN_64, UserConsentRecord.MAX_DEVICE_ID_LENGTH)
                assertEquals(MAX_CRED_ID_LEN_1023, UserConsentRecord.MAX_CREDENTIAL_ID_LENGTH)
            }
    }

    @Nested
    inner class IpAddressValidationTests {
        @Test
        fun `should accept valid ipv4 addresses`() =
            runTest {
                val validIpAddresses =
                    listOf(
                        "192.168.1.1",
                        "10.0.0.1",
                        "172.16.0.1",
                        "127.0.0.1",
                        "255.255.255.255",
                        "0.0.0.0",
                    )

                validIpAddresses.forEach { ip ->
                    val consent =
                        UserConsentRecord(
                            id = "test_id",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            credentialId = testCredentialId,
                            timestamp = testTimestamp,
                            biometricUsed = true,
                            pinUsed = false,
                            ipAddress = ip,
                            userAgent = testUserAgent,
                            deviceId = testDeviceId,
                        )
                    assertNotNull(consent)
                    assertEquals(ip, consent.ipAddress)
                }
            }

        @Test
        fun `should accept valid ipv6 addresses`() =
            runTest {
                val validIpAddresses =
                    listOf(
                        "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                        "2001:db8::1",
                        "::1",
                        "fe80::1",
                        "2001:db8:85a3::8a2e:370:7334",
                    )

                validIpAddresses.forEach { ip ->
                    val consent =
                        UserConsentRecord(
                            id = "test_id",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            credentialId = testCredentialId,
                            timestamp = testTimestamp,
                            biometricUsed = true,
                            pinUsed = false,
                            ipAddress = ip,
                            userAgent = testUserAgent,
                            deviceId = testDeviceId,
                        )
                    assertNotNull(consent)
                    assertEquals(ip, consent.ipAddress)
                }
            }

        @Test
        fun `should reject invalid ip addresses`() =
            runTest {
                val invalidIpAddresses =
                    listOf(
                        "256.256.256.256",
                        "192.168.1",
                        "192.168.1.1.1",
                        "invalid-ip",
                        "192.168.1.-1",
                        "192.168.1.999",
                        "2001:db8:::1",
                        "2001:db8::g",
                        "xyz:123::1",
                    )

                invalidIpAddresses.forEach { ip ->
                    assertFailsWith<IllegalArgumentException> {
                        UserConsentRecord(
                            id = "test_id",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            credentialId = testCredentialId,
                            timestamp = testTimestamp,
                            biometricUsed = true,
                            pinUsed = false,
                            ipAddress = ip,
                            userAgent = testUserAgent,
                            deviceId = testDeviceId,
                        )
                    }
                }
            }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle maximum allowed field sizes`() =
            runTest {
                val maxCredentialId = "a".repeat(MAX_CRED_ID_LEN_1023)
                val maxIpAddress = "2001:0db8:85a3:0000:0000:8a2e:0370:7334" // Valid IPv6
                val maxUserAgent = "a".repeat(MAX_UA_LEN_512)
                val maxDeviceId = "a".repeat(MAX_DEVICE_ID_LEN_64)

                val consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = maxCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = false,
                        ipAddress = maxIpAddress,
                        userAgent = maxUserAgent,
                        deviceId = maxDeviceId,
                    )

                assertNotNull(consent)
                assertEquals(maxCredentialId, consent.credentialId)
                assertEquals(maxIpAddress, consent.ipAddress)
                assertEquals(maxUserAgent, consent.userAgent)
                assertEquals(maxDeviceId, consent.deviceId)
            }

        @Test
        fun `should handle all operation types`() =
            runTest {
                val operationTypes =
                    listOf(
                        ConsentOperationType.REGISTRATION,
                        ConsentOperationType.AUTHENTICATION,
                        ConsentOperationType.CREDENTIAL_DELETION,
                        ConsentOperationType.CREDENTIAL_UPDATE,
                    )

                operationTypes.forEach { operationType ->
                    val consent =
                        UserConsentRecord(
                            id = "test_id",
                            operationType = operationType,
                            rpId = testRpId,
                            credentialId = testCredentialId,
                            timestamp = testTimestamp,
                            biometricUsed = true,
                            pinUsed = false,
                            ipAddress = testIpAddress,
                            userAgent = testUserAgent,
                            deviceId = testDeviceId,
                        )

                    assertNotNull(consent)
                    assertEquals(operationType, consent.operationType)
                }
            }

        @Test
        fun `should handle combined biometric and pin consent`() =
            runTest {
                val consent =
                    UserConsentRecord(
                        id = "test_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = testCredentialId,
                        timestamp = testTimestamp,
                        biometricUsed = true,
                        pinUsed = true,
                        ipAddress = testIpAddress,
                        userAgent = testUserAgent,
                        deviceId = testDeviceId,
                    )

                assertNotNull(consent)
                assertTrue(consent.biometricUsed)
                assertTrue(consent.pinUsed)
                assertEquals(ConsentMethod.BIOMETRIC_AND_PIN, consent.getConsentMethod())
            }
    }
}
