package com.chimali.core.security.api

/**
 * Interface for encrypting and decrypting sensitive data.
 */
interface EncryptionManager {
    /**
     * Encrypts the given plaintext using the provided key. The arguments are borrowed and must
     * remain valid only for the duration of the call. The returned ciphertext is an independent
     * caller-owned buffer; the caller must erase it when its persistence or transfer operation ends.
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
    ): ByteArray

    /**
     * Decrypts the given ciphertext using the provided key. The arguments are borrowed and must
     * remain valid only for the duration of the call. The returned plaintext is an independent
     * caller-owned buffer; the caller must erase it on every terminal path.
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
    ): ByteArray
}
