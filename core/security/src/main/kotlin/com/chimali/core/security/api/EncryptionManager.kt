package com.chimali.core.security.api

/**
 * Interface for encrypting and decrypting sensitive data.
 */
interface EncryptionManager {
    /**
     * Encrypts the given plaintext using the provided key.
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray

    /**
     * Decrypts the given ciphertext using the provided key.
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray
}
