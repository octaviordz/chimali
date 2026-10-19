package com.chimali.core.domain.valueobject

import com.chimali.core.common.annotation.Req
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Platform-agnostic secure random generation.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal expect fun generateSecureRandomBytes(size: Int): ByteArray

/**
 * Typed representation of a FIDO2 credential identifier.
 *
 * Wraps the [encoded] Base64URL-safe string representation of a credential ID.
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
        val decoded = decodeAndValidate(encoded)
        require(encodeCanonical(decoded) == encoded) { "CredentialId must use canonical unpadded Base64URL" }
    }

    /** Canonical unpadded Base64URL form. */
    val normalized: String get() = encoded

    /**
     * Decodes the credential id to a byte array.
     * @return ByteArray representation of the credential id.
     */
    @Req("NFR-SEC-041")
    fun toByteArray(): ByteArray = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(encoded)

    /** Returns the wrapper's diagnostic form, including its canonical encoded identifier. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "CredentialId($encoded)"

    companion object {
        /** Default generated credential identifier length in bytes. */
        const val CREDENTIAL_ID_SIZE_BYTES = 32

        /** Smallest supported decoded credential identifier length in bytes. */
        const val MIN_CREDENTIAL_ID_BYTES = 16

        /** Largest supported decoded credential identifier length in bytes. */
        const val MAX_CREDENTIAL_ID_BYTES = 1023

        /**
         * Generates a new random CredentialId.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun generate(): CredentialId {
            val bytes = generateSecureRandomBytes(CREDENTIAL_ID_SIZE_BYTES)
            return fromByteArray(bytes)
        }

        /**
         * Reconstructs a [CredentialId] from a previously-stored byte array.
         */
        @Req("NFR-SEC-041")
        fun fromByteArray(bytes: ByteArray): CredentialId {
            require(bytes.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES) {
                "Credential ID must be between $MIN_CREDENTIAL_ID_BYTES " +
                    "and $MAX_CREDENTIAL_ID_BYTES bytes, got ${bytes.size}"
            }
            return CredentialId(encodeCanonical(bytes))
        }

        /**
         * Reconstructs a [CredentialId] from a previously-stored encoded string.
         */
        @Req("NFR-SEC-041")
        fun fromEncoded(encoded: String): CredentialId {
            val decoded = decodeAndValidate(encoded)
            return CredentialId(encodeCanonical(decoded))
        }

        private fun decodeAndValidate(encoded: String): ByteArray {
            require(encoded.isNotBlank()) { "CredentialId cannot be empty" }
            val decoded =
                try {
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(encoded)
                } catch (exception: IllegalArgumentException) {
                    throw IllegalArgumentException("CredentialId must be valid Base64URL", exception)
                }
            require(decoded.size in MIN_CREDENTIAL_ID_BYTES..MAX_CREDENTIAL_ID_BYTES) {
                "Credential ID must be between $MIN_CREDENTIAL_ID_BYTES " +
                    "and $MAX_CREDENTIAL_ID_BYTES bytes, got ${decoded.size}"
            }
            return decoded
        }

        private fun encodeCanonical(bytes: ByteArray): String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }
}
