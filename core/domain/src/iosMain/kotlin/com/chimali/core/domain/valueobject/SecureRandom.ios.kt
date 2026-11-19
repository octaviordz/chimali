package com.chimali.core.domain.valueobject

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun generateSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    return fillSecureRandomBytes(bytes) { output ->
        output.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
    }
}
