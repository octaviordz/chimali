package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import java.math.BigInteger
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint

/**
 * Validated P-256 (secp256r1) group operations for the current local profile.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
object P256Group {
    private val curveParams: X9ECParameters = CustomNamedCurves.getByName("secp256r1")
    private val curve = curveParams.curve

    /** Generator point G of P-256. */
    val G: ECPoint = curveParams.g

    /** Order n of the P-256 curve. */
    val ORDER: BigInteger = curveParams.n

    /** Length of a serialized scalar in bytes (32). */
    const val SCALAR_LENGTH = 32

    private const val SEC1_UNCOMPRESSED_PREFIX_LENGTH = 1
    private const val X_COORDINATE_OFFSET = SEC1_UNCOMPRESSED_PREFIX_LENGTH
    private const val Y_COORDINATE_OFFSET = X_COORDINATE_OFFSET + SCALAR_LENGTH
    private const val BIG_INTEGER_SIGN_PADDING_LENGTH = 1

    /** Length of an uncompressed serialized point: 0x04 || x(32) || y(32). */
    const val ELEMENT_UNCOMPRESSED_LENGTH = SEC1_UNCOMPRESSED_PREFIX_LENGTH + 2 * SCALAR_LENGTH

    /** SEC1 prefix byte identifying an uncompressed P-256 point. */
    internal const val UNCOMPRESSED_FORMAT_INDICATOR = 0x04

    /**
     * Scalar multiplication: compute k * P.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun scalarMult(
        point: ECPoint,
        k: BigInteger,
    ): ECPoint {
        requireScalar(k)
        requirePoint(point)
        return point.multiply(k).normalize()
    }

    /**
     * Scalar-base multiplication: compute k * G.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun scalarBaseMult(k: BigInteger): ECPoint = scalarMult(G, k)

    /**
     * Serialize an EC point as uncompressed: 0x04 || x (32 bytes) || y (32 bytes).
     * Total: 65 bytes.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun serializeElement(point: ECPoint): ByteArray {
        val normalized = point.normalize()
        requirePoint(normalized)
        return normalized.getEncoded(false) // uncompressed
    }

    /**
     * Deserialize an EC point from uncompressed encoding.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deserializeElement(encoded: ByteArray): ECPoint {
        require(encoded.size == ELEMENT_UNCOMPRESSED_LENGTH) { "P-256 point must be exactly 65 bytes" }
        require((encoded[0].toInt() and 0xff) == UNCOMPRESSED_FORMAT_INDICATOR) {
            "P-256 point must use uncompressed SEC1 encoding"
        }
        val fieldPrime = curve.field.characteristic
        val x = BigInteger(1, encoded.copyOfRange(X_COORDINATE_OFFSET, Y_COORDINATE_OFFSET))
        val y = BigInteger(1, encoded.copyOfRange(Y_COORDINATE_OFFSET, ELEMENT_UNCOMPRESSED_LENGTH))
        require(x < fieldPrime && y < fieldPrime) { "P-256 coordinates must be canonical field elements" }
        val point =
            try {
                curve.decodePoint(encoded).normalize()
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid P-256 point encoding", failure)
            }
        requirePoint(point)
        return point
    }

    /**
     * Serialize a scalar as a 32-byte big-endian byte array (I2OSP).
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun serializeScalar(scalar: BigInteger): ByteArray {
        requireScalar(scalar)
        val bytes = scalar.toByteArray()
        return try {
            when {
                bytes.size == SCALAR_LENGTH -> bytes.copyOf()
                bytes.size == SCALAR_LENGTH + BIG_INTEGER_SIGN_PADDING_LENGTH && bytes[0] == 0.toByte() ->
                    bytes.copyOfRange(BIG_INTEGER_SIGN_PADDING_LENGTH, bytes.size)
                bytes.size < SCALAR_LENGTH -> ByteArray(SCALAR_LENGTH - bytes.size) + bytes
                else -> throw IllegalArgumentException("P-256 scalar does not fit 32 bytes")
            }
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Deserialize a scalar from a 32-byte big-endian byte array (OS2IP).
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deserializeScalar(bytes: ByteArray): BigInteger {
        require(bytes.size == SCALAR_LENGTH) { "P-256 scalar must be exactly 32 bytes" }
        return BigInteger(1, bytes).also(::requireScalar)
    }

    private fun requireScalar(scalar: BigInteger) {
        require(scalar.signum() > 0 && scalar < ORDER) { "P-256 scalar must be in 1..n-1" }
    }

    private fun requirePoint(point: ECPoint) {
        require(!point.isInfinity && point.isValid && point.curve == curve) {
            "P-256 point must be finite, valid, and on the P-256 curve"
        }
    }
}
