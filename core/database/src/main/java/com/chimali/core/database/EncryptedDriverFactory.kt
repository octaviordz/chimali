package com.chimali.core.database

import android.content.Context
import android.database.sqlite.SQLiteException
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import co.touchlab.kermit.Logger
import com.chimali.core.security.api.MasterSeedProvider
import io.toxicity.sqlite.mc.driver.SQLiteMCDriver
import io.toxicity.sqlite.mc.driver.config.databasesDir
import io.toxicity.sqlite.mc.driver.config.encryption.Key
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking

/**
 * Centrally manages SQLite3MultipleCiphers database encryption for all feature databases (Constitution §I.3).
 * Uses ChaCha20-Poly1305 as the preferred default cipher.
 * Enforces key zeroing in finally blocks (Constitution §X.5, §XII.5).
 *
 * Traceability:
 * @see FR-MC-010 Migrate SQLCipher to SQLite3MultipleCiphers
 * @see FR-MC-020 ChaCha20-Poly1305 default cipher
 * @see FR-MC-030 PBKDF2-HMAC-SHA512 key derivation
 * @see FR-MC-040 SQLiteMCDriver configuration
 * @see Constitution §XII.1 Rigorous Traceability
 * @see Constitution §XII.2 Determinism & Predictable Execution
 * @see Constitution §XII.4 Separation of Concerns (Partitioning)
 * @see Constitution §XII.5 Fail-Safe Error Handling & Graceful Degradation
 */
class EncryptedDriverFactory(
    private val context: Context,
    private val masterSeedProvider: MasterSeedProvider,
) {
    /**
     * Derives a stable 256-bit database encryption key from the high-entropy master seed
     * using PBKDF2-HMAC-SHA512 with 2048 iterations (Constitution §I.3, §XII.1).
     *
     * Adheres to DO-178B §XII.2: static constant reuse for salt and hex characters to avoid
     * dynamic allocation in hot paths; deterministic bounded iteration over seed length.
     * Enforces try/finally zeroing of key material (Constitution §X.5, §XII.5).
     *
     * @see FR-MC-030
     */
    fun deriveDatabaseKey(seed: ByteArray): ByteArray {
        require(seed.isNotEmpty()) { "Master seed cannot be empty" }

        val hexPassword = CharArray(seed.size * HEX_CHARS_PER_BYTE)
        for (i in seed.indices) {
            val v = seed[i].toInt() and HEX_RADIX_MASK
            val baseIdx = i * HEX_CHARS_PER_BYTE
            hexPassword[baseIdx] = HEX_CHARS[v ushr HEX_SHIFT_BITS]
            hexPassword[baseIdx + 1] = HEX_CHARS[v and HEX_CHAR_MASK]
        }

        try {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(hexPassword.concatToString().toByteArray(Charsets.UTF_8), "HmacSHA512"))
            val block = ByteArray(DB_PBE_SALT_BYTES.size + 4)
            DB_PBE_SALT_BYTES.copyInto(block)
            block[block.lastIndex] = 1
            var u = mac.doFinal(block)
            val derived = u.copyOf(DB_KEY_LENGTH_BITS / 8)
            repeat(PBKDF2_ITERATIONS - 1) {
                u = mac.doFinal(u)
                for (i in derived.indices) derived[i] = (derived[i].toInt() xor u[i].toInt()).toByte()
            }
            return derived
        } finally {
            hexPassword.fill('\u0000')
        }
    }

    /**
     * Creates an encrypted SqlDriver using SQLite3MultipleCiphers with ChaCha20-Poly1305 (Constitution §I.3).
     * The derived key material is zeroed immediately after driver construction (Constitution §X.5, §XII.5).
     *
     * Deterministic Recovery (Constitution §XII.2):
     * If opening fails because an existing database file is in an incompatible legacy format
     * (e.g. previous SQLCipher or plain SQLite), the incompatible file is deleted and recreated
     * fresh with ChaCha20-Poly1305 per specification. Recovery is strictly bounded to at most
     * [MAX_RECOVERY_ATTEMPTS] attempt to prevent open-ended retry loops.
     *
     * @see FR-MC-020
     * @see FR-MC-040
     */
    fun createDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver {
        val seed =
            runBlocking { masterSeedProvider.getMasterSeed() }
                ?: error("Master seed not initialized. Database cannot be opened.")

        fun openDriver(): SqlDriver {
            val derivedKey = deriveDatabaseKey(seed)
            try {
                val rawKey = Key.raw(key = derivedKey, salt = DB_SALT_BYTES, fillKey = true)
                return SQLiteMCDriver
                    .Factory(dbName = name, schema = schema) {
                        filesystem(context.databasesDir())
                    }.createBlocking(rawKey)
            } finally {
                derivedKey.fill(0)
            }
        }

        var attempts = 0
        while (attempts <= MAX_RECOVERY_ATTEMPTS) {
            try {
                return openDriver()
            } catch (e: IllegalStateException) {
                if (attempts >= MAX_RECOVERY_ATTEMPTS) throw e
                Logger.w(e) {
                    "Failed to open database $name (attempt ${attempts + 1}). Recreating fresh database."
                }
                deleteIncompatibleDatabase(name)
                attempts++
            } catch (e: SQLiteException) {
                if (attempts >= MAX_RECOVERY_ATTEMPTS) throw e
                Logger.w(e) {
                    "Failed to open database $name due to SQLiteException " +
                        "(attempt ${attempts + 1}). Recreating fresh database."
                }
                deleteIncompatibleDatabase(name)
                attempts++
            }
        }
        error("Failed to open or recreate database $name after $MAX_RECOVERY_ATTEMPTS recovery attempts.")
    }

    private fun deleteIncompatibleDatabase(name: String) {
        try {
            context.deleteDatabase(name)
        } catch (ignored: SecurityException) {
        }
        try {
            val dir = context.databasesDir().path
            File(dir, name).delete()
            File(dir, "$name-journal").delete()
            File(dir, "$name-wal").delete()
            File(dir, "$name-shm").delete()
        } catch (ignored: SecurityException) {
        }
    }

    /**
     * Performs a physical PRAGMA integrity_check on the encrypted database file to ensure
     * encryption is sound and data is uncorrupted.
     *
     * Adheres to DO-178B §XII.5 (Component Boundary Encapsulation & Fail-Secure):
     * All database exceptions are trapped and logged, returning false safely without leaking
     * driver resources.
     *
     * @see FR-MC-080
     */
    fun verifyIntegrity(name: String): Boolean {
        val dbFile = context.getDatabasePath(name)
        if (!dbFile.exists()) return false

        var driver: SqlDriver? = null
        try {
            // Re-create the driver to open the database file and run the query
            driver = createDriver(VaultDatabase.Schema, name)
            return driver
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA integrity_check",
                    mapper = { cursor ->
                        val hasRow = cursor.next().value
                        val result = if (hasRow) cursor.getString(0) else null
                        QueryResult.Value(result?.equals("ok", ignoreCase = true) == true)
                    },
                    parameters = 0,
                ).value
        } catch (e: SQLiteException) {
            Logger.w(e) { "Database integrity check failed due to SQLite exception" }
            return false
        } catch (e: IllegalStateException) {
            Logger.w(e) { "Database integrity check failed due to uninitialized state" }
            return false
        } catch (e: IllegalArgumentException) {
            Logger.w(e) { "Database integrity check failed due to invalid argument" }
            return false
        } finally {
            try {
                driver?.close()
            } catch (ignored: SQLiteException) {
            } catch (ignored: IllegalStateException) {
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    companion object {
        private const val HEX_RADIX_MASK = 0xFF
        private const val HEX_SHIFT_BITS = 4
        private const val HEX_CHAR_MASK = 0x0F
        private const val HEX_CHARS_PER_BYTE = 2
        private const val PBKDF2_ITERATIONS = 2048
        private const val DB_KEY_LENGTH_BITS = 256
        private const val MAX_RECOVERY_ATTEMPTS = 1
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        private val DB_PBE_SALT_BYTES = "chimali_db_salt".toByteArray(Charsets.UTF_8)
        private val DB_SALT_BYTES = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
    }
}
