package com.chimali.core.security.api

/**
 * Service for generating deterministic lookup tokens for exact-match searchable metadata.
 *
 * Replaces the previous AES-SIV approach with HMAC-based blind indexing.
 */
interface MetadataLookupTokenService {
    companion object {
        const val VERSION_1 = 1

        // Domain separation constants for different metadata types
        const val DOMAIN_RP_ID = "chimali.rpid.v1"
        const val DOMAIN_USER_ID = "chimali.userid.v1"
        const val DOMAIN_LABEL = "chimali.label.v1"
    }

    /**
     * Generates a deterministic lookup token for the given metadata value.
     */
    fun generateToken(
        domain: String,
        value: String,
    ): ByteArray
}
