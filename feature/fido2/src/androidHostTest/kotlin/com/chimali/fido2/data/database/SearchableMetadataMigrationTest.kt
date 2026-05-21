package com.chimali.fido2.data.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchableMetadataMigrationTest {
    private lateinit var database: Fido2Database
    private lateinit var protectionService: CredentialMetadataProtectionService

    @BeforeEach
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)
        protectionService = mockk()
        SearchableMetadataMigrationState.resetStateForTesting()
    }

    @AfterEach
    fun tearDown() {
        SearchableMetadataMigrationState.resetStateForTesting()
    }

    @Test
    fun `test successful migration of legacy records`() {
        // Seed legacy relying party (encrypted_metadata is null)
        database.relyingPartyQueries.insert(
            id = "rp-1",
            created_at = 1000L,
            last_used_at = 2000L,
            credential_count = 1L,
            icon_url = "icon",
            is_blocked = 0L,
            name = "RP Name",
            encrypted_metadata = null,
        )

        // Seed legacy credential (rp_id_index, user_id_index, encrypted_metadata are null)
        database.passkeyCredentialQueries.insert(
            id = "cred-1",
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            private_key_alias = "alias",
            public_key = "pubkey",
            rp_id = "rp-1",
            rp_name = "RP Name",
            sign_count = 0L,
            user_display_name = "Display Name",
            user_id = "user-1",
            user_name = "User Name",
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // Seed legacy consent (rp_id_index is null)
        database.userConsentRecordQueries.insert(
            id = "consent-1",
            timestamp = 1000L,
            biometric_used = 1L,
            credential_id = "cred-id-bytes",
            device_id = "device",
            ip_address = "ip",
            operation_type = "AUTHENTICATION",
            pin_used = 0L,
            rp_id = "rp-1",
            user_agent = "agent",
            rp_id_index = null,
        )

        val expectedRpIdIndex = byteArrayOf(1, 2, 3)
        val expectedUserIdIndex = byteArrayOf(4, 5, 6)
        val expectedEncryptedCredMetadata = byteArrayOf(7, 8, 9)
        val expectedEncryptedRpMetadata = byteArrayOf(10, 11, 12)

        every { protectionService.getRpIdIndex("rp-1") } returns expectedRpIdIndex
        every { protectionService.getUserIdIndex("user-1") } returns expectedUserIdIndex
        every { protectionService.encryptMetadata(any(), "rp-1") } returns expectedEncryptedCredMetadata
        every { protectionService.encryptRelyingPartyMetadata(any()) } returns expectedEncryptedRpMetadata

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
        val migratedRp = database.relyingPartyQueries.select_by_id("rp-1").executeAsOne()
        assertTrue(migratedRp.encrypted_metadata?.contentEquals(expectedEncryptedRpMetadata) == true)

        // Verify user_consent_record has been migrated
        val migratedConsent = database.userConsentRecordQueries.select_by_id("consent-1").executeAsOne()
        assertTrue(migratedConsent.rp_id_index?.contentEquals(expectedRpIdIndex) == true)
    }

    @Test
    fun `test migration failure and rollback under interruption`() {
        // Seed legacy credential
        database.passkeyCredentialQueries.insert(
            id = "cred-1",
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            private_key_alias = "alias",
            public_key = "pubkey",
            rp_id = "rp-1",
            rp_name = "RP Name",
            sign_count = 0L,
            user_display_name = "Display Name",
            user_id = "user-1",
            user_name = "User Name",
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // Mock error during token generation
        every { protectionService.getRpIdIndex("rp-1") } throws
            RuntimeException("Simulated encryption hardware failure")

        // Run migration (should fail and roll back transaction)
        val result = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result.isFailure)
        assertEquals(MigrationStatus.FAILED, SearchableMetadataMigrationState.status)
        assertNotNull(SearchableMetadataMigrationState.lastError)

        // Verify that credential remained unmodified due to rollback
        val rolledBackCred = database.passkeyCredentialQueries.select_by_id("cred-1").executeAsOne()
        assertNull(rolledBackCred.rp_id_index)
        assertNull(rolledBackCred.user_id_index)
        assertNull(rolledBackCred.encrypted_metadata)
    }

    @Test
    fun `test migration idempotency on subsequent runs`() {
        val expectedRpIdIndex = byteArrayOf(1, 2, 3)
        val expectedUserIdIndex = byteArrayOf(4, 5, 6)
        val expectedEncryptedCredMetadata = byteArrayOf(7, 8, 9)

        // Seed an ALREADY migrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-already-migrated",
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-bytes",
            cred_protect_policy = 1L,
            label = "label",
            private_key_alias = "alias",
            public_key = "pubkey",
            rp_id = "rp-1",
            rp_name = "RP Name",
            sign_count = 0L,
            user_display_name = "Display Name",
            user_id = "user-1",
            user_name = "User Name",
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
        verify(exactly = 0) { protectionService.encryptMetadata(any(), any()) }
    }

    // --- T048: Interrupted and re-run migration path coverage ---

    @Test
    fun `test retry succeeds after previous failure`() {
        // Seed legacy credential
        database.passkeyCredentialQueries.insert(
            id = "cred-retry",
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-retry",
            cred_protect_policy = 1L,
            label = "label",
            private_key_alias = "alias",
            public_key = "pubkey",
            rp_id = "rp-retry",
            rp_name = "RP Retry",
            sign_count = 0L,
            user_display_name = "Retry User",
            user_id = "user-retry",
            user_name = "Retry",
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        // First attempt: fail
        every { protectionService.getRpIdIndex("rp-retry") } throws RuntimeException("Transient failure")
        val failResult = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(failResult.isFailure)
        assertEquals(MigrationStatus.FAILED, SearchableMetadataMigrationState.status)

        // Second attempt: succeed
        val expectedRpIdx = byteArrayOf(11, 12, 13)
        val expectedUserIdx = byteArrayOf(14, 15, 16)
        val expectedMeta = byteArrayOf(17, 18, 19)
        every { protectionService.getRpIdIndex("rp-retry") } returns expectedRpIdx
        every { protectionService.getUserIdIndex("user-retry") } returns expectedUserIdx
        every { protectionService.encryptMetadata(any(), "rp-retry") } returns expectedMeta

        val retryResult = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(retryResult.isSuccess)
        assertEquals(MigrationStatus.SUCCESS, SearchableMetadataMigrationState.status)

        val migrated = database.passkeyCredentialQueries.select_by_id("cred-retry").executeAsOne()
        assertTrue(migrated.rp_id_index?.contentEquals(expectedRpIdx) == true)
    }

    @Test
    fun `test partial migration picks up only unmigrated records`() {
        val preExistingToken = byteArrayOf(50, 51, 52)
        val preExistingUserToken = byteArrayOf(53, 54, 55)
        val preExistingMeta = byteArrayOf(56, 57, 58)

        // Already-migrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-already",
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-already",
            cred_protect_policy = 1L,
            label = null,
            private_key_alias = "alias",
            public_key = "pubkey",
            rp_id = "rp-old",
            rp_name = "RP Old",
            sign_count = 0L,
            user_display_name = "Old User",
            user_id = "user-old",
            user_name = "OldUser",
            rp_id_index = preExistingToken,
            user_id_index = preExistingUserToken,
            encrypted_metadata = preExistingMeta,
        )

        // New unmigrated credential
        database.passkeyCredentialQueries.insert(
            id = "cred-new",
            created_at = 2000L,
            last_used_at = 3000L,
            aaguid = "aaguid2",
            cose_algorithm = -7L,
            credential_id = "cred-id-new",
            cred_protect_policy = 1L,
            label = null,
            private_key_alias = "alias2",
            public_key = "pubkey2",
            rp_id = "rp-new",
            rp_name = "RP New",
            sign_count = 0L,
            user_display_name = "New User",
            user_id = "user-new",
            user_name = "NewUser",
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )

        val newRpIdx = byteArrayOf(60, 61)
        val newUserIdx = byteArrayOf(62, 63)
        val newMeta = byteArrayOf(64, 65)
        every { protectionService.getRpIdIndex("rp-new") } returns newRpIdx
        every { protectionService.getUserIdIndex("user-new") } returns newUserIdx
        every { protectionService.encryptMetadata(any(), "rp-new") } returns newMeta

        val result = SearchableMetadataMigrationState.migrate(database, protectionService)
        assertTrue(result.isSuccess)

        // Pre-migrated record unchanged
        val alreadyCred = database.passkeyCredentialQueries.select_by_id("cred-already").executeAsOne()
        assertTrue(alreadyCred.rp_id_index?.contentEquals(preExistingToken) == true)

        // New record now migrated
        val newCred = database.passkeyCredentialQueries.select_by_id("cred-new").executeAsOne()
        assertTrue(newCred.rp_id_index?.contentEquals(newRpIdx) == true)

        // Protection service only called for the new record
        verify(exactly = 0) { protectionService.getRpIdIndex("rp-old") }
        verify(exactly = 1) { protectionService.getRpIdIndex("rp-new") }
    }

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
