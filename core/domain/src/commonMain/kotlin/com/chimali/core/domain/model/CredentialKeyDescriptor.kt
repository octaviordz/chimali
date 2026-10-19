package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import kotlinx.serialization.Serializable

/** Public metadata required to reproduce a registered credential's current local key.
 *
 * @property credentialId Complete decoded credential identifier bound to the derived key.
 * @property coseAlgorithm Persisted COSE algorithm identifier; only an explicitly supported pair is accepted.
 * @property derivationProfile Exact versioned local derivation profile paired with [coseAlgorithm].
 */
@Serializable
data class CredentialKeyDescriptor(
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val credentialId: CredentialId,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val coseAlgorithm: Int,
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val derivationProfile: String,
) {
    init {
        require(profileFor(coseAlgorithm) == derivationProfile) {
            "Unsupported credential algorithm/profile pair"
        }
    }

    companion object {
        /** COSE algorithm identifier for ECDSA with SHA-256 (ES256). */
        const val COSE_ES256: Int = -7

        /** COSE algorithm identifier for EdDSA credentials in this profile. */
        const val COSE_EDDSA: Int = -8

        /** COSE algorithm identifier assigned to ML-DSA-65 by the current local profile. */
        const val COSE_ML_DSA_65: Int = -49

        /** Derivation profile identifier paired with [COSE_ES256]. */
        const val P256_PROFILE: String = "chimali-local-hdk-p256-v1"

        /** Derivation profile identifier paired with [COSE_EDDSA]. */
        const val ED25519_PROFILE: String = "chimali-local-ed25519-v1"

        /** Derivation profile identifier paired with [COSE_ML_DSA_65]. */
        const val MLDSA65_PROFILE: String = "chimali-local-mldsa65-v1"

        /** Returns the only profile registered for [coseAlgorithm], or throws for unsupported algorithms. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun profileFor(coseAlgorithm: Int): String =
            when (coseAlgorithm) {
                COSE_ES256 -> P256_PROFILE
                COSE_EDDSA -> ED25519_PROFILE
                COSE_ML_DSA_65 -> MLDSA65_PROFILE
                else -> throw IllegalArgumentException("Unsupported credential algorithm: $coseAlgorithm")
            }
    }
}
