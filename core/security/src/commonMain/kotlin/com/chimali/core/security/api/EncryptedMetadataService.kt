package com.chimali.core.security.api

/**
 * Service for encrypting and decrypting metadata payloads.
 *
 * Uses AES-GCM for authenticated encryption with associated data (AEAD). Inputs are borrowed
 * for the duration of each call and remain caller-owned; returned arrays are independent,
 * caller-owned buffers that callers must erase when their data lifetime ends. Implementations
 * or cryptographic providers may retain internal copies that callers cannot erase through this
 * contract.
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
     * Borrows [plaintext] and [associatedData] without transferring ownership; returns a new
     * ciphertext array owned by the caller, which must erase it when no longer needed.
     */
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray

    /**
     * Decrypts AES-GCM encrypted metadata, verifying its authenticity. Borrows [ciphertext]
     * and [associatedData] without transferring ownership; returns a new plaintext array owned
     * by the caller, which must erase it as soon as its use is complete.
     */
    fun decrypt(
        ciphertext: ByteArray,
        associatedData: ByteArray? = null,
    ): ByteArray
}
