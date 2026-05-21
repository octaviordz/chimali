package com.chimali.core.security.api

/**
 * Service for encrypting and decrypting metadata payloads.
 *
 * Uses AES-GCM for authenticated encryption with associated data (AEAD).
 */
interface EncryptedMetadataService {
    companion object {
        const val VERSION_1 = 1

        // Envelope constants
        const val NONCE_LENGTH_BYTES = 12 // 96 bits for GCM
        const val TAG_LENGTH_BYTES = 16 // 128 bits for GCM
    }

    /**
     * Encrypts metadata using AES-GCM with a unique nonce and optional associated data.
     */
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray

    /**
     * Decrypts AES-GCM encrypted metadata, verifying its authenticity.
     */
    fun decrypt(
        ciphertext: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray
}
