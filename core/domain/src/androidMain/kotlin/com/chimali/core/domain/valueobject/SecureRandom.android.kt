package com.chimali.core.domain.valueobject

import java.security.SecureRandom

internal actual fun generateSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}
