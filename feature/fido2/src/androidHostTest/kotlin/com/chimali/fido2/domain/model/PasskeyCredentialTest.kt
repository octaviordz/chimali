package com.chimali.fido2.domain.model
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.testing.testCredentialIdFromLabel
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

@Req("NFR-SEC-041")
class PasskeyCredentialTest {
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testAaguid: ByteArray
    private lateinit var testCredentialId: ByteArray
    private lateinit var testTimestamp: Instant

    @BeforeTest
    fun setUp() =
        runTest {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(EC_KEY_SIZE_256)
            testPublicKey = keyPairGenerator.generateKeyPair().public
            testAaguid = ByteArray(AAGUID_SIZE_16) { it.toByte() }
            testCredentialId = testCredentialIdFromLabel("passkey-credential-test").toByteArray()
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `independent credential copy keeps UI metadata after transport owner erasure`() {
        val transportOwner =
            PasskeyCredential.create(
                id = testCredentialIdFromLabel("passkey-credential-test"),
                rpId = RpId("https://example.com"),
                userId = UserId("user123"),
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = testPublicKey,
                aaguid = testAaguid,
                credentialId = testCredentialId,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )
        val uiOwner = transportOwner.copyWithIndependentSensitiveText()

        transportOwner.eraseSensitiveText()

        assertEquals("https://example.com", uiOwner.rpId.toBoundaryString())
        assertEquals("user123", uiOwner.userId.toBoundaryString())
        assertEquals("testuser", uiOwner.userName.toBoundaryString())
        assertEquals("Test User", uiOwner.userDisplayName.toBoundaryString())
        assertNotEquals(transportOwner.rpId, uiOwner.rpId)
        assertContentEquals(testAaguid, uiOwner.aaguid)
        assertContentEquals(testCredentialId, uiOwner.credentialId)
        uiOwner.eraseSensitiveText()
    }

    @Nested
    inner class ValidationTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should create valid credential with all required fields`() =
            runTest {
                val credential =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals(testCredentialIdFromLabel("passkey-credential-test").encoded, credential.id.encoded)
                assertEquals("https://example.com", credential.rpId.toBoundaryString())
                assertEquals("user123", credential.userId.toBoundaryString())
                assertEquals("testuser", credential.userName.toBoundaryString())
                assertEquals("Test User", credential.userDisplayName.toBoundaryString())
                assertEquals(testPublicKey, credential.publicKey)
                assertEquals(0L, credential.signCount)
                assertEquals(testTimestamp, credential.createdAt)
                assertEquals(testTimestamp, credential.lastUsedAt)
                assertContentEquals(testAaguid, credential.aaguid)
                assertContentEquals(testCredentialId, credential.credentialId)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `unsupported credential protection policy is rejected by the credential model`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                        credProtectPolicy = 4,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
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
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when rp id is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("ftp://example.com/invalid"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when user id exceeds maximum length`() =
            runTest {
                val longUserId = "a".repeat(USER_ID_MAX_PLUS_ONE)
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("https://example.com"),
                        userId = UserId(longUserId),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when aaguid size is incorrect`() =
            runTest {
                val wrongSizeAaguid = ByteArray(INVALID_AAGUID_SIZE_15) { it.toByte() }
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = wrongSizeAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when credential id is empty`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = ByteArray(0),
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when sign count is negative`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = -1L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should throw exception when last used time is before creation time`() =
            runTest {
                val pastTimestamp = testTimestamp - 60.seconds
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = pastTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `validate() should reject deprecated COSE_ED25519 (-19) and accept COSE_EDSA (-8)`() =
            runTest {
                // Testing rejection of -19 (deprecated)
                assertFailsWith<IllegalArgumentException>(message = "Should reject COSE -19") {
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                        // Current COSE_ED25519
                        coseAlgorithm = -19,
                    )
                }

                // Testing acceptance of -8 (new mandatory EdDSA)
                PasskeyCredential.create(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("https://example.com"),
                    userId = UserId("user123"),
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    derivationProfile = CredentialKeyDescriptor.ED25519_PROFILE,
                    aaguid = testAaguid,
                    credentialId = testCredentialId,
                    // New COSE_EDSA
                    coseAlgorithm = -8,
                )
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `profile must match algorithm and stored key encoding is returned as an owned copy`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("profile-mismatch"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.ED25519_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                        coseAlgorithm = PasskeyCredential.COSE_ES256,
                    )
                }

                val credential =
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("stored-public-key-copy"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                val original = credential.publicKey.encoded.copyOf()
                credential.storedPublicKeyEncoding.fill(0)

                assertContentEquals(original, credential.storedPublicKeyEncoding)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `validate() enforces byte-level UTF-8 length limits for names`() =
            runTest {
                // T011: 64-byte ASCII displayName -> passes
                PasskeyCredential.create(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("example.com"),
                    userId = UserId("user123"),
                    userName = "a".repeat(64),
                    userDisplayName = "a".repeat(64),
                    publicKey = testPublicKey,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    aaguid = testAaguid,
                    credentialId = testCredentialId,
                )

                // T011: 65-byte ASCII displayName -> fails
                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("example.com"),
                        userId = UserId("user123"),
                        userName = "a".repeat(65),
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }

                // T011: Multi-byte UTF-8 validation
                // '€' is 3 bytes in UTF-8
                val string63 = "€".repeat(21) // 63 bytes
                val string66 = "€".repeat(22) // 66 bytes

                PasskeyCredential.create(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("example.com"),
                    userId = UserId("user123"),
                    userName = string63,
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    aaguid = testAaguid,
                    credentialId = testCredentialId,
                )

                assertFailsWith<IllegalArgumentException> {
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("example.com"),
                        userId = UserId("user123"),
                        userName = string66,
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
                }
            }
    }

    @Test
    fun `credential copies own independent erasable metadata and redact diagnostics`() =
        runTest {
            val original =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("sensitive-copy-owner"),
                    rpId = RpId("synthetic.example"),
                    userName = "synthetic-user-name",
                )
            val copied = original.copy()

            original.eraseSensitiveText()

            assertEquals("\u0000".repeat("synthetic.example".length), original.rpId.toBoundaryString())
            assertEquals("synthetic.example", copied.rpId.toBoundaryString())
            assertEquals("synthetic-user-name", copied.userName.toBoundaryString())
            assertFalse(original.toString().contains("synthetic"))
            assertFalse(copied.toString().contains("synthetic"))
        }

    @Nested
    inner class BusinessLogicTests {
        private lateinit var credential: PasskeyCredential

        @BeforeTest
        fun setUp() =
            runTest {
                credential =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
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

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should correctly check if credential belongs to relying party`() =
            runTest {
                assertTrue(credential.belongsToRelyingParty(RpId("https://example.com")))
                assertTrue(credential.belongsToRelyingParty(RpId("https://example.com/")))
                assertTrue(credential.belongsToRelyingParty(RpId("HTTPS://EXAMPLE.COM"))) // Case insensitive
                assertFalse(credential.belongsToRelyingParty(RpId("https://other.com")))
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should correctly check if credential belongs to user`() =
            runTest {
                assertTrue(credential.belongsToUser(UserId("user123")))
                assertTrue(credential.belongsToUser(UserId("USER123"))) // Case insensitive
                assertFalse(credential.belongsToUser(UserId("otheruser")))
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should return safe display name`() =
            runTest {
                assertEquals("Test User", credential.getSafeDisplayName())
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should return correct age in days`() =
            runTest {
                val age = credential.getAgeInDays()
                assertTrue(age >= 0) // Should be non-negative
                assertTrue(age <= 1) // Should be very recent
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should create credential with updated sign count`() =
            runTest {
                val original = credential.copy(createdAt = testTimestamp - 1.days, lastUsedAt = testTimestamp - 1.days)
                val before = TimeProvider().now()
                val updatedCredential = original.withSignCount(SIGN_COUNT_5)
                val after = TimeProvider().now()

                assertEquals(SIGN_COUNT_5, updatedCredential.signCount)
                assertEquals(original.id.encoded, updatedCredential.id.encoded)
                assertEquals(original.rpId.toBoundaryString(), updatedCredential.rpId.toBoundaryString())
                assertNotEquals(original.lastUsedAt, updatedCredential.lastUsedAt)
                assertTrue(updatedCredential.lastUsedAt in before..after)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should create credential with updated last used time`() =
            runTest {
                val newLastUsedAt = credential.createdAt + 30.seconds
                val updatedCredential = credential.withLastUsedAt(newLastUsedAt)

                assertEquals(newLastUsedAt, updatedCredential.lastUsedAt)
                assertEquals(credential.id.encoded, updatedCredential.id.encoded)
                assertEquals(credential.rpId.toBoundaryString(), updatedCredential.rpId.toBoundaryString())
            }
    }

    @Nested
    inner class CompanionObjectTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should create credential using companion object factory method`() =
            runTest {
                val credential =
                    PasskeyCredential.create(
                        id = testCredentialIdFromLabel("passkey-credential-test"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals(testCredentialIdFromLabel("passkey-credential-test").encoded, credential.id.encoded)
                assertEquals(0L, credential.signCount) // Should start at 0
                assertEquals(credential.createdAt, credential.lastUsedAt) // Should be same initially
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
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
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should handle maximum allowed field sizes`() =
            runTest {
                val maxUserId = "a".repeat(MAX_SIZE_64)
                val maxUserName = "a".repeat(MAX_SIZE_64)
                val maxDisplayName = "a".repeat(MAX_SIZE_64)
                val maxCredentialId = ByteArray(MAX_CRED_ID_SIZE_1023) { it.toByte() }

                val credential =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId(maxUserId),
                        userName = maxUserName,
                        userDisplayName = maxDisplayName,
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = maxCredentialId,
                    )

                assertNotNull(credential)
                assertEquals(maxUserId, credential.userId.toBoundaryString())
                assertEquals(maxUserName, credential.userName.toBoundaryString())
                assertEquals(maxDisplayName, credential.userDisplayName.toBoundaryString())
                assertContentEquals(maxCredentialId, credential.credentialId)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should handle http rp id`() =
            runTest {
                val credential =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("http://localhost:8080"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 0L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                    )

                assertNotNull(credential)
                assertEquals("http://localhost:8080", credential.rpId.toBoundaryString())
            }
    }

    @Nested
    inner class EqualsAndHashCodeTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `equals and hashCode cover all properties and edge cases`() =
            runTest {
                val cred1 =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 10L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                        credProtectPolicy = 1,
                        label = "personal",
                    )
                val same =
                    PasskeyCredential(
                        id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                        rpId = RpId("https://example.com"),
                        userId = UserId("user123"),
                        userName = "testuser",
                        userDisplayName = "Test User",
                        publicKey = testPublicKey,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                        signCount = 10L,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                        aaguid = testAaguid,
                        credentialId = testCredentialId,
                        credProtectPolicy = 1,
                        label = "personal",
                    )

                assertEquals(cred1, cred1)
                assertEquals(cred1, same)
                assertEquals(cred1.hashCode(), same.hashCode())
                val nullCred: Any? = null
                assertFalse(cred1 == nullCred)
                assertFalse(cred1.equals("not a passkey credential"))

                // Differing ID
                assertFalse(cred1 == cred1.copy(id = testCredentialIdFromLabel("b3RoZXJfaWQA")))
                // Differing RP
                assertFalse(cred1 == cred1.copy(rpId = SensitiveCredentialText.copyOf("https://other.com")))
                // Differing User ID
                assertFalse(cred1 == cred1.copy(userId = SensitiveCredentialText.copyOf("otheruser")))
                // Differing User Name
                assertFalse(cred1 == cred1.copy(userName = SensitiveCredentialText.copyOf("othername")))
                // Differing User Display Name
                assertFalse(cred1 == cred1.copy(userDisplayName = SensitiveCredentialText.copyOf("Other Display")))
                // Differing sign count
                assertFalse(cred1 == cred1.copy(signCount = 99L))
                // Differing createdAt
                assertFalse(cred1 == cred1.copy(createdAt = testTimestamp - 100.seconds))
                // Differing lastUsedAt
                assertFalse(cred1 == cred1.copy(lastUsedAt = testTimestamp + 10.seconds))
                // Differing aaguid
                assertFalse(cred1 == cred1.copy(aaguid = ByteArray(16) { 0xFF.toByte() }))
                // Differing credentialId
                assertFalse(cred1 == cred1.copy(credentialId = byteArrayOf(1, 2, 9)))
                // Differing credProtectPolicy
                assertFalse(cred1 == cred1.copy(credProtectPolicy = 2))
                // Differing label
                assertFalse(cred1 == cred1.copy(label = "work"))
                assertFalse(cred1 == cred1.copy(label = null))

                // Label hash code null vs non-null
                val credNullLabel = cred1.copy(label = null)
                val credNullLabel2 = cred1.copy(label = null)
                assertEquals(credNullLabel.hashCode(), credNullLabel2.hashCode())
                assertNotEquals(cred1.hashCode(), credNullLabel.hashCode())

                // Belongs to RP
                assertTrue(cred1.belongsToRelyingParty(RpId("https://example.com")))
                assertFalse(cred1.belongsToRelyingParty(RpId("https://wrong.com")))

                // Belongs to User
                assertTrue(cred1.belongsToUser(UserId("user123")))
                assertFalse(cred1.belongsToUser(UserId("otheruser")))

                // Safe display name
                assertEquals("Test User", cred1.getSafeDisplayName())

                // Is expired
                assertFalse(cred1.isExpired(365L))
                assertTrue(cred1.isExpired(0L))
            }
    }
}
