package com.chimali.core.security.hdkeys

import java.math.BigInteger
import java.security.SecureRandom
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint

/**
 * P-256 (secp256r1) prime-order group operations.
 *
 * Provides the elliptic curve primitives required by the HDK-ECDH-P256
 * instantiation as defined in IETF draft-dijkhuis-cfrg-hdkeys-06 §3.2.
 *
 * Uses Bouncy Castle for the underlying EC arithmetic.
 */
object P256Group {
    private val curveParams: X9ECParameters = CustomNamedCurves.getByName("secp256r1")
    private val curve = curveParams.curve
    private val random = SecureRandom()

    /** Generator point G of P-256. */
    val G: ECPoint = curveParams.g

    /** Order n of the P-256 curve. */
    val ORDER: BigInteger = curveParams.n

    /** Length of a serialized scalar in bytes (32). */
    const val SCALAR_LENGTH = 32

    /** Length of the x-coordinate output from ECDH (32 bytes). */
    const val DH_OUTPUT_LENGTH = 32

    /** Length of an uncompressed serialized point: 0x04 || x(32) || y(32). */
    const val ELEMENT_UNCOMPRESSED_LENGTH = 65
    internal const val UNCOMPRESSED_FORMAT_INDICATOR = 0x04

    /**
     * Generate a random scalar in [1, Order-1].
     */
    fun randomScalar(): BigInteger {
        var k: BigInteger
        do {
            k = BigInteger(ORDER.bitLength(), random).mod(ORDER)
        } while (k == BigInteger.ZERO)
        return k
    }

    /**
     * Scalar multiplication: compute k * P.
     */
    fun scalarMult(
        point: ECPoint,
        k: BigInteger,
    ): ECPoint {
        return point.multiply(k).normalize()
    }

    /**
     * Scalar-base multiplication: compute k * G.
     */
    fun scalarBaseMult(k: BigInteger): ECPoint {
        return scalarMult(G, k)
    }

    /**
     * EC point addition: compute A + B.
     */
    fun add(
        a: ECPoint,
        b: ECPoint,
    ): ECPoint {
        return a.add(b).normalize()
    }

    /**
     * Generate a key pair (sk, pk) where pk = sk * G.
     */
    fun generateKeyPair(): Pair<BigInteger, ECPoint> {
        val sk = randomScalar()
        val pk = scalarBaseMult(sk)
        return Pair(sk, pk)
    }

    /**
     * Serialize an EC point as uncompressed: 0x04 || x (32 bytes) || y (32 bytes).
     * Total: 65 bytes.
     */
    fun serializeElement(point: ECPoint): ByteArray {
        val normalized = point.normalize()
        return normalized.getEncoded(false) // uncompressed
    }

    /**
     * Deserialize an EC point from uncompressed encoding.
     */
    fun deserializeElement(encoded: ByteArray): ECPoint {
        return curve.decodePoint(encoded).normalize()
    }

    /**
     * Serialize a scalar as a 32-byte big-endian byte array (I2OSP).
     */
    fun serializeScalar(scalar: BigInteger): ByteArray {
        val bytes = scalar.toByteArray()
        return when {
            bytes.size == SCALAR_LENGTH -> bytes
            bytes.size > SCALAR_LENGTH -> bytes.takeLast(SCALAR_LENGTH).toByteArray()
            else -> ByteArray(SCALAR_LENGTH - bytes.size) + bytes
        }
    }

    /**
     * Deserialize a scalar from a 32-byte big-endian byte array (OS2IP).
     */
    fun deserializeScalar(bytes: ByteArray): BigInteger {
        return BigInteger(1, bytes)
    }

    /**
     * ECKA-DH: Create shared secret as the x-coordinate of sk * pk.
     * Returns the x-coordinate serialized as 32 bytes.
     *
     * Corresponds to CreateSharedSecret in the spec for EC-DH.
     */
    fun createSharedSecret(
        sk: BigInteger,
        pk: ECPoint,
    ): ByteArray {
        val sharedPoint = scalarMult(pk, sk).normalize()
        val x = sharedPoint.affineXCoord.toBigInteger()
        return serializeScalar(x)
    }
}
