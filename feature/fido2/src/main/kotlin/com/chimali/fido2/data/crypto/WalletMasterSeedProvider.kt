package com.chimali.fido2.data.crypto

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.hdkeys.P256Group
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class WalletMasterSeedProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val masterSeedGenerator: MasterSeedGenerator,
        private val hdkManager: HdkManager,
    ) : MasterSeedProvider {
        @Volatile
        private var cachedSeed: ByteArray? = null

        @Volatile
        private var cachedDeviceKeyPair: HdkKeyPair? = null

        @Volatile
        private var cachedPqChildSeed: ByteArray? = null

        override suspend fun getMasterSeed(): ByteArray? = ensureInitialized().first

        override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInitialized().second

        @Synchronized
        private fun ensureInitialized(): Pair<ByteArray?, HdkKeyPair?> {
            if (cachedSeed != null) {
                return Pair(cachedSeed, cachedDeviceKeyPair)
            }

            val mnemonic = getOrCreateMnemonic()
            // BIP39 PBKDF2-SHA512 produces 64 bytes, but the HDK spec (§2.2) requires
            // Ns = 32 bytes for the seed.  We take the first 32 bytes as the HDK seed
            // and keep the full 64-byte material only for the device key pair derivation,
            // which operates via HMAC-SHA512 anyway (deriveDeviceKeyPair).
            val bip39Seed = masterSeedGenerator.deriveSeed(mnemonic)
            val hdkSeed = bip39Seed.copyOf(32) // first 32 bytes → HDK Ns bytes (§2.2)

            cachedSeed = hdkSeed
            // Derive the device key pair deterministically from the master seed so it
            // is identical across app restarts. A random key pair here was the cause of
            // "Could not verify authentication signature" errors after restart.
            cachedDeviceKeyPair = deriveDeviceKeyPair(bip39Seed)

            bip39Seed.fill(0) // zeroise full 64-byte material; hdkSeed (a copy) is kept in cachedSeed

            Timber.d("Master seed initialized from BIP39 mnemonic (word count: %d)", mnemonic.size)
            return Pair(cachedSeed, cachedDeviceKeyPair)
        }

        /**
         * Returns the persisted mnemonic, or generates and persists a new one on first call.
         */
        private fun getOrCreateMnemonic(): List<String> {
            val prefs = openEncryptedPrefs()
            val existing = prefs.getString(KEY_MNEMONIC, null)
            if (!existing.isNullOrBlank()) {
                Timber.d("Loaded existing BIP39 mnemonic from secure storage")
                return existing.split(" ")
            }

            Timber.i("Generating new BIP39 mnemonic (first launch)")
            val newMnemonic = masterSeedGenerator.generateMnemonic(wordCount = 24)
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
                require(words.size == 24) {
                    "Invalid mnemonic: expected 24 words, got ${words.size}."
                }

                val prefs = openEncryptedPrefs()
                val alreadyExisted = !prefs.getString(KEY_MNEMONIC, null).isNullOrBlank()

                prefs.edit(commit = true) {
                    putString(KEY_MNEMONIC, mnemonicString)
                }

                invalidateCache()

                // Re-derive immediately so the new seed is live for any in-flight FIDO2 operations.
                ensureInitialized()

                Timber.i("Master seed imported (%s)", if (alreadyExisted) "replaced existing" else "first import")
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
            val skScalar = BigInteger(1, raw.copyOfRange(0, 32)).mod(P256Group.ORDER)
            val pkPoint = P256Group.scalarBaseMult(skScalar)
            raw.fill(0) // zeroise immediately
            Timber.d("Device key pair derived deterministically from master seed")
            return HdkKeyPair(skScalar, pkPoint)
        }

        // ── T017a: BIP-85-style PQ branch seed derivation ─────────────────────────

        /**
         * T017a — Returns a 64-byte BIP-85-derived child seed for the ML-DSA key branch.
         *
         * Process (mirroring the HHD blogpost + BIP-85 spec):
         * 1. Derive a BIP-32 master root key via HMAC-SHA512("Bitcoin seed", masterSeed).
         * 2. Apply three rounds of hardened CKD (adds 2^31 to each index):
         *    m/83696968’/83286642’/2’
         * 3. Run the BIP-85 entropy extraction: HMAC-SHA512("bip-entropy-from-k", k).
         *
         * The result is a 64-byte seed used to initialize a deterministic SecureRandom
         * for ML-DSA key generation.
         */
        override suspend fun getPqChildSeed(): ByteArray? {
            cachedPqChildSeed?.let { return it }
            val master = getMasterSeed() ?: return null
            return synchronized(this) {
                cachedPqChildSeed ?: derivePqChildSeed(master).also { cachedPqChildSeed = it }
            }
        }

        /**
         * Derives a 64-byte deterministic child seed for the ML-DSA (post-quantum) key branch
         * using **BIP-85** entropy extraction over a **BIP-32** hardened derivation path.
         *
         * ## Isolation from HDK spec
         *
         * This function uses BIP-32 hardened Child Key Derivation (CKD) as a child-seed
         * _extraction_ mechanism. This is **intentional and explicitly isolated** from the
         * HDK-ECDH-P256 derivation path:
         *
         * - The output of this function is a raw seed bytes for ML-DSA key generation — it
         *   is **never** fed into [HdkManager.deriveHdk] or any HDK function.
         * - The classical ECDSA branch ([getMasterSeed] → [HdkManager.deriveHdk]) and this PQ
         *   branch are **cryptographically isolated**: a compromise of one branch does not
         *   implicate the other.
         * - The BIP-32 usage here is purely a **BIP-85 compatibility tool** for deterministic
         *   entropy extraction — not an HDK path in any sense of `draft-dijkhuis-cfrg-hdkeys-06`.
         *
         * ## Derivation path
         *
         * 1. BIP-32 master root key: `HMAC-SHA512("Bitcoin seed", masterSeed)` (BIP-32 §Master key
         *    generation)
         * 2. Three rounds of hardened CKD via [ckdHard]: `m/83696968'/83286642'/2'`
         *    - `83696968'` = BIP-85 purpose namespace
         *    - `83286642'` = application number ("Tectonic" T9 encoding)
         *    - `2'`        = index for the PQ (ML-DSA) branch
         * 3. BIP-85 entropy: `HMAC-SHA512("bip-entropy-from-k", derivedKey)`
         *
         * The result is a 64-byte seed used to deterministically initialize ML-DSA key generation
         * in [PostQuantumCrypto.generateMlDsaKeyPair].
         */
        private fun derivePqChildSeed(masterSeed: ByteArray): ByteArray {
            // BIP-32 master root key from the master seed
            val masterRootKey = hmacSha512("Bitcoin seed".toByteArray(Charsets.UTF_8), masterSeed)
            val k = masterRootKey.copyOfRange(0, 32) // IL = key
            val c = masterRootKey.copyOfRange(32, 64) // IR = chain code

            // Three rounds of hardened CKD: [83696968', 83286642', 2']
            val hardenedOffset = 0x80000000L.toInt() // 2^31 as Int (wraps around)
            val path =
                intArrayOf(
                    83696968 + hardenedOffset, // "BIP85" purpose namespace (hardened)
                    83286642 + hardenedOffset, // HHD app_no = "Tectonic" T9 (hardened)
                    2 + hardenedOffset, // index=2 → PQ (Falcon/ML-DSA) branch (hardened)
                )

            var currentKey = k
            var currentChain = c
            for (index in path) {
                val (nextKey, nextChain) = ckdHard(currentKey, currentChain, index)
                currentKey = nextKey
                currentChain = nextChain
            }

            // BIP-85 entropy extraction: HMAC-SHA512("bip-entropy-from-k", derivedKey)
            val childSeed = hmacSha512("bip-entropy-from-k".toByteArray(Charsets.UTF_8), currentKey)
            Timber.d("PQ child seed derived; seedLen=%d", childSeed.size)
            return childSeed
        }

        /**
         * BIP-32 hardened Child Key Derivation (CKD) function.
         *
         * `I = HMAC-SHA512(key=chainCode, data=0x00 || parentKey || I2OSP(index, 4))`
         * Returns `(IL, IR)` = (new child key bytes, new child chain code bytes).
         *
         * ## Role in this codebase
         *
         * This is a **BIP-85 compatibility tool** used solely by [derivePqChildSeed] to
         * extract deterministic entropy for ML-DSA key generation. It is **not** part of the
         * HDK-ECDH-P256 derivation stack and has **no relation** to [HdkManager.deriveHdk]
         * or any function defined in `draft-dijkhuis-cfrg-hdkeys-06`. The BIP-32 semantics
         * (hardened index, chain code, "Bitcoin seed" HMAC key) are confined entirely to
         * the PQ branch. See [derivePqChildSeed] for the isolation rationale.
         */
        private fun ckdHard(
            parentKey: ByteArray,
            chainCode: ByteArray,
            index: Int,
        ): Pair<ByteArray, ByteArray> {
            val data = ByteArray(1 + 32 + 4)
            data[0] = 0x00
            parentKey.copyInto(data, 1)
            data[33] = ((index ushr 24) and 0xFF).toByte()
            data[34] = ((index ushr 16) and 0xFF).toByte()
            data[35] = ((index ushr 8) and 0xFF).toByte()
            data[36] = (index and 0xFF).toByte()
            val i = hmacSha512(chainCode, data)
            return Pair(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
        }

        private fun hmacSha512(
            key: ByteArray,
            data: ByteArray,
        ): ByteArray {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(key, "HmacSHA512"))
            return mac.doFinal(data)
        }
    }
