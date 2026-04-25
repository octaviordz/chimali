package com.chimali.core.security.hdkeys

import java.math.BigInteger
import org.bouncycastle.math.ec.ECPoint

/**
 * Multiplicative key blinding scheme for HDK-ECDH-P256.
 *
 * Implements the BL operations defined in draft-dijkhuis-cfrg-hdkeys-06 §3.2.2
 * using the multiplicative blinding approach over the P-256 group.
 *
 * DST = "ECDH Key Blind" for the HDK-ECDH-P256 instantiation.
 */
object MultiplicativeBlinding {
    /** Domain separation tag for HDK-ECDH-P256 blinding. */
    val DST = "ECDH Key Blind".toByteArray(Charsets.US_ASCII)

    /**
     * DeriveBlindKey: Derive a blind key from input keying material.
     *
     * bk = SerializeScalar(HashToScalar(ikm))
     *
     * @param ikm Input keying material (typically a salt).
     * @return Blind key as 32 bytes.
     */
    fun deriveBlindKey(ikm: ByteArray): ByteArray {
        val scalar = HashToScalar.compute(ikm, DST)
        return P256Group.serializeScalar(scalar)
    }

    /**
     * DeriveBlindingFactor: Derive a blinding factor from a blind key and context.
     *
     * bf = HashToScalar(bk || 0x00 || ctx)
     *
     * @param bk Blind key (32 bytes).
     * @param ctx Context bytes.
     * @return Blinding factor as a scalar.
     */
    fun deriveBlindingFactor(
        bk: ByteArray,
        ctx: ByteArray,
    ): BigInteger {
        val input = bk + byteArrayOf(0x00) + ctx
        return HashToScalar.compute(input, DST)
    }

    /**
     * BlindPublicKey: Blind a public key using a blind key and context.
     *
     * pk' = ScalarMult(pk, DeriveBlindingFactor(bk, ctx))
     *
     * @param pk Public key (EC point).
     * @param bk Blind key (32 bytes).
     * @param ctx Context bytes.
     * @return Blinded public key.
     */
    fun blindPublicKey(
        pk: ECPoint,
        bk: ByteArray,
        ctx: ByteArray,
    ): ECPoint {
        val bf = deriveBlindingFactor(bk, ctx)
        return P256Group.scalarMult(pk, bf)
    }

    /**
     * BlindPrivateKey: Blind a private key by multiplying with the blinding factor.
     *
     * sk' = sk * bf mod Order()
     *
     * @param sk Private key scalar.
     * @param bf Blinding factor scalar.
     * @return Blinded private key scalar.
     */
    fun blindPrivateKey(
        sk: BigInteger,
        bf: BigInteger,
    ): BigInteger {
        return sk.multiply(bf).mod(P256Group.ORDER)
    }

    /**
     * Combine: Combine two blinding factors.
     *
     * bf = bf1 * bf2 mod Order()
     *
     * @param bf1 First blinding factor.
     * @param bf2 Second blinding factor.
     * @return Combined blinding factor.
     */
    fun combine(
        bf1: BigInteger,
        bf2: BigInteger,
    ): BigInteger {
        return bf1.multiply(bf2).mod(P256Group.ORDER)
    }

    /**
     * BlindDH: Create a blinded ECDH shared secret.
     *
     * Used for proof of possession: the device computes a shared secret
     * with the reader without needing the blinded private key directly.
     *
     * sharedSecret = CreateSharedSecret(sk, ScalarMult(pkReader, bf))
     *
     * @param sk Device private key scalar.
     * @param bf Combined blinding factor.
     * @param pkReader Reader's public key.
     * @return Shared secret bytes.
     */
    fun blindDh(
        sk: BigInteger,
        bf: BigInteger,
        pkReader: ECPoint,
    ): ByteArray {
        val blindedReaderPk = P256Group.scalarMult(pkReader, bf)
        return P256Group.createSharedSecret(sk, blindedReaderPk)
    }
}
