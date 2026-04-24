package com.chimali.core.security.hdkeys

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * DHKEM(P-256, HKDF-SHA256) as defined in RFC 9180 §4.1.
 *
 * Provides Key Encapsulation Mechanism operations for remote HDK derivation.
 * Suite ID: "KEM" || I2OSP(0x0010, 2) — DHKEM(P-256, HKDF-SHA256).
 */
object DhKem {
    /** HPKE version label. */
    private val HPKE_LABEL = "HPKE-v1".toByteArray(Charsets.US_ASCII)

    /** Suite ID for DHKEM(P-256, HKDF-SHA256): "KEM" || I2OSP(0x0010, 2). */
    private val SUITE_ID =
        "KEM".toByteArray(Charsets.US_ASCII) +
            HashToScalar.i2osp(0x0010, HashToScalar.I2OSP_LEN_2)

    /** Length of shared secret output (32 bytes). */
    private const val N_SECRET = 32

    /** Length of a private key (32 bytes). */
    private const val N_SK = 32

    /** Bitmask for candidate key bytes (0xFF for P-256). */
    private const val BITMASK = 0xFF

    /** Maximum attempts for key derivation. */
    private const val MAX_DERIVE_ATTEMPTS = 255
    private const val MAX_DERIVE_COUNTER = 254

    // --- HKDF (RFC 5869) ---

    /**
     * HKDF-Extract: Extract a pseudorandom key from salt and input keying material.
     */
    fun extract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray {
        // RFC 5869 §2.2: if salt is not provided, it is set to a string of HashLen zeros
        val effectiveSalt = if (salt.isEmpty()) ByteArray(N_SECRET) else salt
        return hmacSha256(effectiveSalt, ikm)
    }

    /**
     * HKDF-Expand: Expand a pseudorandom key to the desired length.
     */
    fun expand(
        prk: ByteArray,
        info: ByteArray,
        len: Int,
    ): ByteArray {
        val result = ByteArray(len)
        var offset = 0
        var tPrev = ByteArray(0)
        var counter = 1

        while (offset < len) {
            tPrev = hmacSha256(prk, tPrev + info + HashToScalar.i2osp(counter, HashToScalar.I2OSP_LEN_1))
            val copyLen = minOf(N_SECRET, len - offset)
            tPrev.copyInto(result, offset, 0, copyLen)
            offset += copyLen
            counter++
        }

        return result
    }

    // --- HPKE Labeled Operations ---

    /**
     * LabeledExtract as defined in RFC 9180.
     */
    fun labeledExtract(
        salt: ByteArray,
        label: String,
        ikm: ByteArray,
    ): ByteArray {
        val labeledIkm = HPKE_LABEL + SUITE_ID + label.toByteArray(Charsets.US_ASCII) + ikm
        return extract(salt, labeledIkm)
    }

    /**
     * LabeledExpand as defined in RFC 9180.
     */
    fun labeledExpand(
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val labeledInfo =
            HashToScalar.i2osp(length, HashToScalar.I2OSP_LEN_2) +
                HPKE_LABEL + SUITE_ID +
                label.toByteArray(Charsets.US_ASCII) + info
        return expand(prk, labeledInfo, length)
    }

    /**
     * ExtractAndExpand: Combine DH shared secret with KEM context.
     */
    private fun extractAndExpand(
        dh: ByteArray,
        kemContext: ByteArray,
    ): ByteArray {
        val eaePrk = labeledExtract("".toByteArray(), "eae_prk", dh)
        return labeledExpand(eaePrk, "shared_secret", kemContext, N_SECRET)
    }

    // --- Core KEM Operations ---

    /**
     * DeriveKeyPair: Deterministically derive a key pair from input keying material.
     *
     * Follows the RFC 9180 §7.1.3 algorithm for P-256.
     *
     * @param ikm Input keying material.
     * @return (sk, pk) pair.
     */
    fun deriveKeyPair(ikm: ByteArray): Pair<BigInteger, ECPoint> {
        val dkpPrk = labeledExtract("".toByteArray(), "dkp_prk", ikm)

        DKP_PRK_LOOP@ for (counter in 0..MAX_DERIVE_COUNTER) {
            val candidateBytes =
                labeledExpand(
                    dkpPrk,
                    "candidate",
                    HashToScalar.i2osp(counter, HashToScalar.I2OSP_LEN_1),
                    N_SK,
                )
            // Apply bitmask to first byte
            candidateBytes[0] = (candidateBytes[0].toInt() and BITMASK).toByte()
            val sk = BigInteger(1, candidateBytes)
            if (sk != BigInteger.ZERO && sk < P256Group.ORDER) {
                val pk = P256Group.scalarBaseMult(sk)
                return Pair(sk, pk)
            }
        }

        throw IllegalStateException("DeriveKeyPair failed after $MAX_DERIVE_ATTEMPTS attempts")
    }

    /**
     * Encap: Encapsulate — generate a shared secret and encapsulated key.
     *
     * @param pkR Recipient's public key.
     * @return (sharedSecret, enc) where enc is the serialized ephemeral public key.
     */
    fun encap(pkR: ECPoint): Pair<ByteArray, ByteArray> {
        val (skE, pkE) = P256Group.generateKeyPair()
        val dh = P256Group.createSharedSecret(skE, pkR)
        val enc = P256Group.serializeElement(pkE)
        val pkRm = P256Group.serializeElement(pkR)
        val kemContext = enc + pkRm
        val sharedSecret = extractAndExpand(dh, kemContext)
        return Pair(sharedSecret, enc)
    }

    /**
     * Decap: Decapsulate — recover the shared secret from an encapsulated key.
     *
     * @param enc Encapsulated key (serialized ephemeral public key).
     * @param skR Recipient's private key.
     * @return Shared secret.
     */
    fun decap(
        enc: ByteArray,
        skR: BigInteger,
    ): ByteArray {
        val pkE = P256Group.deserializeElement(enc)
        val dh = P256Group.createSharedSecret(skR, pkE)
        val pkRm = P256Group.serializeElement(P256Group.scalarBaseMult(skR))
        val kemContext = enc + pkRm
        return extractAndExpand(dh, kemContext)
    }

    // --- Internal ---

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
