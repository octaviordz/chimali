package com.chimali.core.security.api

import com.chimali.core.common.annotation.Req

/**
 * An owned P-256 parent key-pair copy.
 *
 * [privateKey] is a mutable 32-byte unsigned big-endian scalar; [publicKey] is its canonical
 * 65-byte uncompressed SEC1 point. The receiving operation owns both arrays and must erase the
 * private scalar in `finally`. Provider caches must return new arrays rather than this instance.
 */
@Req("NFR-SEC-041")
class HdkKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    init {
        require(privateKey.size == PRIVATE_KEY_BYTES) { "P-256 private scalar must be 32 bytes" }
        require(publicKey.size == PUBLIC_KEY_BYTES) { "P-256 public point must be 65 bytes" }
    }

    /** Erases the operation-owned private scalar. The public point is not secret. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun erasePrivateKey() {
        privateKey.fill(0)
    }

    /** Compares both key arrays by content; this ordinary equality check is not constant-time. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is HdkKeyPair &&
                    privateKey.contentEquals(other.privateKey) &&
                    publicKey.contentEquals(other.publicKey)
            )

    /** Returns a content-derived collection hash; it is not a cryptographic digest. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int = 31 * privateKey.contentHashCode() + publicKey.contentHashCode()

    private companion object {
        const val PRIVATE_KEY_BYTES = 32
        const val PUBLIC_KEY_BYTES = 65
    }
}

/**
 * Result of one P-256 child derivation. The public point is non-secret; [factor] is an
 * operation-owned 32-byte scalar factor and must be erased by its current operation owner.
 */
@Req("NFR-SEC-041")
class HdkChild(
    val publicKey: ByteArray,
    val factor: ByteArray,
) {
    init {
        require(publicKey.size == PUBLIC_KEY_BYTES) { "P-256 public point must be 65 bytes" }
        require(factor.size == FACTOR_BYTES) { "P-256 child factor must be 32 bytes" }
    }

    /** Erases the operation-owned child scalar factor. The public point remains unchanged. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseFactor() {
        factor.fill(0)
    }

    /** Compares the public point and factor by content; this ordinary equality check is not constant-time. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is HdkChild && publicKey.contentEquals(other.publicKey) && factor.contentEquals(other.factor))

    /** Returns a content-derived collection hash; it is not a cryptographic digest. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + factor.contentHashCode()

    private companion object {
        const val PUBLIC_KEY_BYTES = 65
        const val FACTOR_BYTES = 32
    }
}

/** Public-only generation result; it carries no parent scalar, seed, or child factor. */
@Req("NFR-SEC-041")
data class GeneratedCredentialPublicKey(
    val publicKey: ByteArray,
)

/** Result of replacing or initially importing the wallet recovery mnemonic. */
sealed interface ImportMnemonicResult {
    /** Indicates that the wallet had no recovery mnemonic and the import created one. */
    data object Created : ImportMnemonicResult

    /** Indicates that the import replaced the wallet's existing recovery mnemonic. */
    data object Replaced : ImportMnemonicResult
}
