package com.chimali.fido2.domain.util

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import java.security.MessageDigest

/**
 * Compares public credential identifier bytes without String or value-class equality.
 * [CredentialId] has already validated and canonicalized each identifier, so padded and
 * unpadded Base64URL input forms compare by the same decoded bytes. The temporary arrays
 * contain public identifiers.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun CredentialId.matchesCredentialId(other: CredentialId): Boolean =
    withCredentialIdBytes(this) { thisBytes ->
        withCredentialIdBytes(other) { otherBytes -> MessageDigest.isEqual(thisBytes, otherBytes) }
    }

/** Borrows a decoded ID buffer only for [action], then erases the temporary bytes on every exit. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal inline fun <T> withCredentialIdBytes(
    credentialId: CredentialId,
    action: (ByteArray) -> T,
): T {
    val bytes = credentialId.toByteArray()
    return try {
        action(bytes)
    } finally {
        bytes.fill(0)
    }
}
