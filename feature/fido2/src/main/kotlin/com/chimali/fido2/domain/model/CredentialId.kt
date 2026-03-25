package com.chimali.fido2.domain.model

import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Base64

/**
 * Typed representation of a FIDO2 credential identifier.
 *
 * Carries both the raw [bytes] (32 cryptographically-secure random bytes) and the
 * [encoded] Base64URL-safe string representation. Holding both avoids repeated
 * String → ByteArray conversions (e.g. inside HDK derivation paths).
 *
 * ## Construction
 * - New credentials: use [CredentialId.generate] to create a fresh random ID.
 * - Existing credentials (loaded from DB as a string): use [CredentialId.fromString].
 */
@JvmInline
value class CredentialId(
    /** Base64URL-safe, no-padding string encoding of the identifier. */
    val encoded: String
) {

    fun toByteArray(charset: Charset = Charsets.UTF_8): ByteArray {
        return encoded.toByteArray(charset)
    }

    override fun toString(): String = "CredentialId($encoded)"

    companion object {
        /**
         * Generates a new cryptographically secure random [CredentialId].
         */
        fun generate(): CredentialId {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return CredentialId(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }

        /**
         * Reconstructs a [CredentialId] from a previously-stored encoded string.
         */
        fun fromString(encoded: String): CredentialId = CredentialId(encoded)
    }
}
