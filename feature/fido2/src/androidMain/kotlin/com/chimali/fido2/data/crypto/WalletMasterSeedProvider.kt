package com.chimali.fido2.data.crypto

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import co.touchlab.kermit.Logger
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.hdkeys.P256Group
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

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
 * ⚠️ **Migration note**: Any credentials registered with `EphemeralMasterSeedProvider`
 * (T145a era) are bound to a transient seed and will be orphaned. Users must re-register.
 */
@Single
class WalletMasterSeedProvider(
    private val context: Context,
    private val masterSeedGenerator: MasterSeedGenerator,
    private val hdkManager: HdkManager,
) : MasterSeedProvider {
    @Volatile
    private var cachedSeed: ByteArray? = null

    @Volatile
    private var cachedDeviceKeyPair: HdkKeyPair? = null

    @Volatile
    private var cachedPqChildSeed: ByteArray? = null

    private val initializationMutex = Mutex()

    override suspend fun getMasterSeed(): ByteArray? = ensureInitialized().first

    override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInitialized().second

    private suspend fun ensureInitialized(): Pair<ByteArray?, HdkKeyPair?> {
        if (cachedSeed != null) {
            return Pair(cachedSeed, cachedDeviceKeyPair)
        }

        return initializationMutex.withLock {
            // Double-check after acquiring lock
            if (cachedSeed != null) {
                return@withLock Pair(cachedSeed, cachedDeviceKeyPair)
            }

            val mnemonic = getOrCreateMnemonic()
            // BIP39 PBKDF2-SHA512 produces 64 bytes, but the HDK spec (§2.2) requires
            // Ns = 32 bytes for the seed.  We take the first 32 bytes as the HDK seed
            // and keep the full 64-byte material only for the device key pair derivation,
            // which operates via HMAC-SHA512 anyway (deriveDeviceKeyPair).
            val bip39Seed = masterSeedGenerator.deriveSeed(mnemonic)
            val hdkSeed = bip39Seed.copyOf(HDK_SEED_SIZE_32) // first 32 bytes → HDK Ns bytes (§2.2)

            cachedSeed = hdkSeed
            // Derive the device key pair deterministically from the master seed so it
            // is identical across app restarts. A random key pair here was the cause of
            // "Could not verify authentication signature" errors after restart.
            cachedDeviceKeyPair = deriveDeviceKeyPair(bip39Seed)

            bip39Seed.fill(0) // zeroise full 64-byte material; hdkSeed (a copy) is kept in cachedSeed

            Logger.d { "Master seed initialized from BIP39 mnemonic (word count: ${mnemonic.size})" }
            Pair(cachedSeed, cachedDeviceKeyPair)
        }
    }

    /**
     * Returns the persisted mnemonic, or generates and persists a new one on first call.
     */
    private fun getOrCreateMnemonic(): List<String> {
        val prefs = openEncryptedPrefs()
        val existing = prefs.getString(KEY_MNEMONIC, null)
        if (!existing.isNullOrBlank()) {
            Logger.d { "Loaded existing BIP39 mnemonic from secure storage" }
            return existing.split(" ")
        }

        Logger.i { "Generating new BIP39 mnemonic (first launch)" }
        val newMnemonic = masterSeedGenerator.generateMnemonic(wordCount = MNEMONIC_WORD_COUNT)
        prefs.edit {
            putString(KEY_MNEMONIC, newMnemonic.joinToString(" "))
        }
        return newMnemonic
    }

    private fun openEncryptedPrefs(): android.content.SharedPreferences {
        val masterKeyAlias =
            androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC,
            )
        return EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
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
            require(words.size == MNEMONIC_WORD_COUNT) {
                "Invalid mnemonic: expected $MNEMONIC_WORD_COUNT words, got ${words.size}."
            }

            val prefs = openEncryptedPrefs()
            val alreadyExisted = !prefs.getString(KEY_MNEMONIC, null).isNullOrBlank()

            prefs.edit(commit = true) {
                putString(KEY_MNEMONIC, mnemonicString)
            }

            invalidateCache()

            // Re-derive immediately so the new seed is live for any in-flight FIDO2 operations.
            ensureInitialized()

            Logger.i { "Master seed imported (${if (alreadyExisted) "replaced existing" else "first import"})" }
            return if (alreadyExisted) ImportMnemonicResult.Replaced else ImportMnemonicResult.Created
        } finally {
            mnemonic.fill('\u0000')
        }
    }

    @Synchronized
    private fun invalidateCache() {
        cachedSeed = null
        cachedDeviceKeyPair = null
        cachedPqChildSeed?.fill(0)
        cachedPqChildSeed = null
    }

    /**
     * Derives a stable P-256 device key pair deterministically from [masterSeed].
     *
     * Uses HMAC-SHA512("chimali_device_key_v1", masterSeed) and takes the first 32 bytes
     * as the private scalar (reduced mod P-256 order). This ensures the device key pair
     * is identical across app restarts, which is required because the signing formula
     * is  sk_device × blindingFactor mod n — any change in sk_device produces an
     * unverifiable signature.
     *
     * The key derivation is intentionally separate from the BIP-85 PQ branch so that
     * ECDSA keys and ML-DSA keys remain cryptographically isolated.
     */
    private fun deriveDeviceKeyPair(masterSeed: ByteArray): HdkKeyPair {
        val raw = hmacSha512("chimali_device_key_v1".toByteArray(Charsets.UTF_8), masterSeed)
        // Take the first 32 bytes as the private scalar (big-endian), reduced mod order.
        val skScalar = BigInteger(1, raw.copyOfRange(0, P256_SCALAR_SIZE_32)).mod(P256Group.ORDER)
        val pkPoint = P256Group.scalarBaseMult(skScalar)
        raw.fill(0) // zeroise immediately
        Logger.d { "Device key pair derived deterministically from master seed" }
        return HdkKeyPair(
            privateKey = P256Group.serializeScalar(skScalar),
            publicKey = P256Group.serializeElement(pkPoint),
        )
    }

    override suspend fun getPqChildSeed(): ByteArray? {
        cachedPqChildSeed?.let { return it }
        val master = getMasterSeed() ?: return null
        return synchronized(this) {
            cachedPqChildSeed ?: derivePqChildSeed(master).also { cachedPqChildSeed = it }
        }
    }

    // ── HDK DeriveSalt-based PQ branch seed derivation ──────────────────────

    /**
     * Derives a 64-byte deterministic child seed for the ML-DSA (post-quantum) key branch
     * using **HDK DeriveSalt** (§2.4 of `draft-dijkhuis-cfrg-hdkeys-06`) and HMAC-SHA512
     * expansion.
     *
     * ## Process
     *
     * 1. Derive a 32-byte salt via `DeriveSalt(masterSeed[0:32], "PQ_ML-DSA_Branch")`.
     * 2. Expand the salt to 64 bytes via `HMAC-SHA512("chimali_pq_seed_v1", pqSalt)`.
     *
     * ## Isolation from ECDSA branch
     *
     * The context string `"PQ_ML-DSA_Branch"` is unique and will never collide with
     * HDK-ECDH-P256 contexts (which use `ID || I2OSP(index, 4)` format per §2.3).
     * The output of this function is a raw seed for ML-DSA key generation — it is
     * **never** fed into [HdkManager.deriveHdk] or any HDK function.
     *
     * ## Clean break
     *
     * This replaces the former BIP-85-style derivation (`m/83696968'/83286642'/2'`).
     * The outputs are cryptographically incompatible — existing PQ keys derived via
     * the legacy path are invalidated.
     */
    private fun derivePqChildSeed(masterSeed: ByteArray): ByteArray {
        val hdkSeed = masterSeed.copyOf(HDK_SEED_SIZE_32)
        val pqContext = PQ_CONTEXT_STRING.toByteArray(Charsets.UTF_8)
        val pqSalt = hdkManager.deriveSalt(hdkSeed, pqContext)

        val expansionKey = PQ_EXPANSION_KEY.toByteArray(Charsets.UTF_8)
        val childSeed = hmacSha512(expansionKey, pqSalt)
        Logger.d { "PQ child seed derived via HDK DeriveSalt; seedLen=${childSeed.size}" }
        return childSeed
    }

    private fun hmacSha512(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    companion object {
        private const val MNEMONIC_WORD_COUNT = 24
        private const val HDK_SEED_SIZE_32 = 32
        private const val P256_SCALAR_SIZE_32 = 32
        private const val PQ_CONTEXT_STRING = "PQ_ML-DSA_Branch"
        private const val PQ_EXPANSION_KEY = "chimali_pq_seed_v1"
    }
}
