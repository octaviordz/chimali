package com.chimali.fido2.testing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId

/** Builds stable synthetic full-length identifiers from short human-readable test labels. */
@Req("NFR-SEC-041")
internal fun testCredentialIdFromLabel(label: String): CredentialId {
    val labelBytes = label.toByteArray(Charsets.UTF_8)
    return CredentialId.fromByteArray(labelBytes.copyOf(maxOf(CredentialId.MIN_CREDENTIAL_ID_BYTES, labelBytes.size)))
}
