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
    MessageDigest.isEqual(toByteArray(), other.toByteArray())
