package com.chimali.fido2.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.data.worker.CorruptedKeyRepairWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private lateinit var database: Fido2Database
    private lateinit var repository: CredentialRepositoryImpl
    private lateinit var metadataProtectionService: CredentialMetadataProtectionService
    private lateinit var passkeyDao: PasskeyCredentialDao
    private lateinit var rpDao: RelyingPartyDao
    private lateinit var consentDao: UserConsentRecordDao
    private lateinit var publicKeyDecoder: PublicKeyDecoder

    private val testDispatcher = UnconfinedTestDispatcher()
    private val timeProvider = TimeProvider()

    private companion object {
        private const val KEY_SIZE_256 = 256
        private val RP_ID_INDEX = byteArrayOf(1, 2, 3)
        private val USER_ID_INDEX = byteArrayOf(4, 5, 6)
        private val ENCRYPTED_META = byteArrayOf(7, 8, 9, 10)
        private val TAMPERED_META = byteArrayOf(99, 99, 99, 99)
    }

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)

        metadataProtectionService = mockk()
        every { metadataProtectionService.getRpIdIndex("example.com") } returns RP_ID_INDEX
        every { metadataProtectionService.getUserIdIndex("user-1") } returns USER_ID_INDEX
        every {
            metadataProtectionService.encryptMetadata(any(), "example.com")
        } returns ENCRYPTED_META
        every {
            metadataProtectionService.encryptRelyingPartyMetadata(any())
        } returns byteArrayOf(20, 21)
        every {
            metadataProtectionService.decryptMetadata(ENCRYPTED_META, "example.com")
        } returns """{"rpId":"example.com","userId":"user-1"}"""

        passkeyDao = PasskeyCredentialDao(database, timeProvider, metadataProtectionService)
        rpDao = RelyingPartyDao(database, timeProvider, metadataProtectionService)
        consentDao = UserConsentRecordDao(database, metadataProtectionService)

        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }.generateKeyPair()
        publicKeyDecoder =
            mockk {
                every { decodePublicKey(any(), any()) } returns Outcome.Success(keyPair.public)
            }

        val cryptoService: Fido2CryptoService =
            mockk {
                coEvery { keyExists(any()) } returns true
            }
        val repairWorker: CorruptedKeyRepairWorker = mockk(relaxed = true)
        val aggregateService: AggregateService<PasskeyCommand, PasskeyState> =
            mockk {
                coEvery { execute(any(), any()) } returns Result.success(mockk(relaxed = true))
            }

        repository =
            CredentialRepositoryImpl(
                passkeyCredentialDao = passkeyDao,
                relyingPartyDao = rpDao,
                userConsentRecordDao = consentDao,
                cryptoService = cryptoService,
                publicKeyDecoder = publicKeyDecoder,
                corruptedKeyRepairWorker = repairWorker,
                timeProvider = timeProvider,
                aggregateService = aggregateService,
                metadataProtectionService = metadataProtectionService,
                ioDispatcher = testDispatcher,
            )
    }

    // --- T023: Lookup-token exact match tests ---

    @Test
    fun `exact-match lookup by RP ID returns seeded credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val results = passkeyDao.getCredentialsByRpId(RpId("example.com")).first()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

    @Test
    fun `exact-match lookup by user ID returns seeded credential`() =
        runTest {
            seedCredentialDirectly("cred-1", rpId = "example.com", userId = "user-1")

            val results = passkeyDao.getCredentialsByUserId(UserId("user-1")).first()
            assertEquals(1, results.size)
            assertEquals("Y3JlZC0xMDAwMDAwMDAwMA", results[0].id)
        }

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

    @Test
    fun `tampered encrypted metadata falls back to plaintext rp_id during hydration`() =
        runTest {
            // Simulate tampered metadata that throws on decryption
            every {
                metadataProtectionService.decryptMetadata(TAMPERED_META, "example.com")
            } throws SecurityException("Authentication tag mismatch")

            // Insert credential with tampered metadata
            database.passkeyCredentialQueries.insert(
                id = "AAAAAAAAAAAAAAAAAAAAAA",
                created_at = 1000L,
                last_used_at = 2000L,
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ByteArray(16)),
                cose_algorithm = -7L,
                credential_id = "AAAAAAAAAAAAAAAAAAAAAA",
                cred_protect_policy = 1L,
                label = null,
                private_key_alias = "alias",
                public_key =
                    java.util.Base64.getEncoder().encodeToString(
                        KeyPairGenerator
                            .getInstance("EC")
                            .apply { initialize(KEY_SIZE_256) }
                            .generateKeyPair()
                            .public.encoded,
                    ),
                rp_id = "example.com",
                rp_name = "Example",
                sign_count = 0L,
                user_display_name = "Tampered User",
                user_id = "user-tampered",
                user_name = "tampered",
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
            assertEquals(RpId("example.com"), credential.rpId)
        }

    @Test
    fun `null encrypted metadata uses plaintext columns during hydration`() =
        runTest {
            // Insert credential without encrypted metadata (legacy pre-migration)
            database.passkeyCredentialQueries.insert(
                id = "AQEBAQEBAQEBAQEBAQEBAQ",
                created_at = 1000L,
                last_used_at = 2000L,
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ByteArray(16)),
                cose_algorithm = -7L,
                credential_id = "AQEBAQEBAQEBAQEBAQEBAQ",
                cred_protect_policy = 1L,
                label = null,
                private_key_alias = "alias",
                public_key =
                    java.util.Base64.getEncoder().encodeToString(
                        KeyPairGenerator
                            .getInstance("EC")
                            .apply { initialize(KEY_SIZE_256) }
                            .generateKeyPair()
                            .public.encoded,
                    ),
                rp_id = "legacy.example.com",
                rp_name = "Legacy",
                sign_count = 0L,
                user_display_name = "Legacy User",
                user_id = "legacy-user",
                user_name = "legacyuser",
                rp_id_index = null,
                user_id_index = null,
                encrypted_metadata = null,
            )

            val entity = database.passkeyCredentialQueries.select_by_id("AQEBAQEBAQEBAQEBAQEBAQ").executeAsOne()
            val result = entity.toDomainModel(publicKeyDecoder, metadataProtectionService)
            assertTrue(result.isSuccess)

            val credential = (result as Outcome.Success).data
            assertEquals(RpId("legacy.example.com"), credential.rpId)
            assertEquals(UserId("legacy-user"), credential.userId)
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
    ) {
        val validBase64Id =
            kotlin.io.encoding.Base64.UrlSafe
                .withPadding(
                    kotlin.io.encoding.Base64.PaddingOption.ABSENT,
                ).encode((id.padEnd(16, '0')).toByteArray().take(16).toByteArray())
        database.passkeyCredentialQueries.insert(
            id = validBase64Id,
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(16)),
            cose_algorithm = -7L,
            credential_id = validBase64Id,
            cred_protect_policy = 1L,
            label = null,
            private_key_alias = "alias-$id",
            public_key =
                java.util.Base64.getEncoder().encodeToString(
                    KeyPairGenerator
                        .getInstance("EC")
                        .apply { initialize(KEY_SIZE_256) }
                        .generateKeyPair()
                        .public.encoded,
                ),
            rp_id = rpId,
            rp_name = "RP",
            sign_count = 0L,
            user_display_name = userDisplayName,
            user_id = userId,
            user_name = userName,
            rp_id_index = rpIdIndex,
            user_id_index = userIdIndex,
            encrypted_metadata = ENCRYPTED_META,
        )
    }
}
