package com.chimali.core.database

import app.cash.sqldelight.db.QueryResult
import io.toxicity.sqlite.mc.driver.EphemeralOpt
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.DatabasesDir
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Integration tests for [SQLiteMCDriver] verifying database encryption, integrity check,
 * key verification, and recovery mechanisms.
 *
 * DO-178B Traceability (§XII.1, §XII.3):
 * @see FR-MC-010 Migrate SQLCipher to SQLite3MultipleCiphers
 * @see FR-MC-020 ChaCha20-Poly1305 default cipher
 * @see FR-MC-040 SQLiteMCDriver configuration
 * @see FR-MC-070 JVM host tests pass without native library setup
 * @see Constitution §XII.1 Rigorous Traceability
 * @see Constitution §XII.3 High-Coverage Testing & Independence
 */
class SQLiteMCDriverIntegrationTest {
    /**
     * Verifies in-memory SQLiteMCDriver executes PRAGMA integrity_check successfully on JVM host.
     *
     * Traceability:
     * @see FR-MC-040
     * @see FR-MC-070
     * @see US2/AC3
     */
    @Test
    fun `in-memory SQLiteMCDriver returns ok for PRAGMA integrity_check without native library workaround`() {
        val factory =
            SQLiteMCDriver.Factory(
                dbName = "test_in_memory.db",
                schema = VaultDatabase.Schema,
            ) {}
        val driver = factory.createBlocking(EphemeralOpt.IN_MEMORY)

        try {
            val result =
                driver
                    .executeQuery(
                        identifier = null,
                        sql = "PRAGMA integrity_check",
                        mapper = { cursor ->
                            val hasRow = cursor.next().value
                            val status = if (hasRow) cursor.getString(0) else null
                            QueryResult.Value(status)
                        },
                        parameters = 0,
                    ).value

            assertEquals("ok", result?.lowercase(), "PRAGMA integrity_check must return 'ok'")
        } finally {
            driver.close()
        }
    }

    /**
     * Verifies that a file database encrypted with ChaCha20-Poly1305 is created and can be reopened
     * with the same key, passing PRAGMA integrity_check.
     *
     * Traceability:
     * @see FR-MC-020
     * @see FR-MC-040
     * @see US1/AC1
     * @see US1/AC2
     */
    @Test
    fun `encrypted SQLiteMCDriver with ChaCha20 creates and reopens database with correct key`(
        @TempDir tempDir: File,
    ) {
        val dbName = "encrypted_vault.db"
        val salt = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
        val keyBytes = ByteArray(32) { (it + 1).toByte() }

        val factory =
            SQLiteMCDriver.Factory(
                dbName = dbName,
                schema = VaultDatabase.Schema,
            ) {
                filesystem(DatabasesDir(tempDir))
            }

        val rawKey1 = Key.raw(key = keyBytes.copyOf(), salt = salt, fillKey = true)
        val driver1 = factory.createBlocking(rawKey1)

        val check1 =
            driver1
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA integrity_check",
                    mapper = { cursor ->
                        val hasRow = cursor.next().value
                        QueryResult.Value(if (hasRow) cursor.getString(0) else null)
                    },
                    parameters = 0,
                ).value
        assertEquals("ok", check1?.lowercase())
        driver1.close()

        val dbFile = File(tempDir, dbName)
        assertTrue(dbFile.exists(), "Encrypted database file must exist on disk")
        assertTrue(dbFile.length() > 0, "Encrypted database file must not be empty")

        // Reopen with the same key
        val rawKey2 = Key.raw(key = keyBytes.copyOf(), salt = salt, fillKey = true)
        val driver2 = factory.createBlocking(rawKey2)

        val check2 =
            driver2
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA integrity_check",
                    mapper = { cursor ->
                        val hasRow = cursor.next().value
                        QueryResult.Value(if (hasRow) cursor.getString(0) else null)
                    },
                    parameters = 0,
                ).value
        assertEquals("ok", check2?.lowercase())
        driver2.close()
    }

    /**
     * Verifies that opening an encrypted database with an incorrect key fails fast with an exception
     * rather than silent corruption (fail-secure state per Constitution §XII.5).
     *
     * Traceability:
     * @see FR-MC-020
     * @see FR-MC-040
     * @see US1/AC3
     * @see Constitution §XII.5
     */
    @Test
    fun `encrypted SQLiteMCDriver fails to open when supplied mismatched key`(
        @TempDir tempDir: File,
    ) {
        val dbName = "mismatched_key_vault.db"
        val salt = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
        val correctKey = ByteArray(32) { (it + 1).toByte() }
        val wrongKey = ByteArray(32) { (it + 99).toByte() }

        val factory =
            SQLiteMCDriver.Factory(
                dbName = dbName,
                schema = VaultDatabase.Schema,
            ) {
                filesystem(DatabasesDir(tempDir))
            }

        // Create with correct key
        val rawKey = Key.raw(key = correctKey.copyOf(), salt = salt, fillKey = true)
        val driver = factory.createBlocking(rawKey)
        driver.close()

        // Attempting to open with mismatched key must throw
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            val wrongRawKey = Key.raw(key = wrongKey.copyOf(), salt = salt, fillKey = true)
            factory.createBlocking(wrongRawKey)
        }
    }

    /**
     * Verifies deterministic recovery from incompatible legacy database format by deleting the corrupt
     * file and recreating a fresh database.
     *
     * Traceability:
     * @see FR-MC-020
     * @see FR-MC-040
     * @see Constitution §XII.2 Determinism & Predictable Execution
     * @see Constitution §XII.5 Fail-Safe Error Handling
     */
    @Test
    fun `recovering from incompatible legacy database deletes corrupted file and recreates fresh DB`(
        @TempDir tempDir: File,
    ) {
        val dbName = "legacy_incompatible.db"
        val corruptFile = File(tempDir, dbName)
        corruptFile.writeBytes("SQLite format 3\u0000plain sqlite legacy data".toByteArray(Charsets.UTF_8))

        val salt = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
        val keyBytes = ByteArray(32) { (it + 5).toByte() }

        val factory =
            SQLiteMCDriver.Factory(
                dbName = dbName,
                schema = VaultDatabase.Schema,
            ) {
                filesystem(DatabasesDir(tempDir))
            }

        // Direct attempt fails because file is plain SQLite / incompatible
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            val rawKey = Key.raw(key = keyBytes.copyOf(), salt = salt, fillKey = true)
            factory.createBlocking(rawKey)
        }

        // Simulate recovery by deleting the incompatible file
        corruptFile.delete()

        // Fresh creation now succeeds
        val freshKey = Key.raw(key = keyBytes.copyOf(), salt = salt, fillKey = true)
        val freshDriver = factory.createBlocking(freshKey)
        val check =
            freshDriver
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA integrity_check",
                    mapper = { cursor ->
                        val hasRow = cursor.next().value
                        QueryResult.Value(if (hasRow) cursor.getString(0) else null)
                    },
                    parameters = 0,
                ).value
        assertEquals("ok", check?.lowercase())
        freshDriver.close()
    }
}
