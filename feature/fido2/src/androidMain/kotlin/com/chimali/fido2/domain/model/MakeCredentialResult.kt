package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req

/**
 * Result of a successful FIDO2 credential registration.
 * Includes both the attestation object for the relying party and the
 * persisted [PasskeyCredential] for local display and management. Success transfers this result to its caller,
 * which must invoke [eraseSensitiveText] after its final use. UI state retains an independent credential copy.
 */
data class MakeCredentialResult(
    val attestationObject: AttestationObject,
    val credential: PasskeyCredential,
) {
    /** Releases transport-owned credential metadata and the independently owned attestation origin. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        credential.eraseSensitiveText()
        attestationObject.clientData.eraseSensitiveText()
    }
}
