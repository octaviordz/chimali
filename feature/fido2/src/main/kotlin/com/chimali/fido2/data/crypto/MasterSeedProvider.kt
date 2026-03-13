package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair

/**
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
}
