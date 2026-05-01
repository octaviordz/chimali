package com.chimali.fido2.domain.model

import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested

class PasskeyCredentialTest {
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testPrivateKeyAlias: String
    private lateinit var testAaguid: ByteArray
    private lateinit var testCredentialId: ByteArray
    private lateinit var testTimestamp: Instant

    @BeforeTest
    fun setUp() =
        runTest {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(EC_KEY_SIZE_256)
            testPublicKey = keyPairGenerator.generateKeyPair().public
            testPrivateKeyAlias = "test_private_key_alias"
            testAaguid = ByteArray(AAGUID_SIZE_16) { it.toByte() }
            testCredentialId = "dGVzdF9jcmVkZW50aWFsX2lk".toByteArray()
            testTimestamp = TimeProvider().now()
        }

    private companion object {
        private const val EC_KEY_SIZE_256 = 256
        private const val AAGUID_SIZE_16 = 16
        private const val INVALID_AAGUID_SIZE_15 = 15
        private const val USER_ID_MAX_PLUS_ONE = 65
        private const val DAYS_730 = 730L
        private const val DAYS_1000 = 1000L
        private const val SIGN_COUNT_5 = 5L
        private const val MAX_SIZE_64 = 64
        private const val MAX_CRED_ID_SIZE_1023 = 1023
    }

    @Nested
    inner class ValidationTests {
        @Test
        fun `should create valid credential with all required fields`() =
            runTest {
                val credential =
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals("dGVzdF9pZA", credential.id.encoded)
                assertEquals("https://example.com", credential.rpId.value)
                assertEquals("user123", credential.userId.value)
                assertEquals("testuser", credential.userName)
                assertEquals("Test User", credential.userDisplayName)
                assertEquals(testPublicKey, credential.publicKey)
                assertEquals(testPrivateKeyAlias, credential.privateKeyAlias)
                assertEquals(0L, credential.signCount)
                assertEquals(testTimestamp, credential.createdAt)
                assertEquals(testTimestamp, credential.lastUsedAt)
                assertContentEquals(testAaguid, credential.aaguid)
                assertContentEquals(testCredentialId, credential.credentialId)
            }

        @Test
        fun `should throw exception when id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded(""),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        @Test
        fun `should throw exception when rp id is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("ftp://example.com/invalid"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        @Test
        fun `should throw exception when user id exceeds maximum length`() =
            runTest {
                val longUserId = "a".repeat(USER_ID_MAX_PLUS_ONE)
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId(longUserId),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        @Test
        fun `should throw exception when aaguid size is incorrect`() =
            runTest {
                val wrongSizeAaguid = ByteArray(INVALID_AAGUID_SIZE_15) { it.toByte() }
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = wrongSizeAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        @Test
        fun `should throw exception when credential id is empty`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = ByteArray(0),
                    )
                }
            }

        @Test
        fun `should throw exception when sign count is negative`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = -1L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        @Test
        fun `should throw exception when last used time is before creation time`() =
            runTest {
                val pastTimestamp = testTimestamp - 60.seconds
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = pastTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }
    }

    @Nested
    inner class BusinessLogicTests {
        private lateinit var credential: PasskeyCredential

        @BeforeTest
        fun setUp() =
            runTest {
                credential =
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
            }

        @Test
        fun `should correctly check if credential is expired`() =
            runTest {
                val oldTimestamp = TimeProvider().now() - 800.days
                val oldCredential =
                    credential.copy(
                        createdAt = oldTimestamp,
                        lastUsedAt = oldTimestamp + 30.seconds,
                    )

                assertTrue(oldCredential.isExpired(DAYS_730)) // Should be expired with 730 days limit
                assertFalse(oldCredential.isExpired(DAYS_1000)) // Should not be expired with 1000 days limit
                assertFalse(credential.isExpired(DAYS_730)) // Current credential should not be expired
            }

        @Test
        fun `should correctly check if credential belongs to relying party`() =
            runTest {
                assertTrue(credential.belongsToRelyingParty(RpId("https://example.com")))
                assertTrue(credential.belongsToRelyingParty(RpId("https://example.com/")))
                assertTrue(credential.belongsToRelyingParty(RpId("HTTPS://EXAMPLE.COM"))) // Case insensitive
                assertFalse(credential.belongsToRelyingParty(RpId("https://other.com")))
            }

        @Test
        fun `should correctly check if credential belongs to user`() =
            runTest {
                assertTrue(credential.belongsToUser(UserId("user123")))
                assertTrue(credential.belongsToUser(UserId("USER123"))) // Case insensitive
                assertFalse(credential.belongsToUser(UserId("otheruser")))
            }

        @Test
        fun `should return safe display name`() =
            runTest {
                assertEquals("Test User", credential.getSafeDisplayName())
            }

        @Test
        fun `should return correct age in days`() =
            runTest {
                val age = credential.getAgeInDays()
                assertTrue(age >= 0) // Should be non-negative
                assertTrue(age <= 1) // Should be very recent
            }

        @Test
        fun `should create credential with updated sign count`() =
            runTest {
                val updatedCredential = credential.withSignCount(SIGN_COUNT_5)

                assertEquals(SIGN_COUNT_5, updatedCredential.signCount)
                assertEquals(credential.id.encoded, updatedCredential.id.encoded)
                assertEquals(credential.rpId.value, updatedCredential.rpId.value)
                assertNotEquals(credential.lastUsedAt, updatedCredential.lastUsedAt) // Should be updated
            }

        @Test
        fun `should create credential with updated last used time`() =
            runTest {
                val newLastUsedAt = credential.createdAt + 30.seconds
                val updatedCredential = credential.withLastUsedAt(newLastUsedAt)

                assertEquals(newLastUsedAt, updatedCredential.lastUsedAt)
                assertEquals(credential.id.encoded, updatedCredential.id.encoded)
                assertEquals(credential.rpId.value, updatedCredential.rpId.value)
            }
    }

    @Nested
    inner class CompanionObjectTests {
        @Test
        fun `should create credential using companion object factory method`() =
            runTest {
                val credential =
                    PasskeyCredential.create(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals("dGVzdF9pZA", credential.id.encoded)
                assertEquals(0L, credential.signCount) // Should start at 0
                assertEquals(credential.createdAt, credential.lastUsedAt) // Should be same initially
            }

        @Test
        fun `should have correct constant values`() =
            runTest {
                assertEquals(MAX_SIZE_64, PasskeyCredential.MAX_USER_ID_LENGTH)
                assertEquals(MAX_SIZE_64, PasskeyCredential.MAX_NAME_LENGTH)
                assertEquals(MAX_SIZE_64, PasskeyCredential.MAX_DISPLAY_NAME_LENGTH)
                assertEquals(MAX_CRED_ID_SIZE_1023, PasskeyCredential.MAX_CREDENTIAL_ID_LENGTH)
                assertEquals(AAGUID_SIZE_16, PasskeyCredential.AAGUID_LENGTH)
            }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle maximum allowed field sizes`() =
            runTest {
                val maxUserId = "a".repeat(MAX_SIZE_64)
                val maxUserName = "a".repeat(MAX_SIZE_64)
                val maxDisplayName = "a".repeat(MAX_SIZE_64)
                val maxCredentialId = ByteArray(MAX_CRED_ID_SIZE_1023) { it.toByte() }

                val credential =
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("https://example.com"),
                        userId = UserId(maxUserId),
                        userName = maxUserName,
                        userDisplayName = maxDisplayName,
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = maxCredentialId,
                    )

                assertNotNull(credential)
                assertEquals(maxUserId, credential.userId.value)
                assertEquals(maxUserName, credential.userName)
                assertEquals(maxDisplayName, credential.userDisplayName)
                assertContentEquals(maxCredentialId, credential.credentialId)
            }

        @Test
        fun `should handle http rp id`() =
            runTest {
                val credential =
                    PasskeyCredential(
                        id = CredentialId.fromEncoded("dGVzdF9pZA"),
                        rpId = RpId("http://localhost:8080"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        privateKeyAlias = testPrivateKeyAlias,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals("http://localhost:8080", credential.rpId.value)
            }
    }
}
