package com.chimali.fido2.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * T023 [US2] + T049 [US4] — Credential repository lookup, partial-search regression,
 * and tampered encrypted metadata rejection tests.
 *
 * Verifies that:
 * - Exact-match lookups via lookup tokens return expected credentials
 * - Partial text search on display fields works independently of token matching
 * - Tampered encrypted metadata is handled gracefully during hydration
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialRepositorySearchableMetadataTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Fido2Database
    private lateinit var repository: CredentialRepositoryImpl
    private lateinit var metadataProtectionService: CredentialMetadataProtectionService
    private lateinit var passkeyDao: PasskeyCredentialDao
    private lateinit var rpDao: RelyingPartyDao
    private lateinit var consentDao: UserConsentRecordDao
    private lateinit var publicKeyDecoder: PublicKeyDecoder
    private val generatedLookupIndexes = mutableListOf<ByteArray>()

    private val timeProvider = TimeProvider()

    private companion object {
        private const val KEY_SIZE_256 = 256
        private val RP_ID_INDEX = byteArrayOf(1, 2, 3)
        private val USER_ID_INDEX = byteArrayOf(4, 5, 6)
        private val ENCRYPTED_META = byteArrayOf(7, 8, 9, 10)
        private val TAMPERED_META = byteArrayOf(99, 99, 99, 99)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `real metadata codec hydrates unicode projections and preserves rollback on encryption failure`() =
        runTest {
            var failEncryption = false
            val plaintextOwners = mutableListOf<ByteArray>()
            val protection =
                CredentialMetadataProtectionService(
                    lookupTokenService =
                        mockk {
                            every { generateToken(any(), any()) } answers {
                                StringBuilder()
                                    .append(firstArg<CharSequence>())
                                    .append(':')
                                    .append(secondArg<CharSequence>())
                                    .toString()
                                    .toByteArray()
                            }
                        },
                    encryptedMetadataService =
                        mockk {
                            every { encrypt(any(), any()) } answers {
                                plaintextOwners.add(firstArg())
                                check(!failEncryption) { "synthetic encryption failure" }
                                firstArg<ByteArray>().copyOf()
                            }
                            every { decrypt(any(), any()) } answers {
                                firstArg<ByteArray>().copyOf().also { plaintextOwners.add(it) }
                            }
                        },
                )
            val dao = PasskeyCredentialDao(database, timeProvider, protection)
            val original =
                PasskeyCredential.create(
                    id = CredentialId.fromByteArray(ByteArray(32) { 3 }),
                    rpId = RpId("example.com"),
                    userId = UserId("用戶-\"\\\n"),
                    userName = "synthetic-user",
                    userDisplayName = "Synthetic User",
                    publicKey =
                        KeyPairGenerator
                            .getInstance("EC")
                            .apply { initialize(KEY_SIZE_256) }
                            .generateKeyPair()
                            .public,
                    aaguid = ByteArray(16),
                    credentialId = ByteArray(32) { 3 },
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            dao.replaceCredentialsForRpAndUser(original)
            val stored = requireNotNull(dao.getCredentialById(original.id))
            val restored = stored.toDomainModel(PublicKeyDecoder(), protection)
            assertTrue(restored is Outcome.Success)
            assertEquals(original.userId, restored.data.userId)
            assertEquals(original.rpId, restored.data.rpId)
            assertEquals(original.derivationProfile, restored.data.derivationProfile)
            assertContentEquals(original.publicKey.encoded, restored.data.publicKey.encoded)

            val replacement =
                original.copy(
                    id = CredentialId.fromByteArray(ByteArray(32) { 4 }),
                    credentialId = ByteArray(32) { 4 },
                )
            failEncryption = true
            assertFailsWith<IllegalStateException> { dao.replaceCredentialsForRpAndUser(replacement) }
            assertEquals(
                original.id.encoded,
                dao
                    .getCredentialsByRpId(original.rpId.toRpId())
                    .first()
                    .single()
                    .id,
            )
            plaintextOwners.forEach { assertContentEquals(ByteArray(it.size), it) }
        }

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)

        metadataProtectionService = mockk()
        every {
            metadataProtectionService.getRpIdIndex(
                match { it.contentEquals("example.com") || it.contentEquals("https://example.com") },
            )
        } answers { RP_ID_INDEX.copyOf().also(generatedLookupIndexes::add) }
        every {
            metadataProtectionService.getUserIdIndex(match { it.contentEquals("user-1") })
        } answers { USER_ID_INDEX.copyOf().also(generatedLookupIndexes::add) }
        every { metadataProtectionService.encryptCredentialMetadata(any(), any()) } returns ENCRYPTED_META
        every {
            metadataProtectionService.encryptRelyingPartyMetadata(any(), any())
        } returns byteArrayOf(20, 21)
        every {
            metadataProtectionService.decryptCredentialMetadata(
                ENCRYPTED_META,
                match { it.contentEquals("example.com") },
            )
        } answers {
            CredentialMetadataProtectionService.CredentialMetadata("example.com".toCharArray(), "user-1".toCharArray())
        }

        passkeyDao = PasskeyCredentialDao(database, timeProvider, metadataProtectionService)
        rpDao = RelyingPartyDao(database, timeProvider, metadataProtectionService)
        consentDao = UserConsentRecordDao(database, metadataProtectionService)

        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }.generateKeyPair()
        publicKeyDecoder =
            mockk {
                every { decodePublicKey(any(), any()) } returns Outcome.Success(keyPair.public)
            }

        val aggregateService: AggregateService<PasskeyCommand, PasskeyState> =
            mockk {
                coEvery { execute(any(), any()) } returns Result.success(mockk(relaxed = true))
            }

        repository =
            CredentialRepositoryImpl(
                passkeyCredentialDao = passkeyDao,
                relyingPartyDao = rpDao,
                userConsentRecordDao = consentDao,
                publicKeyDecoder = publicKeyDecoder,
                timeProvider = timeProvider,
                aggregateService = aggregateService,
                metadataProtectionService = metadataProtectionService,
            )
    }

    // --- T023: Lookup-token exact match tests ---

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `exact-match lookup by RP ID returns seeded credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val results = passkeyDao.getCredentialsByRpId(RpId("example.com")).first()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `exact-match lookup by user ID returns seeded credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val results = passkeyDao.getCredentialsByUserId(UserId("user-1")).first()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `duplicate check by RP and user ID returns existing credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val duplicates =
                passkeyDao.getCredentialsByRpIdAndUserId(
                    RpId("example.com"),
                    UserId("user-1"),
                )
            assertEquals(1, duplicates.size)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `DAO lookup index owners are erased after synchronous and flow queries`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val rpResults = passkeyDao.getCredentialsByRpId(RpId("example.com"))
            assertTrue(generatedLookupIndexes.isEmpty(), "Flow lookup tokens are created on collection")
            assertEquals(1, rpResults.first().size)
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            val userResults = passkeyDao.getCredentialsByUserId(UserId("user-1"))
            assertTrue(generatedLookupIndexes.isEmpty(), "Flow lookup tokens are created on collection")
            assertEquals(1, userResults.first().size)
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            assertEquals(
                1,
                passkeyDao.getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1")).size,
            )
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            assertEquals(1, passkeyDao.getPagedCredentialsByRpId(RpId("example.com"), limit = 10, offset = 0).size)
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            consentDao.insertConsent(
                UserConsentRecord(
                    id = "synthetic-consent-id",
                    operationType = ConsentOperationType.AUTHENTICATION,
                    rpId = RpId("https://example.com"),
                    credentialId = null,
                    timestamp = timeProvider.now(),
                    isBiometricUsed = false,
                    isPinUsed = false,
                    ipAddress = null,
                    userAgent = null,
                    deviceId = null,
                ),
            )
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            val consentFlow = consentDao.getRecentConsent(RpId("example.com"), limit = 10)
            assertTrue(generatedLookupIndexes.isEmpty(), "Flow lookup tokens are created on collection")
            assertEquals(1, consentFlow.first().size)
            assertLookupIndexesErased()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential lookup index owners are erased after query failures and partial acquisition`() =
        runTest {
            seedCredentialDirectly("cred-query-failure")
            driver.execute(null, "DROP TABLE passkey_credential", 0)

            assertFailsWith<Exception> {
                passkeyDao.getCredentialsByRpId(RpId("example.com")).first()
            }
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            assertFailsWith<Exception> {
                passkeyDao.getCredentialsByUserId(UserId("user-1")).first()
            }
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            assertFailsWith<Exception> {
                passkeyDao.getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
            }
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            assertFailsWith<Exception> {
                passkeyDao.getPagedCredentialsByRpId(RpId("example.com"), limit = 10, offset = 0)
            }
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            every {
                metadataProtectionService.getUserIdIndex(match { it.contentEquals("user-1") })
            } throws IllegalStateException("synthetic user-token failure")
            assertFailsWith<IllegalStateException> {
                passkeyDao.getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
            }
            assertLookupIndexesErased()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `filtered consent query owner is erased when query fails`() =
        runTest {
            driver.execute(null, "DROP TABLE user_consent_record", 0)

            assertFailsWith<Exception> {
                consentDao.getRecentConsent(RpId("example.com"), limit = 10).first()
            }

            assertLookupIndexesErased()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential and consent lookup index owners are erased when filtered flows are cancelled`() =
        runTest {
            seedCredentialDirectly("cred-flow-cancel")
            consentDao.insertConsent(syntheticConsent("consent-flow-cancel"))
            generatedLookupIndexes.clear()

            val rpJob =
                launch {
                    passkeyDao.getCredentialsByRpId(RpId("example.com")).collect {
                        awaitCancellation()
                    }
                }
            runCurrent()
            rpJob.cancelAndJoin()
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            val userJob =
                launch {
                    passkeyDao.getCredentialsByUserId(UserId("user-1")).collect {
                        awaitCancellation()
                    }
                }
            runCurrent()
            userJob.cancelAndJoin()
            assertLookupIndexesErased()

            generatedLookupIndexes.clear()
            val consentJob =
                launch {
                    consentDao.getRecentConsent(RpId("example.com"), limit = 10).collect {
                        awaitCancellation()
                    }
                }
            runCurrent()
            consentJob.cancelAndJoin()
            assertLookupIndexesErased()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent lookup index owner is erased when insertion fails`() =
        runTest {
            val consent = syntheticConsent("consent-duplicate")
            consentDao.insertConsent(consent)
            generatedLookupIndexes.clear()

            assertFailsWith<Exception> { consentDao.insertConsent(consent) }

            assertLookupIndexesErased()
        }

    private fun syntheticConsent(id: String) =
        UserConsentRecord(
            id = id,
            operationType = ConsentOperationType.AUTHENTICATION,
            rpId = RpId("https://example.com"),
            credentialId = null,
            timestamp = timeProvider.now(),
            isBiometricUsed = false,
            isPinUsed = false,
            ipAddress = null,
            userAgent = null,
            deviceId = null,
        )

    private fun assertLookupIndexesErased() {
        assertTrue(generatedLookupIndexes.isNotEmpty())
        generatedLookupIndexes.forEach { index -> assertContentEquals(ByteArray(index.size), index) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential summary rejects malformed algorithm profile metadata`() =
        runTest {
            seedCredentialDirectly(
                "bad-profile",
                rpId = "example.com",
                userId = "user-1",
                derivationProfile = "unrecognized-profile",
            )

            val result = repository.getCredentialSummariesForRp(RpId("example.com"))

            assertTrue(result is Outcome.Error)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential summary returns stored descriptor and key without public key hydration`() =
        runTest {
            seedCredentialDirectly("metadata-summary")
            val row = passkeyDao.getCredentialsByRpId(RpId("example.com")).first().single()

            val result = repository.getCredentialSummariesForRp(RpId("example.com"))

            assertTrue(result is Outcome.Success)
            val summary = result.data.single()
            assertEquals(row.id, summary.id)
            assertEquals(row.credential_id, summary.credentialId.encoded)
            assertEquals(row.cose_algorithm.toInt(), summary.coseAlgorithm)
            assertEquals(row.derivation_profile, summary.derivationProfile)
            assertContentEquals(
                java.util.Base64
                    .getDecoder()
                    .decode(row.public_key),
                summary.storedPublicKeyEncoding,
            )
            verify(exactly = 0) { publicKeyDecoder.decodePublicKey(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `projection replacement rolls back deletions when insertion preparation fails`() =
        runTest {
            seedCredentialDirectly("original", rpId = "example.com", userId = "user-1")
            val seeded =
                database.passkeyCredentialQueries
                    .select_all()
                    .executeAsList()
                    .single()
            assertContentEquals(RP_ID_INDEX, seeded.rp_id_index)
            assertContentEquals(USER_ID_INDEX, seeded.user_id_index)
            val original =
                passkeyDao
                    .getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
                    .single()
            val newId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 0x5a })
            val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }.generateKeyPair()
            val replacement =
                PasskeyCredential.create(
                    id = newId,
                    rpId = RpId("example.com"),
                    userId = UserId("user-1"),
                    userName = "replacement",
                    userDisplayName = "Replacement",
                    publicKey = keyPair.public,
                    aaguid = ByteArray(16),
                    credentialId = newId.toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            every { metadataProtectionService.encryptCredentialMetadata(any(), any()) } throws
                IllegalStateException("synthetic projection failure")

            assertFailsWith<IllegalStateException> {
                passkeyDao.replaceCredentialsForRpAndUser(replacement)
            }

            val afterFailure =
                passkeyDao
                    .getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
                    .single()
            assertEquals(original.id, afterFailure.id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `projection replacement rolls back deletions when cancelled before commit`() =
        runTest {
            seedCredentialDirectly("original", rpId = "example.com", userId = "user-1")
            val original =
                passkeyDao
                    .getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
                    .single()
            val newId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 0x6b })
            val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }.generateKeyPair()
            val replacement =
                PasskeyCredential.create(
                    id = newId,
                    rpId = RpId("example.com"),
                    userId = UserId("user-1"),
                    userName = "replacement",
                    userDisplayName = "Replacement",
                    publicKey = keyPair.public,
                    aaguid = ByteArray(16),
                    credentialId = newId.toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            lateinit var operationJob: Job
            val operation =
                launch {
                    operationJob = currentCoroutineContext()[Job]!!
                    try {
                        passkeyDao.replaceCredentialsForRpAndUser(replacement)
                    } catch (_: CancellationException) {
                        // The DAO checks cancellation before commit; this child remains cancelled.
                    }
                }
            every { metadataProtectionService.encryptCredentialMetadata(any(), any()) } answers {
                operationJob.cancel()
                ENCRYPTED_META
            }

            operation.join()

            assertTrue(operation.isCancelled)
            val afterCancellation =
                passkeyDao
                    .getCredentialsByRpIdAndUserId(RpId("example.com"), UserId("user-1"))
                    .single()
            assertEquals(original.id, afterCancellation.id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `partial text search on display fields returns matching credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1", userName = "alice")
            seedCredentialDirectly(
                "cred-2",
                rpId = "example.com",
                userId = "user-2",
                userName = "bob",
                userIdIndex = byteArrayOf(40, 50),
            )

            // Partial search on display field (user_name LIKE '%ali%')
            val results =
                database.passkeyCredentialQueries
                    .search_all(query = "%ali%")
                    .executeAsList()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `search_all returns all credentials matching display text`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1", userDisplayName = "Alice Smith")
            seedCredentialDirectly(
                "cred-2",
                rpId = "example.com",
                userId = "user-2",
                userDisplayName = "Bob Jones",
                userIdIndex = byteArrayOf(40, 50),
            )

            val results =
                database.passkeyCredentialQueries
                    .search_all(query = "%Smith%")
                    .executeAsList()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

    // --- T049: Tampered encrypted metadata rejection ---

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `tampered encrypted metadata falls back to plaintext rp_id during hydration`() =
        runTest {
            // Simulate tampered metadata that throws on decryption
            every {
                metadataProtectionService.decryptCredentialMetadata(
                    TAMPERED_META,
                    match { it.contentEquals("example.com") },
                )
            } throws
                SecurityException("Authentication tag mismatch")

            // Insert credential with tampered metadata
            database.passkeyCredentialQueries.insert(
                id = "AAAAAAAAAAAAAAAAAAAAAA",
                created_at = 1000L,
                updated_at = 2000L,
                last_used_at = 2000L,
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ByteArray(16)),
                cose_algorithm = -7L,
                credential_id = "AAAAAAAAAAAAAAAAAAAAAA",
                cred_protect_policy = 1L,
                label = null,
                derivation_profile = "chimali-local-hdk-p256-v1",
                public_key =
                    java.util.Base64.getEncoder().encodeToString(
                        KeyPairGenerator
                            .getInstance("EC")
                            .apply { initialize(KEY_SIZE_256) }
                            .generateKeyPair()
                            .public.encoded,
                    ),
                rp_id = "example.com".encodeToByteArray(),
                rp_name = "Example".encodeToByteArray(),
                sign_count = 0L,
                user_display_name = "Tampered User".encodeToByteArray(),
                user_id = "user-tampered".encodeToByteArray(),
                user_name = "tampered".encodeToByteArray(),
                rp_id_index = RP_ID_INDEX,
                user_id_index = USER_ID_INDEX,
                encrypted_metadata = TAMPERED_META,
            )

            // Hydrate via EntityMapper — should fall back to plaintext rp_id
            val entity = database.passkeyCredentialQueries.select_by_id("AAAAAAAAAAAAAAAAAAAAAA").executeAsOne()
            val result = entity.toDomainModel(publicKeyDecoder, metadataProtectionService)
            assertTrue(result.isSuccess)

            val credential = (result as Outcome.Success).data
            // Falls back to the plaintext rp_id column since decryption failed
            assertEquals("example.com", credential.rpId.toBoundaryString())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `null encrypted metadata uses plaintext columns during hydration`() =
        runTest {
            // Insert credential without encrypted metadata (legacy pre-migration)
            database.passkeyCredentialQueries.insert(
                id = "AQEBAQEBAQEBAQEBAQEBAQ",
                created_at = 1000L,
                updated_at = 2000L,
                last_used_at = 2000L,
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ByteArray(16)),
                cose_algorithm = -7L,
                credential_id = "AQEBAQEBAQEBAQEBAQEBAQ",
                cred_protect_policy = 1L,
                label = null,
                derivation_profile = "chimali-local-hdk-p256-v1",
                public_key =
                    java.util.Base64.getEncoder().encodeToString(
                        KeyPairGenerator
                            .getInstance("EC")
                            .apply { initialize(KEY_SIZE_256) }
                            .generateKeyPair()
                            .public.encoded,
                    ),
                rp_id = "legacy.example.com".encodeToByteArray(),
                rp_name = "Legacy".encodeToByteArray(),
                sign_count = 0L,
                user_display_name = "Legacy User".encodeToByteArray(),
                user_id = "legacy-user".encodeToByteArray(),
                user_name = "legacyuser".encodeToByteArray(),
                rp_id_index = null,
                user_id_index = null,
                encrypted_metadata = null,
            )

            val entity = database.passkeyCredentialQueries.select_by_id("AQEBAQEBAQEBAQEBAQEBAQ").executeAsOne()
            val result = entity.toDomainModel(publicKeyDecoder, metadataProtectionService)
            assertTrue(result.isSuccess)

            val credential = (result as Outcome.Success).data
            assertEquals("legacy.example.com", credential.rpId.toBoundaryString())
            assertEquals("legacy-user", credential.userId.toBoundaryString())
        }

    // -- Helper --

    private fun seedCredentialDirectly(
        id: String,
        rpId: String = "example.com",
        userId: String = "user-1",
        userName: String = "user",
        userDisplayName: String = "Display",
        rpIdIndex: ByteArray = RP_ID_INDEX,
        userIdIndex: ByteArray = USER_ID_INDEX,
        derivationProfile: String = CredentialKeyDescriptor.P256_PROFILE,
    ) {
        val validBase64Id =
            kotlin.io.encoding.Base64.UrlSafe
                .withPadding(
                    kotlin.io.encoding.Base64.PaddingOption.ABSENT,
                ).encode((id.padEnd(16, '0')).toByteArray().take(16).toByteArray())
        database.passkeyCredentialQueries.insert(
            id = validBase64Id,
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(16)),
            cose_algorithm = -7L,
            credential_id = validBase64Id,
            cred_protect_policy = 1L,
            label = null,
            derivation_profile = derivationProfile,
            public_key =
                java.util.Base64.getEncoder().encodeToString(
                    KeyPairGenerator
                        .getInstance("EC")
                        .apply { initialize(KEY_SIZE_256) }
                        .generateKeyPair()
                        .public.encoded,
                ),
            rp_id = rpId.encodeToByteArray(),
            rp_name = "RP".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = userDisplayName.encodeToByteArray(),
            user_id = userId.encodeToByteArray(),
            user_name = userName.encodeToByteArray(),
            rp_id_index = rpIdIndex.copyOf(),
            user_id_index = userIdIndex.copyOf(),
            encrypted_metadata = ENCRYPTED_META,
        )
    }
}
