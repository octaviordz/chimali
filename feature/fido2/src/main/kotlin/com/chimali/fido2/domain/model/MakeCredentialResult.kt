package com.chimali.fido2.domain.model

/**
 * Result of a successful FIDO2 credential registration.
 * Includes both the attestation object for the relying party and the
 * persisted [PasskeyCredential] for local display and management.
 */
data class MakeCredentialResult(
    val attestationObject: AttestationObject,
    val credential: PasskeyCredential,
)
