package com.chimali.fido2.domain.model

import java.time.Instant

/**
 * Lightweight credential projection used exclusively for the **candidate selection**
 * phase of a `GetAssertion` ceremony.
 *
 * ## Why this exists
 *
 * The full [PasskeyCredential] domain model requires a `java.security.PublicKey`, which
 * is not stored in the database — it must be **derived on-demand** via the HDK (Hierarchical
 * Deterministic Key) engine (`HdkManager.deriveHdk`). That derivation costs ~50–80ms per call.
 *
 * Previously, `getCredentialsForRp()` ran a full HDK derivation for **every stored credential**
 * just to build the candidate list, even though `GetAssertionUseCase` only needs one credential
 * (the MRU or the allow-listed one). Combined with the derivation inside `sign()`, this caused
 * N+1 HDK calls per `GetAssertion` ceremony:
 *
 * ```
 * N credentials listed  →  N × HDK
 * 1 sign call           →  1 × HDK
 * Total                 →  (N+1) × HDK
 * ```
 *
 * ## How this fixes it
 *
 * [CredentialSummary] carries only the metadata columns that [SelectCredentialUseCase] needs
 * to pick the best candidate (MRU selection, allow-list filtering). No key material is included.
 * Key derivation runs exactly **once**, for the selected credential only, immediately before signing.
 *
 * ```
 * N candidates listed   →  0 × HDK  (pure DB read)
 * 1 hydration call      →  1 × HDK  (winner only)
 * 1 sign call           →  1 × HDK
 * Total                 →  2 × HDK  (constant, independent of N)
 * ```
 *
 * @property id            The credential's database primary key (string form of the credential UUID).
 * @property rpId          Relying party identifier (e.g. "example.com").
 * @property credentialId  Raw FIDO2 credential ID bytes (used to match allow-list entries).
 * @property lastUsedAt    Timestamp of last use; drives MRU selection when multiple candidates match.
 */
data class CredentialSummary(
    val id: String,
    val rpId: String,
    val credentialId: ByteArray,
    val lastUsedAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialSummary) return false
        return id == other.id && rpId == other.rpId && credentialId.contentEquals(other.credentialId)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        return result
    }
}
