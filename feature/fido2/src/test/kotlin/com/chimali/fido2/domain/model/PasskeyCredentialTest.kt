package com.chimali.fido2.domain.model

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.*

@DisplayName("PasskeyCredential Domain Model Tests")
class PasskeyCredentialTest {
    
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testPrivateKeyAlias: String
    private lateinit var testAaguid: ByteArray
    private lateinit var testCredentialId: ByteArray
    private lateinit var testTimestamp: Instant
    
    @BeforeEach
    fun setUp() = runTest {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        testPublicKey = keyPairGenerator.generateKeyPair().public
        testPrivateKeyAlias = "test_private_key_alias"
        testAaguid = ByteArray(16) { it.toByte() }
        testCredentialId = "test_credential_id".toByteArray()
        testTimestamp = Instant.now()
    }
    
    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {
        
        @Test
        @DisplayName("Should create valid credential with all required fields")
        fun `should create valid credential with all required fields`() = runTest {
            val credential = PasskeyCredential(
                id = "test_id",
                rpId = "https://example.com",
                userId = "user123",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = testPublicKey,
                privateKeyAlias = testPrivateKeyAlias,
                signCount = 0L,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp,
                aaguid = testAaguid,
                credentialId = testCredentialId
            )
            
            assertNotNull(credential)
            assertEquals("test_id", credential.id)
            assertEquals("https://example.com", credential.rpId)
            assertEquals("user123", credential.userId)
            assertEquals("testuser", credential.userName)
            assertEquals("Test User", credential.userDisplayName)
            assertEquals(testPublicKey, credential.publicKey)
            assertEquals(testPrivateKeyAlias, credential.privateKeyAlias)
            assertEquals(0L, credential.signCount)
            assertEquals(testTimestamp, credential.createdAt)
            assertEquals(testTimestamp, credential.lastUsedAt)
            assertArrayEquals(testAaguid, credential.aaguid)
            assertArrayEquals(testCredentialId, credential.credentialId)
        }
        
        @Test
        @DisplayName("Should throw exception when ID is blank")
        fun `should throw exception when id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = testAaguid,
                    credentialId = testCredentialId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when RP ID is invalid")
        fun `should throw exception when rp id is invalid`() = runTest {
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "ftp://example.com/invalid",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = testAaguid,
                    credentialId = testCredentialId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when user ID exceeds maximum length")
        fun `should throw exception when user id exceeds maximum length`() = runTest {
            val longUserId = "a".repeat(65)
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "https://example.com",
                    userId = longUserId,
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = testAaguid,
                    credentialId = testCredentialId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when AAGUID size is incorrect")
        fun `should throw exception when aaguid size is incorrect`() = runTest {
            val wrongSizeAaguid = ByteArray(15) { it.toByte() }
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = wrongSizeAaguid,
                    credentialId = testCredentialId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when credential ID is empty")
        fun `should throw exception when credential id is empty`() = runTest {
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = testAaguid,
                    credentialId = ByteArray(0)
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when sign count is negative")
        fun `should throw exception when sign count is negative`() = runTest {
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = -1L,
                    createdAt = testTimestamp,
                    lastUsedAt = testTimestamp,
                    aaguid = testAaguid,
                    credentialId = testCredentialId
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when last used time is before creation time")
        fun `should throw exception when last used time is before creation time`() = runTest {
            val pastTimestamp = testTimestamp.minusSeconds(60)
            assertThrows<IllegalArgumentException> {
                PasskeyCredential(
                    id = "test_id",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = testPrivateKeyAlias,
                    signCount = 0L,
                    createdAt = testTimestamp,
                    lastUsedAt = pastTimestamp,
                    aaguid = testAaguid,
                    credentialId = testCredentialId
                )
            }
        }
    }
    
    @Nested
    @DisplayName("Business Logic Tests")
    inner class BusinessLogicTests {
        
        private lateinit var credential: PasskeyCredential
        
        @BeforeEach
        fun setUp() = runTest {
            credential = PasskeyCredential(
                id = "test_id",
                rpId = "https://example.com",
                userId = "user123",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = testPublicKey,
                privateKeyAlias = testPrivateKeyAlias,
                signCount = 0L,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp,
                aaguid = testAaguid,
                credentialId = testCredentialId
            )
        }
        
        @Test
        @DisplayName("Should correctly check if credential is expired")
        fun `should correctly check if credential is expired`() = runTest {
            val oldTimestamp = Instant.now().minusSeconds(800 * 24 * 60 * 60) // 800 days ago
            val oldCredential = credential.copy(createdAt = oldTimestamp, lastUsedAt = oldTimestamp.plusSeconds(30))
            
            assertTrue(oldCredential.isExpired(730)) // Should be expired with 730 days limit
            assertFalse(oldCredential.isExpired(1000)) // Should not be expired with 1000 days limit
            assertFalse(credential.isExpired(730)) // Current credential should not be expired
        }
        
        @Test
        @DisplayName("Should correctly check if credential belongs to relying party")
        fun `should correctly check if credential belongs to relying party`() = runTest {
            assertTrue(credential.belongsToRelyingParty("https://example.com"))
            assertTrue(credential.belongsToRelyingParty("https://example.com/"))
            assertTrue(credential.belongsToRelyingParty("HTTPS://EXAMPLE.COM")) // Case insensitive
            assertFalse(credential.belongsToRelyingParty("https://other.com"))
        }
        
        @Test
        @DisplayName("Should correctly check if credential belongs to user")
        fun `should correctly check if credential belongs to user`() = runTest {
            assertTrue(credential.belongsToUser("user123"))
            assertTrue(credential.belongsToUser("USER123")) // Case insensitive
            assertFalse(credential.belongsToUser("otheruser"))
        }
        
        @Test
        @DisplayName("Should return safe display name")
        fun `should return safe display name`() = runTest {
            assertEquals("Test User", credential.getSafeDisplayName())
        }
        
        @Test
        @DisplayName("Should return correct age in days")
        fun `should return correct age in days`() = runTest {
            val age = credential.getAgeInDays()
            assertTrue(age >= 0) // Should be non-negative
            assertTrue(age <= 1) // Should be very recent
        }
        
        @Test
        @DisplayName("Should create credential with updated sign count")
        fun `should create credential with updated sign count`() = runTest {
            val updatedCredential = credential.withSignCount(5L)
            
            assertEquals(5L, updatedCredential.signCount)
            assertEquals(credential.id, updatedCredential.id)
            assertEquals(credential.rpId, updatedCredential.rpId)
            assertNotEquals(credential.lastUsedAt, updatedCredential.lastUsedAt) // Should be updated
        }
        
        @Test
        @DisplayName("Should create credential with updated last used time")
        fun `should create credential with updated last used time`() = runTest {
            val newLastUsedAt = credential.createdAt.plusSeconds(30)
            val updatedCredential = credential.withLastUsedAt(newLastUsedAt)
            
            assertEquals(newLastUsedAt, updatedCredential.lastUsedAt)
            assertEquals(credential.id, updatedCredential.id)
            assertEquals(credential.rpId, updatedCredential.rpId)
        }
    }
    
    @Nested
    @DisplayName("Companion Object Tests")
    inner class CompanionObjectTests {
        
        @Test
        @DisplayName("Should create credential using companion object factory method")
        fun `should create credential using companion object factory method`() = runTest {
            val credential = PasskeyCredential.create(
                id = "test_id",
                rpId = "https://example.com",
                userId = "user123",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = testPublicKey,
                privateKeyAlias = testPrivateKeyAlias,
                aaguid = testAaguid,
                credentialId = testCredentialId
            )
            
            assertNotNull(credential)
            assertEquals("test_id", credential.id)
            assertEquals(0L, credential.signCount) // Should start at 0
            assertEquals(credential.createdAt, credential.lastUsedAt) // Should be same initially
        }
        
        @Test
        @DisplayName("Should have correct constant values")
        fun `should have correct constant values`() = runTest {
            assertEquals(64, PasskeyCredential.MAX_USER_ID_LENGTH)
            assertEquals(64, PasskeyCredential.MAX_NAME_LENGTH)
            assertEquals(64, PasskeyCredential.MAX_DISPLAY_NAME_LENGTH)
            assertEquals(1023, PasskeyCredential.MAX_CREDENTIAL_ID_LENGTH)
            assertEquals(16, PasskeyCredential.AAGUID_LENGTH)
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        @DisplayName("Should handle maximum allowed field sizes")
        fun `should handle maximum allowed field sizes`() = runTest {
            val maxUserId = "a".repeat(64)
            val maxUserName = "a".repeat(64)
            val maxDisplayName = "a".repeat(64)
            val maxCredentialId = ByteArray(1023) { it.toByte() }
            
            val credential = PasskeyCredential(
                id = "test_id",
                rpId = "https://example.com",
                userId = maxUserId,
                userName = maxUserName,
                userDisplayName = maxDisplayName,
                publicKey = testPublicKey,
                privateKeyAlias = testPrivateKeyAlias,
                signCount = 0L,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp,
                aaguid = testAaguid,
                credentialId = maxCredentialId
            )
            
            assertNotNull(credential)
            assertEquals(maxUserId, credential.userId)
            assertEquals(maxUserName, credential.userName)
            assertEquals(maxDisplayName, credential.userDisplayName)
            assertArrayEquals(maxCredentialId, credential.credentialId)
        }
        
        @Test
        @DisplayName("Should handle HTTP RP ID (not just HTTPS)")
        fun `should handle http rp id`() = runTest {
            val credential = PasskeyCredential(
                id = "test_id",
                rpId = "http://localhost:8080",
                userId = "user123",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = testPublicKey,
                privateKeyAlias = testPrivateKeyAlias,
                signCount = 0L,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp,
                aaguid = testAaguid,
                credentialId = testCredentialId
            )
            
            assertNotNull(credential)
            assertEquals("http://localhost:8080", credential.rpId)
        }
    }
}
