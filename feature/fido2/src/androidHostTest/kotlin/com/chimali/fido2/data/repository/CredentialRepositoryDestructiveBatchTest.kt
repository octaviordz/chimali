package com.chimali.fido2.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialRepositoryDestructiveBatchTest {
    private lateinit var passkeyDao: PasskeyCredentialDao
    private lateinit var decoder: PublicKeyDecoder
    private lateinit var timeProvider: TimeProvider
    private lateinit var aggregate: AggregateService<PasskeyCommand, PasskeyState>
    private lateinit var repository: CredentialRepositoryImpl
    private lateinit var credential: PasskeyCredential
    private lateinit var row: Passkey_credential

    @BeforeTest
    fun setUp() =
        runTest {
            passkeyDao = mockk()
            val relyingPartyDao = mockk<RelyingPartyDao>(relaxed = true)
            val consentDao = mockk<UserConsentRecordDao>(relaxed = true)
            decoder = mockk()
            timeProvider = mockk(relaxed = true)
            aggregate =
                mockk {
                    coEvery { execute(any(), any()) } returns Result.success(mockk(relaxed = true))
                }
            val protection =
                mockk<CredentialMetadataProtectionService>(relaxed = true) {
                    every { decryptCredentialMetadata(any(), any()) } answers {
                        CredentialMetadataProtectionService.CredentialMetadata(
                            "https://example.com".toCharArray(),
                            "user-123".toCharArray(),
                        )
                    }
                }
            val id = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 7 })
            val keyPairGenerator =
                java.security.KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(256) }
            val publicKey = keyPairGenerator.generateKeyPair().public
            credential =
                PasskeyCredential.create(
                    id = id,
                    rpId = RpId("https://example.com"),
                    userId = UserId("user-123"),
                    userName = "user",
                    userDisplayName = "User",
                    publicKey = publicKey,
                    aaguid = ByteArray(16),
                    credentialId = id.toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val now = Clock.System.now().toEpochMilliseconds()
            row =
                Passkey_credential(
                    id = id.encoded,
                    created_at = now,
                    last_used_at = now,
                    updated_at = now,
                    aaguid =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(credential.aaguid),
                    cose_algorithm = PasskeyCredential.COSE_ES256.toLong(),
                    credential_id =
                        java.util.Base64
                            .getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(id.toByteArray()),
                    cred_protect_policy = credential.credProtectPolicy.toLong(),
                    derivation_profile = credential.derivationProfile,
                    label = null,
                    public_key =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(publicKey.encoded),
                    rp_id = "https://example.com".encodeToByteArray(),
                    rp_name = "example".encodeToByteArray(),
                    sign_count = 0L,
                    user_display_name = "User".encodeToByteArray(),
                    user_id = "user-123".encodeToByteArray(),
                    user_name = "user".encodeToByteArray(),
                    rp_id_index = byteArrayOf(1),
                    user_id_index = byteArrayOf(2),
                    encrypted_metadata = byteArrayOf(3),
                )
            coEvery { decoder.decodePublicKey(any(), any()) } returns Outcome.Success(publicKey)
            every { passkeyDao.getAllCredentials() } returns flowOf(emptyList())
            every { passkeyDao.getCredentialsByRpText(any()) } returns flowOf(emptyList())
            coEvery { passkeyDao.deleteCredential(id) } just Runs
            repository =
                CredentialRepositoryImpl(
                    passkeyDao,
                    relyingPartyDao,
                    consentDao,
                    decoder,
                    timeProvider,
                    aggregate,
                    protection,
                )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `query creation failure is typed and a subsequent destructive batch succeeds`() =
        runTest {
            every { passkeyDao.getAllCredentials() } throws android.database.SQLException("synthetic-query-detail")

            val failed = repository.deleteAllCredentials()

            val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(failed).error)
            assertEquals("Credential storage operation failed", error.message)
            assertIs<android.database.SQLException>(error.cause)

            val row = row.copyRow()
            every { passkeyDao.getAllCredentials() } returns flowOf(listOf(row))
            val succeeding = repository.deleteAllCredentials()

            assertIs<Outcome.Success<*>>(succeeding)
            assertRowErased(row)
            coVerify(exactly = 1) { passkeyDao.deleteCredential(credential.id) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `query collection failure is typed and prevents reset`() =
        runTest {
            every { passkeyDao.getAllCredentials() } returns
                flow { throw android.database.SQLException("synthetic-collection-detail") }

            val result = repository.resetAuthenticator()

            val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("Credential storage operation failed", error.message)
            coVerify(exactly = 0) { aggregate.execute(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `RP scoped query failure prevents deletion`() =
        runTest {
            every { passkeyDao.getCredentialsByRpText(any()) } throws
                android.database.SQLException("synthetic-rp-failure")

            val result = repository.deleteAllCredentials(RpId("https://example.com"))

            assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
            coVerify(exactly = 0) { aggregate.execute(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `actual RP lookup chain returns redacted unavailable-key error and succeeds after provisioning`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val lookupTokens = HmacMetadataLookupTokenService()
                val protection =
                    CredentialMetadataProtectionService(
                        lookupTokens,
                        mockk(relaxed = true),
                    )
                val actualDao = PasskeyCredentialDao(database, timeProvider, protection)
                repository =
                    CredentialRepositoryImpl(
                        actualDao,
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        decoder,
                        timeProvider,
                        aggregate,
                        protection,
                    )
                val rpId = RpId("https://example.com")

                val failed = repository.deleteAllCredentials(rpId)

                val error = assertIs<DomainError.StorageError>(assertIs<Outcome.Error<*>>(failed).error)
                assertEquals("Credential storage is temporarily unavailable", error.message)
                assertEquals(null, error.cause)
                assertTrue(!error.message.contains("Metadata lookup key unavailable"))
                coVerify(exactly = 0) { aggregate.execute(any(), any()) }

                lookupTokens.provisionKey(ByteArray(32) { it.toByte() })
                assertIs<Outcome.Success<*>>(repository.deleteAllCredentials(rpId))
                coVerify(exactly = 0) { aggregate.execute(any(), any()) }
                lookupTokens.clearKey()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `malformed row rejects the whole destructive snapshot and erases partial owners`() =
        runTest {
            val validRow = row.copyRow()
            val malformedRow = row.copyRow().copy(credential_id = "inconsistent")
            val hydrated = mutableListOf<PasskeyCredential>()
            repository.onDestructiveCredentialCreatedForTest = hydrated::add
            every { passkeyDao.getAllCredentials() } returns flowOf(listOf(validRow, malformedRow))

            val result = repository.deleteAllCredentials()

            val error = assertIs<DomainError.ValidationError>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("Credential storage contains invalid records", error.message)
            assertRowErased(validRow)
            assertRowErased(malformedRow)
            assertEquals(1, hydrated.size)
            hydrated.forEach(::assertCredentialErased)
            coVerify(exactly = 0) { aggregate.execute(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `empty storage succeeds and expiry cleanup deletes only expired records`() =
        runTest {
            assertIs<Outcome.Success<*>>(repository.resetAuthenticator())

            val now = Instant.fromEpochMilliseconds(2_000_000_000_000)
            every { timeProvider.now() } returns now
            val expiredId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 8 })
            val currentId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 9 })
            val expired = row.copyRow(expiredId).copy(created_at = 0L)
            val current = row.copyRow(currentId).copy(created_at = now.toEpochMilliseconds())
            val hydrated = mutableListOf<PasskeyCredential>()
            repository.onDestructiveCredentialCreatedForTest = hydrated::add
            every { passkeyDao.getAllCredentials() } returns flowOf(listOf(expired, current))
            coEvery { passkeyDao.deleteCredential(expiredId) } just Runs

            val result = repository.cleanupExpiredCredentials(maxAgeDays = 1)

            assertEquals(Outcome.Success(1), result)
            assertRowErased(expired)
            assertRowErased(current)
            assertEquals(1, hydrated.size)
            hydrated.forEach(::assertCredentialErased)
            coVerify(exactly = 1) { passkeyDao.deleteCredential(expiredId) }
            coVerify(exactly = 0) { passkeyDao.deleteCredential(currentId) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `item cancellation propagates while erasing every credential owner`() =
        runTest {
            val sourceRow = row.copyRow()
            val hydrated = mutableListOf<PasskeyCredential>()
            val cancellation = CancellationException("synthetic-batch-cancellation")
            repository.onDestructiveCredentialCreatedForTest = hydrated::add
            every { passkeyDao.getAllCredentials() } returns flowOf(listOf(sourceRow))
            coEvery { aggregate.execute(any(), any()) } returns Result.failure(cancellation)

            val thrown = assertFailsWith<CancellationException> { repository.resetAuthenticator() }

            assertTrue(thrown === cancellation)
            assertRowErased(sourceRow)
            assertEquals(1, hydrated.size)
            hydrated.forEach(::assertCredentialErased)
        }

    private fun Passkey_credential.copyRow(id: CredentialId = credential.id): Passkey_credential =
        copy(
            id = id.encoded,
            credential_id =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(id.toByteArray()),
            rp_id = rp_id.copyOf(),
            rp_name = rp_name.copyOf(),
            user_display_name = user_display_name.copyOf(),
            user_id = user_id.copyOf(),
            user_name = user_name.copyOf(),
            rp_id_index = rp_id_index?.copyOf(),
            user_id_index = user_id_index?.copyOf(),
            encrypted_metadata = encrypted_metadata?.copyOf(),
        )

    private fun assertRowErased(source: Passkey_credential) {
        listOf(source.rp_id, source.rp_name, source.user_id, source.user_name, source.user_display_name)
            .forEach { bytes -> assertTrue(bytes.all { it == 0.toByte() }) }
    }

    private fun assertCredentialErased(owner: PasskeyCredential) {
        listOf(owner.rpId, owner.userId, owner.userName, owner.userDisplayName).forEach { text ->
            val characters = text.copyCharacters()
            assertTrue(characters.all { it == '\u0000' })
            characters.fill('\u0000')
        }
    }
}
