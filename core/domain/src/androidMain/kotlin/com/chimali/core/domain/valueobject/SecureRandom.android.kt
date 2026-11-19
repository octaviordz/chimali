package com.chimali.core.domain.valueobject

import com.chimali.core.common.annotation.Req
import java.security.SecureRandom

@Req("NFR-SEC-041", "TR-AUTH-031")
internal actual fun generateSecureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    return fillSecureRandomBytes(bytes) { output ->
        SecureRandom().nextBytes(output)
        0
    }
}
