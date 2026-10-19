package com.chimali.core.security.api

import com.chimali.core.common.annotation.Req

/**
 * Generates keyed deterministic tokens for exact-match searchable metadata using domain separation.
 * Implementations may use JCA objects that retain provider-owned key copies beyond adapter cleanup;
 * callers must not interpret mutable-buffer clearing as proof of physical key erasure.
 */
interface MetadataLookupTokenService {
    companion object {
        /** Token format version included in every HMAC input. */
        const val VERSION_1 = 1

        /** Domain identifier for relying-party IDs. */
        const val DOMAIN_RP_ID = "chimali.rpid.v1"

        /** Domain identifier for user IDs. */
        const val DOMAIN_USER_ID = "chimali.userid.v1"

        /** Domain identifier for display labels. */
        const val DOMAIN_LABEL = "chimali.label.v1"
    }

    /**
     * Generates a deterministic token over the domain, [VERSION_1] and value using the active index key.
     * The operation borrows [domain] and [value], and returns a new byte array owned by the caller.
     *
     * @param domain borrowed domain-separation text
     * @param value borrowed metadata text
     * @return caller-owned token bytes; the caller must erase them when no longer needed
     * @throws MetadataLookupKeyUnavailableException when no index key is provisioned
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun generateToken(
        domain: String,
        value: CharSequence,
    ): ByteArray
}

/** Raised when searchable metadata lookup is attempted without an active index key. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class MetadataLookupKeyUnavailableException : IllegalStateException("Metadata lookup key unavailable")
