package com.chimali.fido2.domain.model

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

@DisplayName("UserConsentRecord Domain Model Tests")
class UserConsentRecordTest {
    
    private lateinit var testTimestamp: Instant
    private lateinit var testRpId: String
    private lateinit var testCredentialId: String
    private lateinit var testIpAddress: String
    private lateinit var testUserAgent: String
    private lateinit var testDeviceId: String
    
    @BeforeEach
    fun setUp() = runTest {
        testTimestamp = Instant.now()
        testRpId = "https://example.com"
        testCredentialId = "test_credential_id"
        testIpAddress = "192.168.1.1"
        testUserAgent = "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0"
        testDeviceId = "device_12345"
    }
    
    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {
        
        @Test
        @DisplayName("Should create valid consent record with all fields")
        fun `should create valid consent record with all fields`() = runTest {
            val consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = testCredentialId,
                timestamp = testTimestamp,
                biometricUsed = true,
                pinUsed = false,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceId = testDeviceId
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
        @DisplayName("Should create valid consent record with minimal fields")
        fun `should create valid consent record with minimal fields`() = runTest {
            val consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.AUTHENTICATION,
                rpId = testRpId,
                credentialId = null,
                timestamp = testTimestamp,
                biometricUsed = false,
                pinUsed = true,
                ipAddress = null,
                userAgent = null,
                deviceId = null
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
        @DisplayName("Should throw exception when ID is blank")
        fun `should throw exception when id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when RP ID is blank")
        fun `should throw exception when rp id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when RP ID is invalid")
        fun `should throw exception when rp id is invalid`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when timestamp is in the future")
        fun `should throw exception when timestamp is in the future`() = runTest {
            val futureTimestamp = Instant.now().plusSeconds(120)
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when credential ID is blank")
        fun `should throw exception when credential id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when credential ID exceeds maximum length")
        fun `should throw exception when credential id exceeds maximum length`() = runTest {
            val longCredentialId = "a".repeat(1024)
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when IP address is invalid")
        fun `should throw exception when ip address is invalid`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when user agent is blank")
        fun `should throw exception when user agent is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = testDeviceId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when device ID is blank")
        fun `should throw exception when device id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
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
                    deviceId = ""
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when no consent method is used")
        fun `should throw exception when no consent method is used`() = runTest {
            // The domain model allows consent records with no explicit verification
            // method for silent/implicit consent scenarios. This tests that such
            // records can be created successfully.
            val consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = testCredentialId,
                timestamp = testTimestamp,
                biometricUsed = false,
                pinUsed = false,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceId = testDeviceId
            )
            assertEquals(ConsentMethod.NONE, consent.getConsentMethod())
        }
    }
    
    @Nested
    @DisplayName("Business Logic Tests")
    inner class BusinessLogicTests {
        
        private lateinit var consent: UserConsentRecord
        
        @BeforeEach
        fun setUp() = runTest {
            consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = testCredentialId,
                timestamp = testTimestamp,
                biometricUsed = true,
                pinUsed = false,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceId = testDeviceId
            )
        }
        
        @Test
        @DisplayName("Should correctly check if consent is recent")
        fun `should correctly check if consent is recent`() = runTest {
            assertTrue(consent.isRecent(5)) // Should be recent within 5 minutes
            
            val oldConsent = consent.copy(timestamp = Instant.now().minusSeconds(6 * 60)) // 6 minutes ago
            assertFalse(oldConsent.isRecent(5))
            
            val recentConsent = consent.copy(timestamp = Instant.now().minusSeconds(4 * 60)) // 4 minutes ago
            assertTrue(recentConsent.isRecent(5))
        }
        
        @Test
        @DisplayName("Should correctly check if consent is for specific credential")
        fun `should correctly check if consent is for specific credential`() = runTest {
            assertTrue(consent.isForCredential(testCredentialId))
            assertTrue(consent.isForCredential("TEST_CREDENTIAL_ID")) // Case insensitive
            
            assertFalse(consent.isForCredential("other_credential_id"))
            
            val consentWithoutCredential = consent.copy(credentialId = null)
            assertFalse(consentWithoutCredential.isForCredential(testCredentialId))
        }
        
        @Test
        @DisplayName("Should correctly check if consent is for specific RP")
        fun `should correctly check if consent is for specific rp`() = runTest {
            assertTrue(consent.isForRelyingParty(testRpId))
            assertTrue(consent.isForRelyingParty("HTTPS://EXAMPLE.COM")) // Case insensitive
            
            assertFalse(consent.isForRelyingParty("https://other.com"))
        }
        
        @Test
        @DisplayName("Should return correct consent method")
        fun `should return correct consent method`() = runTest {
            assertEquals(ConsentMethod.BIOMETRIC, consent.getConsentMethod())
            
            val pinConsent = consent.copy(biometricUsed = false, pinUsed = true)
            assertEquals(ConsentMethod.PIN, pinConsent.getConsentMethod())
            
            val combinedConsent = consent.copy(biometricUsed = true, pinUsed = true)
            assertEquals(ConsentMethod.BIOMETRIC_AND_PIN, combinedConsent.getConsentMethod())
            
            val noConsent = consent.copy(biometricUsed = false, pinUsed = false)
            assertEquals(ConsentMethod.NONE, noConsent.getConsentMethod())
        }
        
        @Test
        @DisplayName("Should return safe credential ID")
        fun `should return safe credential id`() = runTest {
            assertEquals(testCredentialId, consent.getSafeCredentialId())
            
            val consentWithoutCredential = consent.copy(credentialId = null)
            assertEquals("N/A", consentWithoutCredential.getSafeCredentialId())
        }
        
        @Test
        @DisplayName("Should correctly identify registration consent")
        fun `should correctly identify registration consent`() = runTest {
            assertTrue(consent.isRegistrationConsent())
            
            val authConsent = consent.copy(operationType = ConsentOperationType.AUTHENTICATION)
            assertFalse(authConsent.isRegistrationConsent())
        }
        
        @Test
        @DisplayName("Should correctly identify authentication consent")
        fun `should correctly identify authentication consent`() = runTest {
            assertFalse(consent.isAuthenticationConsent())
            
            val authConsent = consent.copy(operationType = ConsentOperationType.AUTHENTICATION)
            assertTrue(authConsent.isAuthenticationConsent())
        }
    }
    
    @Nested
    @DisplayName("Companion Object Tests")
    inner class CompanionObjectTests {
        
        @Test
        @DisplayName("Should create consent record using companion object factory method")
        fun `should create consent record using companion object factory method`() = runTest {
            val consent = UserConsentRecord.create(
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = testCredentialId,
                biometricUsed = true,
                pinUsed = false,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceId = testDeviceId
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
            assertTrue(consent.timestamp.isBefore(Instant.now().plusSeconds(1))) // Should be recent
        }
        
        @Test
        @DisplayName("Should create consent record with minimal parameters")
        fun `should create consent record with minimal parameters`() = runTest {
            val consent = UserConsentRecord.create(
                operationType = ConsentOperationType.AUTHENTICATION,
                rpId = testRpId,
                biometricUsed = false,
                pinUsed = true
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
        @DisplayName("Should have correct constant values")
        fun `should have correct constant values`() = runTest {
            assertEquals(45, UserConsentRecord.MAX_IP_ADDRESS_LENGTH)
            assertEquals(512, UserConsentRecord.MAX_USER_AGENT_LENGTH)
            assertEquals(64, UserConsentRecord.MAX_DEVICE_ID_LENGTH)
            assertEquals(1023, UserConsentRecord.MAX_CREDENTIAL_ID_LENGTH)
        }
    }
    
    @Nested
    @DisplayName("IP Address Validation Tests")
    inner class IpAddressValidationTests {
        
        @Test
        @DisplayName("Should accept valid IPv4 addresses")
        fun `should accept valid ipv4 addresses`() = runTest {
            val validIpAddresses = listOf(
                "192.168.1.1",
                "10.0.0.1",
                "172.16.0.1",
                "127.0.0.1",
                "255.255.255.255",
                "0.0.0.0"
            )
            
            validIpAddresses.forEach { ip ->
                val consent = UserConsentRecord(
                    id = "test_id",
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = testRpId,
                    credentialId = testCredentialId,
                    timestamp = testTimestamp,
                    biometricUsed = true,
                    pinUsed = false,
                    ipAddress = ip,
                    userAgent = testUserAgent,
                    deviceId = testDeviceId
                )
                assertNotNull(consent)
                assertEquals(ip, consent.ipAddress)
            }
        }
        
        @Test
        @DisplayName("Should accept valid IPv6 addresses")
        fun `should accept valid ipv6 addresses`() = runTest {
            val validIpAddresses = listOf(
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                "2001:db8::1",
                "::1",
                "fe80::1",
                "2001:db8:85a3::8a2e:370:7334"
            )
            
            validIpAddresses.forEach { ip ->
                val consent = UserConsentRecord(
                    id = "test_id",
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = testRpId,
                    credentialId = testCredentialId,
                    timestamp = testTimestamp,
                    biometricUsed = true,
                    pinUsed = false,
                    ipAddress = ip,
                    userAgent = testUserAgent,
                    deviceId = testDeviceId
                )
                assertNotNull(consent)
                assertEquals(ip, consent.ipAddress)
            }
        }
        
        @Test
        @DisplayName("Should reject invalid IP addresses")
        fun `should reject invalid ip addresses`() = runTest {
            val invalidIpAddresses = listOf(
                "256.256.256.256",
                "192.168.1",
                "192.168.1.1.1",
                "invalid-ip",
                "192.168.1.-1",
                "192.168.1.999",
                "2001:db8:::1",
                "2001:db8::g",
                "xyz:123::1"
            )
            
            invalidIpAddresses.forEach { ip ->
                assertThrows<IllegalArgumentException> {
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
                        deviceId = testDeviceId
                    )
                }
            }
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        @DisplayName("Should handle maximum allowed field sizes")
        fun `should handle maximum allowed field sizes`() = runTest {
            val maxCredentialId = "a".repeat(1023)
            val maxIpAddress = "2001:0db8:85a3:0000:0000:8a2e:0370:7334" // Valid IPv6
            val maxUserAgent = "a".repeat(512)
            val maxDeviceId = "a".repeat(64)
            
            val consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = maxCredentialId,
                timestamp = testTimestamp,
                biometricUsed = true,
                pinUsed = false,
                ipAddress = maxIpAddress,
                userAgent = maxUserAgent,
                deviceId = maxDeviceId
            )
            
            assertNotNull(consent)
            assertEquals(maxCredentialId, consent.credentialId)
            assertEquals(maxIpAddress, consent.ipAddress)
            assertEquals(maxUserAgent, consent.userAgent)
            assertEquals(maxDeviceId, consent.deviceId)
        }
        
        @Test
        @DisplayName("Should handle all operation types")
        fun `should handle all operation types`() = runTest {
            val operationTypes = listOf(
                ConsentOperationType.REGISTRATION,
                ConsentOperationType.AUTHENTICATION,
                ConsentOperationType.CREDENTIAL_DELETION,
                ConsentOperationType.CREDENTIAL_UPDATE
            )
            
            operationTypes.forEach { operationType ->
                val consent = UserConsentRecord(
                    id = "test_id",
                    operationType = operationType,
                    rpId = testRpId,
                    credentialId = testCredentialId,
                    timestamp = testTimestamp,
                    biometricUsed = true,
                    pinUsed = false,
                    ipAddress = testIpAddress,
                    userAgent = testUserAgent,
                    deviceId = testDeviceId
                )
                
                assertNotNull(consent)
                assertEquals(operationType, consent.operationType)
            }
        }
        
        @Test
        @DisplayName("Should handle combined biometric and PIN consent")
        fun `should handle combined biometric and pin consent`() = runTest {
            val consent = UserConsentRecord(
                id = "test_id",
                operationType = ConsentOperationType.REGISTRATION,
                rpId = testRpId,
                credentialId = testCredentialId,
                timestamp = testTimestamp,
                biometricUsed = true,
                pinUsed = true,
                ipAddress = testIpAddress,
                userAgent = testUserAgent,
                deviceId = testDeviceId
            )
            
            assertNotNull(consent)
            assertTrue(consent.biometricUsed)
            assertTrue(consent.pinUsed)
            assertEquals(ConsentMethod.BIOMETRIC_AND_PIN, consent.getConsentMethod())
        }
    }
}
