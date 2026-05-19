package com.chimali.core.database

import android.content.Context
import android.database.sqlite.SQLiteException
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import co.touchlab.kermit.Logger
import com.chimali.core.security.api.MasterSeedProvider
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Centrally manages SQLCipher database encryption for all feature databases (Constitution §I.3).
 * Enforces key zeroing in finally blocks (Constitution §X.5).
 */
class EncryptedDriverFactory(
    private val context: Context,
    private val masterSeedProvider: MasterSeedProvider,
) {
    init {
        try {
            // Initialize SQLCipher native libraries
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Logger.d(e) { "SQLCipher native library not loaded (expected during host unit tests)" }
        }
    }

    /**
     * Derives a stable 256-bit AES database encryption key from the high-entropy master seed
     * using PBKDF2-HMAC-SHA512 with 2048 iterations (Constitution §I.3).
     *
     * Enforces try/finally zeroing of key material (Constitution §X.5).
     */
    fun deriveDatabaseKey(seed: ByteArray): ByteArray {
        require(seed.isNotEmpty()) { "Master seed cannot be empty" }

        val hexChars = "0123456789abcdef".toCharArray()
        val hexPassword = CharArray(seed.size * HEX_CHARS_PER_BYTE)
        for (i in seed.indices) {
            val v = seed[i].toInt() and HEX_RADIX_MASK
            val baseIdx = i * HEX_CHARS_PER_BYTE
            hexPassword[baseIdx] = hexChars[v ushr HEX_SHIFT_BITS]
            hexPassword[baseIdx + 1] = hexChars[v and HEX_CHAR_MASK]
        }

        val salt = "chimali_db_salt".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(hexPassword, salt, PBKDF2_ITERATIONS, SQLCIPHER_KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val secretKey = factory.generateSecret(spec)
            val derived = secretKey.encoded
            require(derived != null && derived.isNotEmpty()) { "Derived database key cannot be empty" }
            return derived
        } finally {
            hexPassword.fill('\u0000')
            spec.clearPassword()
        }
    }

    /**
     * Creates an encrypted SqlDriver using SQLCipher and SupportOpenHelperFactory (Constitution §I.3).
     * The derived key material is zeroed immediately after driver construction.
     */
    fun createDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        name: String,
    ): SqlDriver {
        val seed =
            runBlocking { masterSeedProvider.getMasterSeed() }
                ?: error("Master seed not initialized. Database cannot be opened.")

        val derivedKey = deriveDatabaseKey(seed)
        try {
            val supportFactory = SupportOpenHelperFactory(derivedKey)
            return AndroidSqliteDriver(
                schema = schema,
                context = context,
                name = name,
                factory = supportFactory,
                cacheSize = 1,
            )
        } finally {
            derivedKey.fill(0)
        }
    }

    /**
     * Performs a physical PRAGMA integrity_check on the encrypted database file to ensure
     * encryption is sound and data is uncorrupted.
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
        } finally {
            try {
                driver?.close()
            } catch (ignored: SQLiteException) {
            } catch (ignored: IllegalStateException) {
            }
        }
    }

    companion object {
        private const val HEX_RADIX_MASK = 0xFF
        private const val HEX_SHIFT_BITS = 4
        private const val HEX_CHAR_MASK = 0x0F
        private const val HEX_CHARS_PER_BYTE = 2
        private const val PBKDF2_ITERATIONS = 2048
        private const val SQLCIPHER_KEY_LENGTH_BITS = 256
    }
}
