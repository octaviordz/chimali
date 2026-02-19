package com.chimali.core.security.hdkeys

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Implementation of hash_to_field from RFC 9380 using expand_message_xmd with SHA-256.
 *
 * This maps arbitrary byte strings to scalars in the P-256 scalar field,
 * as required by the key blinding scheme in draft-dijkhuis-cfrg-hdkeys-06 §3.3.
 */
object HashToScalar {

    /** Security parameter L = ceil((ceil(log2(p)) + k) / 8) where k=128 for P-256. */
    private const val L = 48

    /** SHA-256 output length in bytes. */
    private const val HASH_LENGTH = 32

    /**
     * expand_message_xmd as defined in RFC 9380 §5.3.1.
     *
     * Expands a message into [lenInBytes] pseudorandom bytes using SHA-256.
     *
     * @param msg The input message.
     * @param dst Domain separation tag (must be <= 255 bytes).
     * @param lenInBytes Desired output length.
     * @return Expanded uniform bytes.
     */
    fun expandMessageXmd(msg: ByteArray, dst: ByteArray, lenInBytes: Int): ByteArray {
        require(dst.size <= 255) { "DST must be at most 255 bytes" }
        require(lenInBytes <= 65535) { "lenInBytes must be at most 65535" }

        val ell = ceil(lenInBytes.toDouble() / HASH_LENGTH).toInt()
        require(ell <= 255) { "ell must be at most 255" }

        val dstPrime = dst + i2osp(dst.size, 1)
        val zPad = ByteArray(64) // SHA-256 block size = 64 bytes
        val libStr = i2osp(lenInBytes, 2)

        // b_0 = H(Z_pad || msg || l_i_b_str || I2OSP(0, 1) || DST_prime)
        val b0 = sha256(zPad, msg, libStr, i2osp(0, 1), dstPrime)

        val result = ByteArray(lenInBytes)
        var bPrev = sha256(b0, i2osp(1, 1), dstPrime)

        // Copy first block
        val copyLen1 = minOf(HASH_LENGTH, lenInBytes)
        bPrev.copyInto(result, 0, 0, copyLen1)

        for (i in 2..ell) {
            val bi = sha256(xor(b0, bPrev), i2osp(i, 1), dstPrime)
            val offset = (i - 1) * HASH_LENGTH
            val copyLen = minOf(HASH_LENGTH, lenInBytes - offset)
            bi.copyInto(result, offset, 0, copyLen)
            bPrev = bi
        }

        return result
    }

    /**
     * hash_to_field as defined in RFC 9380, mapping to a single scalar element.
     *
     * Produces a scalar in [0, Order-1] from arbitrary input.
     *
     * @param msg Input message bytes.
     * @param dst Domain separation tag as ASCII bytes.
     * @return A scalar (BigInteger) in the P-256 scalar field.
     */
    fun hashToScalar(msg: ByteArray, dst: ByteArray): BigInteger {
        val uniformBytes = expandMessageXmd(msg, dst, L)
        return BigInteger(1, uniformBytes).mod(P256Group.ORDER)
    }

    // --- Internal helpers ---

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts) {
            digest.update(part)
        }
        return digest.digest()
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "XOR operands must be the same length" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    /**
     * I2OSP: Integer to Octet String Primitive. Encodes [value] as [length] bytes big-endian.
     */
    internal fun i2osp(value: Int, length: Int): ByteArray {
        val result = ByteArray(length)
        var v = value
        for (i in length - 1 downTo 0) {
            result[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        return result
    }
}
