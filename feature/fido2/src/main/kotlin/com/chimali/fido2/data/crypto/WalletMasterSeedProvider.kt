package com.chimali.fido2.data.crypto

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalletMasterSeedProvider"
private const val PREFS_FILE_NAME = "chimali_wallet_seed"
private const val KEY_MNEMONIC = "bip39_mnemonic"

/**
 * T145c — Persistent [MasterSeedProvider] backed by BIP39 and [EncryptedSharedPreferences].
 *
 * On the first launch, a fresh 24-word BIP39 mnemonic is generated with [MasterSeedGenerator],
 * stored encrypted on-device via [EncryptedSharedPreferences], and the corresponding 64-byte
 * PBKDF2 seed is derived and returned.
 *
 * On subsequent launches, the persisted mnemonic is read from encrypted storage and the same
 * deterministic seed is re-derived, ensuring FIDO2 credentials remain valid across restarts.
 *
 * **AES note**: The mnemonic string is stored as a SharedPreferences *value*, which
 * [EncryptedSharedPreferences] protects with AES-256-GCM, satisfying Constitution §I.
 *
 * **Device key pair**: Derived once from the seed and cached in memory for the
 * lifetime of the process. The private scalar must never appear in plaintext logs.
 *
 * ⚠️ **Migration note**: Any credentials registered with [EphemeralMasterSeedProvider]
 * (T145a era) are bound to a transient seed and will be orphaned. Users must re-register.
 */
@Singleton
class WalletMasterSeedProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val masterSeedGenerator: MasterSeedGenerator,
    private val hdkManager: HdkManager
) : MasterSeedProvider {

    @Volatile
    private var cachedSeed: ByteArray? = null

    @Volatile
    private var cachedDeviceKeyPair: HdkKeyPair? = null

    override suspend fun getMasterSeed(): ByteArray? = ensureInitialized().first

    override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInitialized().second

    @Synchronized
    private fun ensureInitialized(): Pair<ByteArray?, HdkKeyPair?> {
        if (cachedSeed != null) {
            return Pair(cachedSeed, cachedDeviceKeyPair)
        }

        val mnemonic = getOrCreateMnemonic()
        val seed = masterSeedGenerator.deriveSeed(mnemonic)

        cachedSeed = seed
        cachedDeviceKeyPair = hdkManager.generateDeviceKeyPair()

        Log.d(TAG, "Master seed initialized from BIP39 mnemonic (word count: ${mnemonic.size})")
        return Pair(cachedSeed, cachedDeviceKeyPair)
    }

    /**
     * Returns the persisted mnemonic, or generates and persists a new one on first call.
     */
    private fun getOrCreateMnemonic(): List<String> {
        val prefs = openEncryptedPrefs()
        val existing = prefs.getString(KEY_MNEMONIC, null)
        if (!existing.isNullOrBlank()) {
            Log.d(TAG, "Loaded existing BIP39 mnemonic from secure storage")
            return existing.split(" ")
        }

        Log.i(TAG, "Generating new BIP39 mnemonic (first launch)")
        val newMnemonic = masterSeedGenerator.generateMnemonic(wordCount = 24)
        prefs.edit()
            .putString(KEY_MNEMONIC, newMnemonic.joinToString(" "))
            .apply()
        return newMnemonic
    }

    private fun openEncryptedPrefs(): android.content.SharedPreferences {
        val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
            androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
        )
        return EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * T146g — Returns the raw BIP39 mnemonic for Dev Tools (debug only).
     * Reads back the persisted mnemonic from [EncryptedSharedPreferences] and splits
     * on spaces. Returns null if no mnemonic has been persisted yet.
     *
     * ⚠️ Caller must zero backing structures immediately after use.
     */
    override suspend fun getMnemonic(): List<String>? {
        val raw = openEncryptedPrefs().getString(KEY_MNEMONIC, null)
        return if (raw.isNullOrBlank()) null else raw.split(" ")
    }

    /**
     * T146g — Imports a BIP39 mnemonic, replacing any existing seed.
     *
     * The [mnemonic] [CharArray] is zeroed after use regardless of success or failure.
     * Cache is invalidated on success so the next [getMasterSeed]/[getDeviceKeyPair] call
     * re-derives from the new seed.
     *
     * @throws IllegalArgumentException if the word count is not exactly 24.
     */
    override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult {
        try {
            val mnemonicString = String(mnemonic)
            val words = mnemonicString.split(" ")
            require(words.size == 24) {
                "Invalid mnemonic: expected 24 words, got ${words.size}."
            }

            val prefs = openEncryptedPrefs()
            val alreadyExisted = !prefs.getString(KEY_MNEMONIC, null).isNullOrBlank()

            prefs.edit()
                .putString(KEY_MNEMONIC, mnemonicString)
                .commit() // commit() (synchronous) guarantees disk write before cache invalidation

            invalidateCache()

            // Re-derive immediately so the new seed is live for any in-flight FIDO2 operations.
            ensureInitialized()

            Log.i(TAG, "Master seed imported (${if (alreadyExisted) "replaced existing" else "first import"})")
            return if (alreadyExisted) ImportMnemonicResult.Replaced else ImportMnemonicResult.Created
        } finally {
            mnemonic.fill('\u0000')
        }
    }

    @Synchronized
    private fun invalidateCache() {
        cachedSeed = null
        cachedDeviceKeyPair = null
    }
}
