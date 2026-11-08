package com.chimali.fido2.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Verifies migration 2 retains legacy passkeys and removes the one-row-per-account restriction. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class Fido2DatabaseCredentialMigrationTest {
    @Test
    fun `migration preserves credentials and allows multiple credentials for an account`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                identifier = null,
                sql =
                    """
                    CREATE TABLE passkey_credential (
                        id TEXT NOT NULL PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        last_used_at INTEGER,
                        updated_at INTEGER NOT NULL,
                        aaguid TEXT NOT NULL,
                        cose_algorithm INTEGER NOT NULL,
                        credential_id TEXT NOT NULL,
                        cred_protect_policy INTEGER NOT NULL DEFAULT 1,
                        derivation_profile TEXT NOT NULL,
                        encrypted_metadata BLOB,
                        label TEXT,
                        public_key TEXT NOT NULL,
                        rp_id BLOB NOT NULL,
                        rp_id_index BLOB,
                        rp_name BLOB NOT NULL,
                        sign_count INTEGER NOT NULL DEFAULT 0,
                        user_display_name BLOB NOT NULL,
                        user_id BLOB NOT NULL,
                        user_id_index BLOB,
                        user_name BLOB NOT NULL,
                        UNIQUE(rp_id, user_id)
                    )
                    """.trimIndent(),
                parameters = 0,
            )
            driver.execute(
                identifier = null,
                sql =
                    """
                    CREATE VIEW credential_summary AS
                    SELECT id, rp_id, rp_name, user_name, user_display_name, created_at, last_used_at, sign_count
                    FROM passkey_credential
                    """.trimIndent(),
                parameters = 0,
            )
            driver.execute(
                identifier = null,
                sql =
                    """
                    INSERT INTO passkey_credential (
                        id, created_at, last_used_at, updated_at, aaguid, cose_algorithm, credential_id,
                        cred_protect_policy, derivation_profile, encrypted_metadata, label, public_key,
                        rp_id, rp_id_index, rp_name, sign_count, user_display_name, user_id, user_id_index, user_name
                    ) VALUES (
                        'legacy-credential', 10, 11, 12, 'aaguid', -7, 'legacy-credential',
                        1, 'p256', X'010203', 'legacy-label', 'public-key', X'6578616d706c652e636f6d', X'01',
                        X'6578616d706c652e636f6d', 4, X'55736572', X'757365722d31', X'02', X'75736572'
                    )
                    """.trimIndent(),
                parameters = 0,
            )

            Fido2Database.Schema.migrate(driver, oldVersion = 1, newVersion = Fido2Database.Schema.version)

            val database = Fido2Database(driver)
            val preserved = database.passkeyCredentialQueries.select_by_id("legacy-credential").executeAsOne()
            assertEquals("legacy-credential", preserved.id)
            assertEquals(10L, preserved.created_at)
            assertEquals(11L, preserved.last_used_at)
            assertEquals(12L, preserved.updated_at)
            assertEquals("aaguid", preserved.aaguid)
            assertEquals(-7L, preserved.cose_algorithm)
            assertEquals("legacy-credential", preserved.credential_id)
            assertEquals(1L, preserved.cred_protect_policy)
            assertEquals("p256", preserved.derivation_profile)
            assertContentEquals(byteArrayOf(1, 2, 3), preserved.encrypted_metadata)
            assertEquals("legacy-label", preserved.label)
            assertEquals("public-key", preserved.public_key)
            assertContentEquals("example.com".encodeToByteArray(), preserved.rp_id)
            assertContentEquals(byteArrayOf(1), preserved.rp_id_index)
            assertContentEquals("example.com".encodeToByteArray(), preserved.rp_name)
            assertEquals(4L, preserved.sign_count)
            assertContentEquals("User".encodeToByteArray(), preserved.user_display_name)
            assertContentEquals("user-1".encodeToByteArray(), preserved.user_id)
            assertContentEquals(byteArrayOf(2), preserved.user_id_index)
            assertContentEquals("user".encodeToByteArray(), preserved.user_name)
            val viewRows =
                driver
                    .executeQuery(
                        identifier = null,
                        sql = "SELECT id FROM credential_summary WHERE id = 'legacy-credential'",
                        mapper = { cursor ->
                            val ids = mutableListOf<String>()
                            while (cursor.next().value) ids += requireNotNull(cursor.getString(0))
                            QueryResult.Value(ids)
                        },
                        parameters = 0,
                    ).value
            assertEquals(listOf("legacy-credential"), viewRows)

            val protectionService =
                mockk<CredentialMetadataProtectionService> {
                    every { getRpIdIndex(any()) } answers { "rp:${firstArg<CharSequence>()}".toByteArray() }
                    every { getUserIdIndex(any()) } answers { "user:${firstArg<CharSequence>()}".toByteArray() }
                    every { encryptCredentialMetadata(any(), any()) } returns byteArrayOf(9, 8, 7)
                }
            val id = testCredentialIdFromLabel("second-same-account")
            val publicKey =
                KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(256) }
                    .generateKeyPair()
                    .public
            val secondCredential =
                PasskeyCredential.create(
                    id = id,
                    rpId = RpId("example.com"),
                    userId = UserId("user-1"),
                    userName = "user",
                    userDisplayName = "User",
                    publicKey = publicKey,
                    aaguid = ByteArray(16),
                    credentialId = id.toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val dao = PasskeyCredentialDao(database, TimeProvider(), protectionService)
            runTest { dao.insertCredentialProjection(secondCredential) }

            val rows = database.passkeyCredentialQueries.select_all().executeAsList()
            assertEquals(setOf("legacy-credential", id.encoded), rows.map { it.id }.toSet())
            assertTrue(
                driver
                    .executeQuery(
                        identifier = null,
                        sql =
                            "SELECT name FROM sqlite_master " +
                                "WHERE type = 'index' AND name LIKE 'idx_passkey_credential_%'",
                        mapper = { cursor ->
                            val indexes = mutableSetOf<String>()
                            while (cursor.next().value) indexes += requireNotNull(cursor.getString(0))
                            QueryResult.Value(indexes)
                        },
                        parameters = 0,
                    ).value
                    .containsAll(
                        setOf(
                            "idx_passkey_credential_rp_id",
                            "idx_passkey_credential_user_id",
                            "idx_passkey_credential_rp_id_index",
                            "idx_passkey_credential_user_id_index",
                        ),
                    ),
            )
        } finally {
            driver.close()
        }
    }
}
