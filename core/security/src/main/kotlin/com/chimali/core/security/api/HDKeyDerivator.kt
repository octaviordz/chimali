package com.chimali.core.security.api

/**
 * Interface for deriving keys from a master seed following BIP32/44.
 */
interface HDKeyDerivator {
    /**
     * Derives a sub-key from the master seed for a specific path.
     * Example path: "m/44'/0'/0'/0/0"
     */
    fun deriveKey(seed: ByteArray, path: String): ByteArray
}
