package com.chimali.core.security.util

/**
 * Utility class for handling sensitive data in memory.
 */
object SecureMemory {
    /**
     * Wipes a byte array by filling it with zeros.
     */
    fun wipe(array: ByteArray) {
        array.fill(0)
    }

    /**
     * Wipes a char array by filling it with zeros.
     */
    fun wipe(array: CharArray) {
        array.fill('\u0000')
    }

    /**
     * Executes the block with the sensitive data and wipes it immediately after.
     */
    inline fun <T> useSensitive(
        data: ByteArray,
        block: (ByteArray) -> T,
    ): T {
        try {
            return block(data)
        } finally {
            wipe(data)
        }
    }
}
