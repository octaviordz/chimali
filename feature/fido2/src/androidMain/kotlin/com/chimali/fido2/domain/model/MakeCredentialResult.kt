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
    /** Releases credential metadata and every mutable owner in the returned attestation. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        eraseSensitiveData()
    }

    /** Erases the independently owned credential and attestation response after final use. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveData() {
        credential.eraseSensitiveText()
        attestationObject.clientData.eraseSensitiveData()
        attestationObject.eraseSensitiveData()
    }
}
