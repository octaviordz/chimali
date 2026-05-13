package com.chimali.core.security.api

/**
 * A key pair for HDK operations.
 *
 * @property privateKey The private key scalar (32 bytes, big-endian).
 * @property publicKey The public key in uncompressed SEC1 encoding (65 bytes: 0x04 || X || Y).
 */
data class HdkKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HdkKeyPair) return false
        return privateKey.contentEquals(other.privateKey) &&
            publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

/**
 * Result of an HDK derivation step.
 *
 * @property publicKey The blinded public key at this path (uncompressed SEC1, 65 bytes).
 * @property salt The derived salt for further child derivation (32 bytes).
 * @property blindingFactor The combined blinding factor as raw scalar bytes (32 bytes, big-endian).
 */
data class HdkResult(
    val publicKey: ByteArray,
    val salt: ByteArray,
    val blindingFactor: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HdkResult) return false
        return publicKey.contentEquals(other.publicKey) &&
            salt.contentEquals(other.salt) &&
            blindingFactor.contentEquals(other.blindingFactor)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + blindingFactor.contentHashCode()
        return result
    }
}
