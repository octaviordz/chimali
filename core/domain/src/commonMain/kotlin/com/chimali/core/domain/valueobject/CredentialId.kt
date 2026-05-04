package com.chimali.core.domain.valueobject

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Platform-agnostic secure random generation.
 */
internal expect fun generateSecureRandomBytes(size: Int): ByteArray

/**
 * Typed representation of a FIDO2 credential identifier.
 *
 * Carries both the raw [bytes] (32 cryptographically-secure random bytes) and the
 * [encoded] Base64URL-safe string representation. Holding both avoids repeated
 * String → ByteArray conversions (e.g. inside HDK derivation paths).
 *
 * ## Construction
 * - New credentials: use [CredentialId.generate] to create a fresh random ID.
 * - Existing credentials (loaded from DB as a string): use [CredentialId.fromEncoded].
 */
@OptIn(ExperimentalEncodingApi::class)
@Serializable
@JvmInline
value class CredentialId private constructor(
    /** Base64URL-safe, no-padding string encoding of the identifier. */
    val encoded: String,
) {
    init {
        require(encoded.isNotBlank()) { "CredentialId cannot be empty" }
    }

    /**
     * Normalized version of the encoded string (no padding).
     */
    val normalized: String get() = encoded.trimEnd('=')

    /**
     * Decodes the credential id to a byte array.
     * @return ByteArray representation of the credential id.
     */
    fun toByteArray(): ByteArray = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(encoded)

    override fun toString(): String = "CredentialId($encoded)"

    companion object {
        const val CREDENTIAL_ID_SIZE_BYTES = 32
        const val MIN_CREDENTIAL_ID_BYTES = 16
        const val MAX_CREDENTIAL_ID_BYTES = 1023

        /**
         * Generates a new random CredentialId.
         */
        fun generate(): CredentialId {
            val bytes = generateSecureRandomBytes(CREDENTIAL_ID_SIZE_BYTES)
            return fromByteArray(bytes)
        }

        /**
         * Reconstructs a [CredentialId] from a previously-stored byte array.
         */
        fun fromByteArray(bytes: ByteArray): CredentialId {
            require(bytes.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES) {
                "Credential ID must be between $MIN_CREDENTIAL_ID_BYTES " +
                    "and $MAX_CREDENTIAL_ID_BYTES bytes, got ${bytes.size}"
            }
            return CredentialId(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes))
        }

        /**
         * Reconstructs a [CredentialId] from a previously-stored encoded string.
         */
        fun fromEncoded(encoded: String): CredentialId = CredentialId(encoded.trimEnd('='))
    }
}
