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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.security.PublicKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Verifies database migrations preserve passkeys and remove optional consent context. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class Fido2DatabaseCredentialMigrationTest {
    private val ownedBuffers = mutableListOf<ByteArray>()
    private val mocksToClear = mutableListOf<Any>()

    private fun own(buffer: ByteArray): ByteArray {
        ownedBuffers += buffer
        return buffer
    }

    private fun <T : Any> ownMock(mock: T): T {
        mocksToClear += mock
        return mock
    }

    @Test
    fun `migration clears historical optional consent context`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                identifier = null,
                sql =
                    """
                    CREATE TABLE user_consent_record (
                        id TEXT NOT NULL PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        biometric_used INTEGER NOT NULL DEFAULT 0,
                        credential_id TEXT,
                        device_id TEXT,
                        ip_address TEXT,
                        operation_type TEXT NOT NULL,
                        pin_used INTEGER NOT NULL DEFAULT 0,
                        rp_id BLOB NOT NULL,
                        rp_id_index BLOB,
                        user_agent TEXT
                    )
                    """.trimIndent(),
                parameters = 0,
            )
            driver.execute(
                identifier = null,
                sql =
                    """
                    INSERT INTO user_consent_record (
                        id, created_at, timestamp, updated_at, credential_id,
                        device_id, ip_address, operation_type, rp_id, user_agent
                    ) VALUES (
                        'synthetic-consent', 1, 1, 1, 'synthetic-credential',
                        'synthetic-device', 'synthetic-ip', 'GET', X'010203', 'synthetic-agent'
                    )
                    """.trimIndent(),
                parameters = 0,
            )

            Fido2Database.Schema.migrate(driver, oldVersion = 3, newVersion = 4)

            val migrated =
                driver
                    .executeQuery(
                        identifier = null,
                        sql =
                            """
                            SELECT device_id, ip_address, user_agent, credential_id, rp_id
                            FROM user_consent_record WHERE id = 'synthetic-consent'
                            """.trimIndent(),
                        mapper = { cursor ->
                            check(cursor.next().value)
                            QueryResult.Value(
                                listOf(
                                    cursor.getString(0),
                                    cursor.getString(1),
                                    cursor.getString(2),
                                    cursor.getString(3),
                                    cursor.getBytes(4),
                                ),
                            )
                        },
                        parameters = 0,
                    ).value

            assertEquals(null, migrated[0])
            assertEquals(null, migrated[1])
            assertEquals(null, migrated[2])
            assertEquals("synthetic-credential", migrated[3])
            assertContentEquals(byteArrayOf(1, 2, 3), migrated[4] as ByteArray)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `migration preserves credentials and allows multiple credentials for an account`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val rowBuffers = mutableListOf<ByteArray>()
        var secondCredential: PasskeyCredential? = null
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
                    CREATE TABLE user_consent_record (
                        id TEXT NOT NULL PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        biometric_used INTEGER NOT NULL DEFAULT 0,
                        credential_id TEXT,
                        device_id TEXT,
                        ip_address TEXT,
                        operation_type TEXT NOT NULL,
                        pin_used INTEGER NOT NULL DEFAULT 0,
                        rp_id BLOB NOT NULL,
                        rp_id_index BLOB,
                        user_agent TEXT
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
            rowBuffers +=
                listOfNotNull(
                    preserved.encrypted_metadata,
                    preserved.rp_id,
                    preserved.rp_id_index,
                    preserved.rp_name,
                    preserved.user_display_name,
                    preserved.user_id,
                    preserved.user_id_index,
                    preserved.user_name,
                )
            assertEquals("legacy-credential", preserved.id)
            assertEquals(10L, preserved.created_at)
            assertEquals(11L, preserved.last_used_at)
            assertEquals(12L, preserved.updated_at)
            assertEquals("aaguid", preserved.aaguid)
            assertEquals(-7L, preserved.cose_algorithm)
            assertEquals("legacy-credential", preserved.credential_id)
            assertEquals(1L, preserved.cred_protect_policy)
            assertEquals("p256", preserved.derivation_profile)
            assertContentEquals(own(byteArrayOf(1, 2, 3)), preserved.encrypted_metadata)
            assertEquals("legacy-label", preserved.label)
            assertEquals("public-key", preserved.public_key)
            assertContentEquals(own("example.com".encodeToByteArray()), preserved.rp_id)
            assertContentEquals(own(byteArrayOf(1)), preserved.rp_id_index)
            assertContentEquals(own("example.com".encodeToByteArray()), preserved.rp_name)
            assertEquals(4L, preserved.sign_count)
            assertContentEquals(own("User".encodeToByteArray()), preserved.user_display_name)
            assertContentEquals(own("user-1".encodeToByteArray()), preserved.user_id)
            assertContentEquals(own(byteArrayOf(2)), preserved.user_id_index)
            assertContentEquals(own("user".encodeToByteArray()), preserved.user_name)
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
                ownMock(
                    mockk<CredentialMetadataProtectionService> {
                        every { getRpIdIndex(any()) } answers { own(byteArrayOf(0x31)) }
                        every { getUserIdIndex(any()) } answers { own(byteArrayOf(0x32)) }
                        every { encryptCredentialMetadata(any(), any()) } answers { own(byteArrayOf(9, 8, 7)) }
                    },
                )
            val id = testCredentialIdFromLabel("second-same-account")
            val publicKey =
                ownMock(
                    mockk<PublicKey> {
                        every { encoded } returns own("synthetic-public-key".encodeToByteArray())
                    },
                )
            val credentialBytes = own(id.toByteArray())
            secondCredential =
                PasskeyCredential.create(
                    id = id,
                    rpId = RpId("example.com"),
                    userId = UserId("user-1"),
                    userName = "user",
                    userDisplayName = "User",
                    publicKey = publicKey,
                    aaguid = own(ByteArray(16)),
                    credentialId = credentialBytes,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val dao = PasskeyCredentialDao(database, TimeProvider(), protectionService)
            runTest { dao.insertCredentialProjection(requireNotNull(secondCredential)) }

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
            secondCredential?.eraseSensitiveText()
            rowBuffers.forEach { it.fill(0) }
            ownedBuffers.forEach { it.fill(0) }
            ownedBuffers.clear()
            mocksToClear.forEach { clearMocks(it) }
            mocksToClear.clear()
            driver.close()
        }
    }
}
