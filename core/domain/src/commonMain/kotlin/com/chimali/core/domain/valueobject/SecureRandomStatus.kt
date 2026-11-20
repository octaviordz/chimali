package com.chimali.core.domain.valueobject

internal fun fillSecureRandomBytes(
    bytes: ByteArray,
    fill: (ByteArray) -> Int,
): ByteArray {
    var completed = false
    try {
        if (fill(bytes) != 0) error("Secure random generation failed")
        completed = true
        return bytes
    } catch (_: Exception) {
        error("Secure random generation failed")
    } finally {
        if (!completed) bytes.fill(0)
    }
}
