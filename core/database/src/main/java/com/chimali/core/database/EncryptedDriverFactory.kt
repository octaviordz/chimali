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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking

private const val PBKDF2_BLOCK_COUNTER_BYTES = 4
private const val KEY_LENGTH_BYTES_PER_BIT = 8

/**
 * Centrally manages SQLite3MultipleCiphers database encryption for all feature databases (Constitution §I.4).
 * Uses ChaCha20-Poly1305 as the preferred default cipher.
 * Clears owned key buffers in finally blocks (Constitution §I.5 and ES-V.5); provider-owned copies
 * may remain outside this class's control.
 *
 * Traceability:
 * @see FR-MC-010 Migrate SQLCipher to SQLite3MultipleCiphers
 * @see FR-MC-020 ChaCha20-Poly1305 default cipher
 * @see FR-MC-030 PBKDF2-HMAC-SHA512 key derivation
 * @see FR-MC-040 SQLiteMCDriver configuration
 * @see Constitution ES-III.1 Rigorous Traceability
 * @see Constitution ES-III.2 Determinism & Predictable Execution
 * @see Constitution ES-III.4 Separation of Concerns (Partitioning)
 * @see Constitution ES-III.5 Fail-Safe Error Handling & Graceful Degradation
 */
class EncryptedDriverFactory(
    private val context: Context,
    private val masterSeedProvider: MasterSeedProvider,
) {
    /**
     * Derives a stable 256-bit database encryption key from the 32-byte wallet root PRK
     * using PBKDF2-HMAC-SHA512 with 2048 iterations (Constitution §I.4, ES-III.1).
     *
     * Adheres to ES-III.2: static constant reuse for salt and hex characters to avoid
     * dynamic allocation in hot paths; deterministic bounded iteration over seed length.
     * Clears owned key buffers in finally blocks (Constitution §I.5 and ES-V.5).
     *
     * @see FR-MC-030
     */
    fun deriveDatabaseKey(seed: ByteArray): ByteArray {
        require(seed.size == WALLET_ROOT_PRK_LENGTH_BYTES) { "Wallet root PRK must be exactly 32 bytes" }

        val hexPassword = ByteArray(seed.size * HEX_CHARS_PER_BYTE)
        for (i in seed.indices) {
            val v = seed[i].toInt() and HEX_RADIX_MASK
            val baseIdx = i * HEX_CHARS_PER_BYTE
            hexPassword[baseIdx] = HEX_CHARS[v ushr HEX_SHIFT_BITS].code.toByte()
            hexPassword[baseIdx + 1] = HEX_CHARS[v and HEX_CHAR_MASK].code.toByte()
        }

        val block = ByteArray(DB_PBE_SALT_BYTES.size + PBKDF2_BLOCK_COUNTER_BYTES)
        DB_PBE_SALT_BYTES.copyInto(block)
        block[block.lastIndex] = 1
        var u: ByteArray? = null
        var derived: ByteArray? = null
        var completed = false
        try {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(hexPassword, "HmacSHA512"))
            val first = mac.doFinal(block)
            u = first
            derived = first.copyOf(DB_KEY_LENGTH_BITS / KEY_LENGTH_BYTES_PER_BIT)
            repeat(PBKDF2_ITERATIONS - 1) {
                val previous = requireNotNull(u)
                val next = mac.doFinal(previous)
                previous.fill(0)
                u = next
                val output = requireNotNull(derived)
                for (i in output.indices) output[i] = (output[i].toInt() xor next[i].toInt()).toByte()
            }
            return requireNotNull(derived).also {
                derived = null
                completed = true
            }
        } finally {
            if (!completed) derived?.fill(0)
            hexPassword.fill(0)
            block.fill(0)
            u?.fill(0)
        }
    }

    /**
     * Creates an encrypted SqlDriver using SQLite3MultipleCiphers with ChaCha20-Poly1305 (Constitution §I.4).
     * The owned derived-key buffer is cleared after driver construction (Constitution §I.5 and ES-V.5);
     * provider-owned copies may remain outside this class's control.
     *
     * Existing database files are preserved when opening fails. The caller receives the open error
     * and can decide how to recover without this factory deleting user data or SQLite sidecars.
     *
     * @see FR-MC-020
     * @see FR-MC-040
     */
    fun createDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver =
        runBlocking {
            masterSeedProvider.withSessionOwner { _ ->
                val seed =
                    masterSeedProvider.getMasterSeed()
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

                try {
                    return@withSessionOwner openDriver()
                } finally {
                    seed.fill(0)
                }
            }
        }

    /**
     * Performs a physical PRAGMA integrity_check on the encrypted database file to ensure
     * encryption is sound and data is uncorrupted.
     *
     * Applies the fail-safe boundary by containing database exceptions and closing driver resources:
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
            Logger.w { "Database integrity check failed (${e::class.simpleName})" }
            return false
        } catch (e: IllegalStateException) {
            Logger.w { "Database integrity check failed (${e::class.simpleName})" }
            return false
        } catch (e: IllegalArgumentException) {
            Logger.w { "Database integrity check failed (${e::class.simpleName})" }
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
        private const val WALLET_ROOT_PRK_LENGTH_BYTES = 32
        private const val PBKDF2_ITERATIONS = 2048
        private const val DB_KEY_LENGTH_BITS = 256
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        private val DB_PBE_SALT_BYTES = "chimali_db_salt".toByteArray(Charsets.UTF_8)
        private val DB_SALT_BYTES = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
    }
}
