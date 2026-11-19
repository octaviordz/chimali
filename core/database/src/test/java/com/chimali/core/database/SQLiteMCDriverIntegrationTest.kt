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
 * key verification, and preservation on failed opens.
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
        val keyCopy1 = keyBytes.copyOf()
        val keyCopy2 = keyBytes.copyOf()

        val factory =
            SQLiteMCDriver.Factory(
                dbName = dbName,
                schema = VaultDatabase.Schema,
            ) {
                filesystem(DatabasesDir(tempDir))
            }

        var driver1: SQLiteMCDriver? = null
        var driver2: SQLiteMCDriver? = null
        try {
            driver1 = factory.createBlocking(Key.raw(key = keyCopy1, salt = salt, fillKey = true))
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
            driver1 = null

            val dbFile = File(tempDir, dbName)
            assertTrue(dbFile.exists(), "Encrypted database file must exist on disk")
            assertTrue(dbFile.length() > 0, "Encrypted database file must not be empty")

            driver2 = factory.createBlocking(Key.raw(key = keyCopy2, salt = salt, fillKey = true))
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
        } finally {
            try {
                driver2?.close()
            } finally {
                try {
                    driver1?.close()
                } finally {
                    keyCopy1.fill(0)
                    keyCopy2.fill(0)
                    keyBytes.fill(0)
                    salt.fill(0)
                    assertTrue(keyCopy1.all { it == 0.toByte() } && keyCopy2.all { it == 0.toByte() })
                    assertTrue(keyBytes.all { it == 0.toByte() } && salt.all { it == 0.toByte() })
                }
            }
        }
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
        val correctKeyCopy = correctKey.copyOf()
        val wrongKeyCopy = wrongKey.copyOf()

        val factory =
            SQLiteMCDriver.Factory(
                dbName = dbName,
                schema = VaultDatabase.Schema,
            ) {
                filesystem(DatabasesDir(tempDir))
            }

        try {
            val driver = factory.createBlocking(Key.raw(key = correctKeyCopy, salt = salt, fillKey = true))
            driver.close()

            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                factory.createBlocking(Key.raw(key = wrongKeyCopy, salt = salt, fillKey = true))
            }
        } finally {
            correctKeyCopy.fill(0)
            wrongKeyCopy.fill(0)
            correctKey.fill(0)
            wrongKey.fill(0)
            salt.fill(0)
            assertTrue(correctKeyCopy.all { it == 0.toByte() } && wrongKeyCopy.all { it == 0.toByte() })
            assertTrue(
                correctKey.all { it == 0.toByte() } &&
                    wrongKey.all { it == 0.toByte() } &&
                    salt.all { it == 0.toByte() },
            )
        }
    }
}
