package com.chimali.core.security.api

/**
 * T019a — Interface for AES-256-SIV (Synthetic IV) deterministic authenticated encryption.
 *
 * Unlike [EncryptionManager] (AES-256-GCM), this interface guarantees deterministic
 * output for identical plaintext+key pairs, enabling encrypted metadata indexing.
 *
 * ## When to use SIV vs GCM
 *
 * | Scenario | Use |
 * |---|---|
 * | Searchable metadata tags (RP ID tags, credential aliases) | **SivEncryptionManager** |
 * | Key wrapping / master key boundaries | **SivEncryptionManager** |
 * | In-flight credential blob encryption (Constitution §I.1) | [EncryptionManager] (GCM) |
 *
 * Implementation: [com.chimali.core.security.impl.AesSivEncryptionManager]
 * Key requirement: 64 bytes (512 bits) — two 256-bit sub-keys.
 */
interface SivEncryptionManager {
    /**
     * Deterministically encrypts [plaintext] using AES-256-SIV.
     *
     * @param plaintext Data to encrypt.
     * @param key       64-byte SIV key.
     * @return Ciphertext = SIV_tag (16 bytes) || encrypted_data.
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray

    /**
     * Decrypts [ciphertext] and verifies its authenticity.
     *
     * @param ciphertext Output of [encrypt].
     * @param key        64-byte SIV key matching the one used for encryption.
     * @return Decrypted plaintext.
     * @throws SecurityException if authentication fails.
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray
}
