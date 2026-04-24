package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair

/*
 * T145a — Provides access to the master seed and the device root key pair for FIDO2
 * key derivation via [Fido2CryptoService].
 *
 * The master seed is a 32-byte secret that acts as the root of trust for all
 * FIDO2 credentials, satisfying NFR-SEC-040 (Master Key Management) and
 * FR-AUTH-030 (backup via Master Seed / Shamir's Secret Sharing).
 *
 * The device key pair is generated once per installation from the master seed
 * using [com.chimali.core.security.api.HdkManager.generateDeviceKeyPair]. Both
 * the seed and the private scalar must be stored encrypted (e.g., in SQLCipher)
 * and must never appear in plaintext logs or persistent storage.
 *
 * Implementations are responsible for:
 * - Providing the master seed when unlocked.
 * - Providing the device key pair derived from that seed.
 * - Returning null when the seed is not available (vault locked / not yet created).
 */

/**
 * Result of a [MasterSeedProvider.importMnemonic] operation.
 *
 * - [Created]  — no mnemonic existed; the provided one is now the active seed.
 * - [Replaced] — a mnemonic already existed and has been overwritten; any credentials
 *   derived from the previous seed are now orphaned and must be re-registered.
 */
sealed interface ImportMnemonicResult {
    data object Created : ImportMnemonicResult

    data object Replaced : ImportMnemonicResult
}

interface MasterSeedProvider {
    /**
     * Returns the 32-byte master seed, or null if not yet initialized or wallet is locked.
     */
    suspend fun getMasterSeed(): ByteArray?

    /**
     * Returns the device root [HdkKeyPair], or null if not initialized.
     * The private scalar must be kept in memory only for the duration of the signing
     * operation and should be zeroed out immediately after use.
     */
    suspend fun getDeviceKeyPair(): HdkKeyPair?

    /**
     * Returns the raw BIP39 mnemonic as an ordered word list, or null if not yet initialized.
     *
     * ⚠️ The caller is responsible for zeroing the returned list's backing arrays
     * immediately after use. This function must NOT be called outside [BuildConfig.DEBUG]
     * contexts in production code.
     */
    suspend fun getMnemonic(): List<String>?

    /**
     * T146g — Imports a BIP39 mnemonic, persisting it as the new active master seed.
     *
     * The [mnemonic] [CharArray] must contain exactly 24 space-separated words joined into
     * a single char sequence (e.g. `"word1 word2 … word24".toCharArray()`).  The array is
     * **zeroed immediately after use**; the caller must not rely on its content afterwards.
     *
     * If a mnemonic already exists it is **overwritten** and [ImportMnemonicResult.Replaced]
     * is returned so the caller can warn the user that existing credentials are now orphaned.
     *
     * @throws IllegalArgumentException if the word count is not exactly 24.
     */
    suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult

    /**
     * T017a — Returns a 64-byte BIP-85-derived child seed for the ML-DSA (PQ) key branch.
     *
     * Derived via fully-hardened CKD path `[83696968', 83286642', 2']` from the master seed
     * followed by HMAC-SHA512("bip-entropy-from-k", k) — the BIP-85 entropy extraction step.
     *
     * This seed is cryptographically isolated from the ECDSA key branch (index=1), ensuring
     * that compromise of one branch does not degrade the other (NIST SP 800-108 key separation).
     *
     * Returns null if the master seed is not yet available.
     */
    suspend fun getPqChildSeed(): ByteArray?
}
