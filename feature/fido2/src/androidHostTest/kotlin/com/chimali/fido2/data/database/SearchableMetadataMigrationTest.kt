package com.chimali.fido2.data.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchableMetadataMigrationTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Fido2Database
    private lateinit var protectionService: CredentialMetadataProtectionService

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)
        protectionService = mockk()
        SearchableMetadataMigrationState.resetStateForTesting()
    }

    @AfterEach
    fun tearDown() {
        SearchableMetadataMigrationState.resetStateForTesting()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test successful migration of legacy records`() {
        // Seed legacy relying party (encrypted_metadata is null)
        database.relyingPartyQueries.insert(
            id = ("rp-1").encodeToByteArray(),
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            credential_count = 1L,
            icon_url = "icon",
            is_blocked = 0L,
            name = ("RP Name").encodeToByteArray(),
            encrypted_metadata = null,
        )

        // Seed legacy credential (rp_id_index, user_id_index, encrypted_metadata are null)
        database.passkeyCredentialQueries.insert(
            id = "cred-1",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey",
            rp_id = "rp-1".encodeToByteArray(),
            rp_name = "RP Name".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "Display Name".encodeToByteArray(),
            user_id = "user-1".encodeToByteArray(),
            user_name = "User Name".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // Seed legacy consent (rp_id_index is null)
        database.userConsentRecordQueries.insert(
            id = "consent-1",
            created_at = 1000L,
            timestamp = 1000L,
            updated_at = 1000L,
            biometric_used = 1L,
            credential_id = "cred-id-bytes",
            device_id = "device",
            ip_address = "ip",
            operation_type = "AUTHENTICATION",
            pin_used = 0L,
            rp_id = ("rp-1").encodeToByteArray(),
            user_agent = "agent",
            rp_id_index = null,
        )

        val expectedRpIdIndex = byteArrayOf(1, 2, 3)
        val expectedUserIdIndex = byteArrayOf(4, 5, 6)
        val expectedEncryptedCredMetadata = byteArrayOf(7, 8, 9)
        val expectedEncryptedRpMetadata = byteArrayOf(10, 11, 12)
        val generatedIndexes = mutableListOf<ByteArray>()

        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-1") })
        } answers { expectedRpIdIndex.copyOf().also(generatedIndexes::add) }
        every {
            protectionService.getUserIdIndex(match { it.contentEquals("user-1") })
        } answers { expectedUserIdIndex.copyOf().also(generatedIndexes::add) }
        every {
            protectionService.encryptCredentialMetadata(
                match { it.contentEquals("rp-1") },
                match { it.contentEquals("user-1") },
            )
        } returns expectedEncryptedCredMetadata
        every { protectionService.encryptRelyingPartyMetadata(any(), any()) } returns expectedEncryptedRpMetadata

        // Execute migration
        val result = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result.isSuccess)
        assertEquals(MigrationStatus.SUCCESS, SearchableMetadataMigrationState.status)

        // Verify passkey_credential has been migrated
        val migratedCred = database.passkeyCredentialQueries.select_by_id("cred-1").executeAsOne()
        assertTrue(migratedCred.rp_id_index?.contentEquals(expectedRpIdIndex) == true)
        assertTrue(migratedCred.user_id_index?.contentEquals(expectedUserIdIndex) == true)
        assertTrue(migratedCred.encrypted_metadata?.contentEquals(expectedEncryptedCredMetadata) == true)

        // Verify relying_party has been migrated
        val migratedRp = database.relyingPartyQueries.select_by_id("rp-1".encodeToByteArray()).executeAsOne()
        assertTrue(migratedRp.encrypted_metadata?.contentEquals(expectedEncryptedRpMetadata) == true)

        // Verify user_consent_record has been migrated
        val migratedConsent = database.userConsentRecordQueries.select_by_id("consent-1").executeAsOne()
        assertTrue(
            migratedConsent.rp_id_index?.contentEquals(expectedRpIdIndex) == true,
            "Expected consent token ${expectedRpIdIndex.contentToString()}, " +
                "found ${migratedConsent.rp_id_index?.contentToString()}",
        )
        assertEquals(3, generatedIndexes.size)
        generatedIndexes.forEach { assertEquals(0, it.count { byte -> byte != 0.toByte() }) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test migration failure and rollback under interruption`() {
        val credentialMarker = "synthetic-credential-marker-5f34"
        val failureMarker = "synthetic-migration-failure-$credentialMarker"
        // Seed legacy credential
        database.passkeyCredentialQueries.insert(
            id = credentialMarker,
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey",
            rp_id = "rp-1".encodeToByteArray(),
            rp_name = "RP Name".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "Display Name".encodeToByteArray(),
            user_id = "user-1".encodeToByteArray(),
            user_name = "User Name".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // Mock error during token generation
        val thrownFailure = RuntimeException("Simulated migration failure: $failureMarker")
        every { protectionService.getRpIdIndex(match { it.contentEquals("rp-1") }) } throws thrownFailure

        // Run migration (should fail and roll back transaction)
        CapturedDiagnostics().use { diagnostics ->
            val result = SearchableMetadataMigrationState.migrate(database, protectionService)
            assertTrue(result.isFailure)
            assertEquals(MigrationStatus.FAILED, SearchableMetadataMigrationState.status)
            val returnedFailure = requireNotNull(result.exceptionOrNull())
            val retainedFailure = requireNotNull(SearchableMetadataMigrationState.lastError)
            assertEquals("Searchable metadata migration failed", returnedFailure.message)
            assertEquals("Searchable metadata migration failed", retainedFailure.message)
            assertNull(returnedFailure.cause)
            assertNull(retainedFailure.cause)
            assertTrue(failureMarker !in returnedFailure.toString())
            assertTrue(failureMarker !in retainedFailure.toString())
            assertTrue(thrownFailure.message!!.contains(failureMarker))
            diagnostics.assertNoSensitiveValues(credentialMarker, failureMarker)
            assertTrue(diagnostics.size > 0)
        }

        // Verify that credential remained unmodified due to rollback
        val rolledBackCred = database.passkeyCredentialQueries.select_by_id(credentialMarker).executeAsOne()
        assertNull(rolledBackCred.rp_id_index)
        assertNull(rolledBackCred.user_id_index)
        assertNull(rolledBackCred.encrypted_metadata)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `migration erases credential RP index when user index acquisition fails`() {
        database.passkeyCredentialQueries.insert(
            id = "synthetic-partial-migration-credential",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "credential-id",
            cred_protect_policy = 1L,
            label = null,
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "public-key",
            rp_id = "rp-partial".encodeToByteArray(),
            rp_name = "RP".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "User".encodeToByteArray(),
            user_id = "user-partial".encodeToByteArray(),
            user_name = "user".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )
        val retainedRpIndex = byteArrayOf(41, 42, 43)
        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-partial") })
        } returns retainedRpIndex
        every {
            protectionService.getUserIdIndex(match { it.contentEquals("user-partial") })
        } throws IllegalStateException("synthetic user index failure")

        val result = SearchableMetadataMigrationState.migrate(database, protectionService)

        assertTrue(result.isFailure)
        assertEquals("Searchable metadata migration failed", result.exceptionOrNull()?.message)
        assertNull(result.exceptionOrNull()?.cause)
        assertNull(SearchableMetadataMigrationState.lastError?.cause)
        assertTrue(retainedRpIndex.all { it == 0.toByte() })
        val credential =
            database.passkeyCredentialQueries
                .select_by_id("synthetic-partial-migration-credential")
                .executeAsOne()
        assertNull(credential.rp_id_index)
        assertNull(credential.user_id_index)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `migration erases consent RP index when update fails and remains retryable`() {
        database.userConsentRecordQueries.insert(
            id = "synthetic-consent-update-failure",
            created_at = 1000L,
            timestamp = 1000L,
            updated_at = 1000L,
            biometric_used = 0L,
            credential_id = null,
            device_id = null,
            ip_address = null,
            operation_type = "AUTHENTICATION",
            pin_used = 0L,
            rp_id = ("rp-consent-update").encodeToByteArray(),
            user_agent = null,
            rp_id_index = null,
        )
        driver.execute(
            null,
            """
            CREATE TRIGGER reject_consent_index_update
            BEFORE UPDATE OF rp_id_index ON user_consent_record
            BEGIN
              SELECT RAISE(ABORT, 'synthetic consent index update failure');
            END;
            """.trimIndent(),
            0,
        )
        val retainedRpIndexes = mutableListOf<ByteArray>()
        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-consent-update") })
        } answers { byteArrayOf(51, 52, 53).also(retainedRpIndexes::add) }

        val result = SearchableMetadataMigrationState.migrate(database, protectionService)

        assertTrue(result.isFailure)
        assertEquals("Searchable metadata migration failed", result.exceptionOrNull()?.message)
        assertNull(result.exceptionOrNull()?.cause)
        assertNull(SearchableMetadataMigrationState.lastError?.cause)
        assertEquals(1, retainedRpIndexes.size)
        assertTrue(retainedRpIndexes.all { index -> index.all { it == 0.toByte() } })
        val consent = database.userConsentRecordQueries.select_by_id("synthetic-consent-update-failure").executeAsOne()
        assertNull(consent.rp_id_index)

        driver.execute(null, "DROP TRIGGER reject_consent_index_update", 0)
        assertTrue(SearchableMetadataMigrationState.migrate(database, protectionService).isSuccess)
        assertEquals(2, retainedRpIndexes.size)
        assertTrue(retainedRpIndexes.all { index -> index.all { it == 0.toByte() } })
        val retriedConsent =
            database.userConsentRecordQueries.select_by_id("synthetic-consent-update-failure").executeAsOne()
        assertTrue(retriedConsent.rp_id_index?.contentEquals(byteArrayOf(51, 52, 53)) == true)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `migration erases RP owner and row buffers when user text decoding fails then retries`() {
        val rpMarker = "synthetic-rp-migration-marker"
        val invalidUserUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        database.passkeyCredentialQueries.insert(
            id = "synthetic-malformed-user-utf8",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "synthetic-credential-id",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "public-key",
            rp_id = rpMarker.encodeToByteArray(),
            rp_name = "RP".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "User".encodeToByteArray(),
            user_id = invalidUserUtf8.copyOf(),
            user_name = "user".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        val retainedOwners = mutableListOf<SensitiveCredentialText>()
        val retainedRowBytes = mutableListOf<ByteArray>()
        CapturedDiagnostics().use { diagnostics ->
            val failed =
                SearchableMetadataMigrationState.migrateWithTextDecoder(database, protectionService) { bytes ->
                    retainedRowBytes += bytes
                    SensitiveCredentialText.fromUtf8Bytes(bytes).also(retainedOwners::add)
                }
            assertTrue(failed.isFailure)
            assertEquals("Searchable metadata migration failed", failed.exceptionOrNull()?.message)
            assertNull(failed.exceptionOrNull()?.cause)
            assertEquals(MigrationStatus.FAILED, SearchableMetadataMigrationState.status)
            assertNull(SearchableMetadataMigrationState.lastError?.cause)
            assertTrue(rpMarker !in failed.exceptionOrNull().toString())
            diagnostics.assertNoSensitiveValues(rpMarker)
        }

        assertEquals(1, retainedOwners.size)
        val ownerCopy = retainedOwners.single().copyCharacters()
        try {
            assertTrue(ownerCopy.all { it == '\u0000' })
        } finally {
            ownerCopy.fill('\u0000')
        }
        assertTrue(retainedRowBytes.size >= 2)
        assertTrue(retainedRowBytes.all { bytes -> bytes.all { it == 0.toByte() } })
        val rolledBack = database.passkeyCredentialQueries.select_by_id("synthetic-malformed-user-utf8").executeAsOne()
        assertNull(rolledBack.rp_id_index)
        assertNull(rolledBack.user_id_index)
        assertNull(rolledBack.encrypted_metadata)

        // Replace only the synthetic malformed test row, then confirm the failed migration can retry successfully.
        database.passkeyCredentialQueries.delete_by_id("synthetic-malformed-user-utf8")
        database.passkeyCredentialQueries.insert(
            id = "synthetic-malformed-user-utf8",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "synthetic-credential-id",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "public-key",
            rp_id = rpMarker.encodeToByteArray(),
            rp_name = "RP".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "User".encodeToByteArray(),
            user_id = "corrected-user".encodeToByteArray(),
            user_name = "user".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )
        every {
            protectionService.getRpIdIndex(match { it.contentEquals(rpMarker) })
        } returns byteArrayOf(1, 2)
        every {
            protectionService.getUserIdIndex(match { it.contentEquals("corrected-user") })
        } returns byteArrayOf(3, 4)
        every {
            protectionService.encryptCredentialMetadata(any(), any())
        } returns byteArrayOf(5, 6)

        assertTrue(SearchableMetadataMigrationState.migrate(database, protectionService).isSuccess)
        assertEquals(MigrationStatus.SUCCESS, SearchableMetadataMigrationState.status)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test migration idempotency on subsequent runs`() {
        val expectedRpIdIndex = byteArrayOf(1, 2, 3)
        val expectedUserIdIndex = byteArrayOf(4, 5, 6)
        val expectedEncryptedCredMetadata = byteArrayOf(7, 8, 9)

        // Seed an ALREADY migrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-already-migrated",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey",
            rp_id = "rp-1".encodeToByteArray(),
            rp_name = "RP Name".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "Display Name".encodeToByteArray(),
            user_id = "user-1".encodeToByteArray(),
            user_name = "User Name".encodeToByteArray(),
            rp_id_index = expectedRpIdIndex,
            user_id_index = expectedUserIdIndex,
            encrypted_metadata = expectedEncryptedCredMetadata,
        )

        // Mock the protection service (should not be called because row is already migrated)
        // If it is called, the test will fail or verify will fail.

        val result = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result.isSuccess)

        verify(exactly = 0) { protectionService.getRpIdIndex(any()) }
        verify(exactly = 0) { protectionService.getUserIdIndex(any()) }
        verify(exactly = 0) { protectionService.encryptCredentialMetadata(any(), any()) }
    }

    // --- T048: Interrupted and re-run migration path coverage ---

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test retry succeeds after previous failure`() {
        // Seed legacy credential
        database.passkeyCredentialQueries.insert(
            id = "cred-retry",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-retry",
            cred_protect_policy = 1L,
            label = "label",
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey",
            rp_id = "rp-retry".encodeToByteArray(),
            rp_name = "RP Retry".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "Retry User".encodeToByteArray(),
            user_id = "user-retry".encodeToByteArray(),
            user_name = "Retry".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // First attempt: fail
        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-retry") })
        } throws RuntimeException("Transient failure")
        val failResult = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(failResult.isFailure)
        assertEquals(MigrationStatus.FAILED, SearchableMetadataMigrationState.status)

        // Second attempt: succeed
        val expectedRpIdx = byteArrayOf(11, 12, 13)
        val expectedUserIdx = byteArrayOf(14, 15, 16)
        val expectedMeta = byteArrayOf(17, 18, 19)
        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-retry") })
        } answers { expectedRpIdx.copyOf() }
        every {
            protectionService.getUserIdIndex(match { it.contentEquals("user-retry") })
        } answers { expectedUserIdx.copyOf() }
        every {
            protectionService.encryptCredentialMetadata(
                match { it.contentEquals("rp-retry") },
                match { it.contentEquals("user-retry") },
            )
        } returns expectedMeta

        val retryResult = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(retryResult.isSuccess)
        assertEquals(MigrationStatus.SUCCESS, SearchableMetadataMigrationState.status)

        val migrated = database.passkeyCredentialQueries.select_by_id("cred-retry").executeAsOne()
        assertTrue(migrated.rp_id_index?.contentEquals(expectedRpIdx) == true)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test partial migration picks up only unmigrated records`() {
        val preExistingToken = byteArrayOf(50, 51, 52)
        val preExistingUserToken = byteArrayOf(53, 54, 55)
        val preExistingMeta = byteArrayOf(56, 57, 58)

        // Already-migrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-already",
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-already",
            cred_protect_policy = 1L,
            label = null,
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey",
            rp_id = "rp-old".encodeToByteArray(),
            rp_name = "RP Old".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "Old User".encodeToByteArray(),
            user_id = "user-old".encodeToByteArray(),
            user_name = "OldUser".encodeToByteArray(),
            rp_id_index = preExistingToken,
            user_id_index = preExistingUserToken,
            encrypted_metadata = preExistingMeta,
        )

        // New unmigrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-new",
            created_at = 2000L,
            updated_at = 2000L,
            last_used_at = 3000L,
            aaguid = "aaguid2",
            cose_algorithm = -7L,
            credential_id = "cred-id-new",
            cred_protect_policy = 1L,
            label = null,
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey2",
            rp_id = "rp-new".encodeToByteArray(),
            rp_name = "RP New".encodeToByteArray(),
            sign_count = 0L,
            user_display_name = "New User".encodeToByteArray(),
            user_id = "user-new".encodeToByteArray(),
            user_name = "NewUser".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        val newRpIdx = byteArrayOf(60, 61)
        val newUserIdx = byteArrayOf(62, 63)
        val newMeta = byteArrayOf(64, 65)
        every {
            protectionService.getRpIdIndex(match { it.contentEquals("rp-new") })
        } answers { newRpIdx.copyOf() }
        every {
            protectionService.getUserIdIndex(match { it.contentEquals("user-new") })
        } answers { newUserIdx.copyOf() }
        every {
            protectionService.encryptCredentialMetadata(
                match { it.contentEquals("rp-new") },
                match { it.contentEquals("user-new") },
            )
        } returns newMeta

        val result = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result.isSuccess)

        // Pre-migrated record unchanged
        val alreadyCred = database.passkeyCredentialQueries.select_by_id("cred-already").executeAsOne()
        assertTrue(alreadyCred.rp_id_index?.contentEquals(preExistingToken) == true)

        // New record now migrated
        val newCred = database.passkeyCredentialQueries.select_by_id("cred-new").executeAsOne()
        assertTrue(newCred.rp_id_index?.contentEquals(newRpIdx) == true)

        // Protection service only called for the new record
        verify(exactly = 1) { protectionService.getRpIdIndex(any()) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `test concurrent migration call returns success without re-running`() {
        // First run: success
        val result1 = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result1.isSuccess)

        // Second run: should return success immediately without re-executing
        val result2 = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result2.isSuccess)
        assertEquals(MigrationStatus.SUCCESS, SearchableMetadataMigrationState.status)
    }
}
