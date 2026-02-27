package com.chimali.feature.fido2.internal

/**
 * Utility for securely zeroing out sensitive byte arrays in volatile memory.
 * Constitution Principle I: Sensitive data must only exist in decrypted form within
 * volatile memory using mutable structures that are explicitly zeroed out after use.
 */
object SecureMemory {
    /**
     * Zeroes out the given byte array in place.
     * Use this immediately after a credential key or CTAP response has been consumed.
     */
    fun zeroOut(data: ByteArray) {
        data.fill(0)
    }

    /**
     * Zeroes out the given char array in place.
     * Use this for any passphrase or PIN handling.
     */
    fun zeroOut(data: CharArray) {
        data.fill('\u0000')
    }

    /**
     * Executes a block with the given byte array, then zeroes it out.
     */
    inline fun <R> withSecure(data: ByteArray, block: (ByteArray) -> R): R {
        return try {
            block(data)
        } finally {
            zeroOut(data)
        }
    }
}
