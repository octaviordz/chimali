package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.HdkChild
import com.chimali.core.security.api.HdkManager
import java.math.BigInteger
import java.security.MessageDigest

/** Implements Chimali's local P-256 child relation from the byte-only HDK contract. */
@Req("NFR-SEC-041", "TR-AUTH-031")
object HdkP256 : HdkManager {
    private const val LOCAL_SECRET_LENGTH = 32

    /** Owned low-level result used while signing; callers erase factor and child scalar.
     * @property childPublicKey Owned uncompressed SEC1 P-256 point (65 bytes); public and not erased.
     * @property factor Owned 32-byte non-zero derivation factor; caller must erase after signing.
     * @property childPrivateScalar Owned 32-byte big-endian child scalar; caller must erase after signing.
     */
    class Result(
        val childPublicKey: ByteArray,
        val factor: ByteArray,
        val childPrivateScalar: ByteArray,
    ) {
        /** Erases the owned factor and child scalar; [childPublicKey] is public and remains available. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun eraseSecrets() {
            factor.fill(0)
            childPrivateScalar.fill(0)
        }
    }

    /**
     * Derives a child pair and verifies that the borrowed parent scalar belongs to the supplied
     * point. Child factors use the contract's non-zero `(digest mod (n-1))+1` mapping, so zero
     * child scalars are algebraically impossible; no modulo repair or alternate validation path is
     * needed. The JVM's temporary [BigInteger] values cannot be erased and remain operation-local.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deriveChild(
        localSecret: ByteArray,
        parentPrivateScalar: ByteArray,
        parentPublicKey: ByteArray,
        index: UInt,
    ): Result {
        require(localSecret.size == LOCAL_SECRET_LENGTH) { "Local credential secret must be 32 bytes" }
        val parentPoint = P256Group.deserializeElement(parentPublicKey)
        val parentScalar = P256Group.deserializeScalar(parentPrivateScalar)
        val expectedParentPoint = P256Group.scalarBaseMult(parentScalar)
        require(expectedParentPoint == parentPoint) { "Parent private and public keys do not correspond" }

        var digest: ByteArray? = null
        var factorBytes: ByteArray? = null
        var childPrivate: ByteArray? = null
        val context = ByteArray(P256Group.ELEMENT_UNCOMPRESSED_LENGTH + UINT_BYTES)
        val preimage = ByteArray(localSecret.size + context.size)
        try {
            parentPublicKey.copyInto(context)
            writeUInt32(index, context, P256Group.ELEMENT_UNCOMPRESSED_LENGTH)
            localSecret.copyInto(preimage)
            context.copyInto(preimage, localSecret.size)
            digest = MessageDigest.getInstance("SHA-256").digest(preimage)
            val factor = BigInteger(1, digest).mod(P256Group.ORDER - BigInteger.ONE).add(BigInteger.ONE)
            factorBytes = P256Group.serializeScalar(factor)
            val childPoint = P256Group.scalarMult(parentPoint, factor)
            childPrivate = P256Group.serializeScalar(parentScalar.multiply(factor).mod(P256Group.ORDER))
            val publicBytes = P256Group.serializeElement(childPoint)
            val result = Result(publicBytes, factorBytes, childPrivate)
            factorBytes = null
            childPrivate = null
            return result
        } finally {
            context.fill(0)
            preimage.fill(0)
            digest?.fill(0)
            factorBytes?.fill(0)
            childPrivate?.fill(0)
        }
    }

    /** Derives the public child from borrowed secret/public inputs and returns its caller-owned factor. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun deriveChild(
        localSecret: ByteArray,
        parentPublicKey: ByteArray,
        index: UInt,
    ): HdkChild {
        require(localSecret.size == LOCAL_SECRET_LENGTH) { "Local credential secret must be 32 bytes" }
        val parentPoint = P256Group.deserializeElement(parentPublicKey)
        var digest: ByteArray? = null
        var factorBytes: ByteArray? = null
        val context = ByteArray(P256Group.ELEMENT_UNCOMPRESSED_LENGTH + UINT_BYTES)
        val preimage = ByteArray(localSecret.size + context.size)
        try {
            parentPublicKey.copyInto(context)
            writeUInt32(index, context, P256Group.ELEMENT_UNCOMPRESSED_LENGTH)
            localSecret.copyInto(preimage)
            context.copyInto(preimage, localSecret.size)
            digest = MessageDigest.getInstance("SHA-256").digest(preimage)
            val factor = BigInteger(1, digest).mod(P256Group.ORDER - BigInteger.ONE).add(BigInteger.ONE)
            factorBytes = P256Group.serializeScalar(factor)
            val childPublicKey = P256Group.serializeElement(P256Group.scalarMult(parentPoint, factor))
            val child = HdkChild(childPublicKey, factorBytes)
            factorBytes = null
            return child
        } finally {
            context.fill(0)
            preimage.fill(0)
            digest?.fill(0)
            factorBytes?.fill(0)
        }
    }

    /** Combines borrowed valid parent scalar and factor inputs into a new child scalar. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun deriveChildPrivateKey(
        parentPrivateKey: ByteArray,
        factor: ByteArray,
    ): ByteArray {
        val parentScalar = P256Group.deserializeScalar(parentPrivateKey)
        val factorScalar = P256Group.deserializeScalar(factor)
        return P256Group.serializeScalar(parentScalar.multiply(factorScalar).mod(P256Group.ORDER))
    }

    private fun writeUInt32(
        value: UInt,
        destination: ByteArray,
        offset: Int,
    ) {
        destination[offset] = (value shr 24).toByte()
        destination[offset + 1] = (value shr 16).toByte()
        destination[offset + 2] = (value shr 8).toByte()
        destination[offset + 3] = value.toByte()
    }

    private const val UINT_BYTES = 4
}
