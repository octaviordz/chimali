package com.chimali.core.security.impl

import com.chimali.core.security.api.SivEncryptionManager
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.modes.SICBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.koin.core.annotation.Single

/**
 * T019a — AES-256-SIV (Synthetic IV / Deterministic Authenticated Encryption) implementation.
 *
 * ## Purpose (Constitution §I.2)
 *
 * The Chimali Constitution mandates **AES-256-SIV** for:
 * 1. **Searchable encrypted metadata** — category names, RP ID lookup tags, credential
 *    aliases — where deterministic ciphertext is required for exact-match queries.
 * 2. **Key wrapping** - master key boundaries (e.g., within Proto DataStore)
 *    where nonce-misuse resistance is paramount.
 *
 * Unlike AES-256-GCM, SIV produces identical ciphertext for identical plaintext+key.
 * This is **intentional** and enables encrypted indexing without an IV.
 *
 * ## Construction (RFC 5297 §2)
 *
 * ```
 * SIV-AES-256(K, P):
 *   K1 || K2 = K[0..31] || K[32..63]   (two 128-bit sub-keys)
 *   S = S2V(K1, P)                      // synthetic IV via CMAC
 *   C = AES-CTR-256(K2, IV=S, P)        // encrypt with IV
 *   return S || C
 * ```
 *
 * Ciphertext length = plaintext length + 16 (SIV tag).
 *
 * **Security constraint**: Do NOT use the same key for both SIV and GCM contexts.
 * Use [com.chimali.core.security.api.EncryptionManager] for non-deterministic GCM payloads.
 *
 * @see [RFC 5297](https://www.rfc-editor.org/rfc/rfc5297)
 */
@Single
class AesSivEncryptionManager : SivEncryptionManager {
    companion object {
        /** AES block size in bytes. Always 16 for AES. */
        private const val AES_BLOCK_SIZE = 16

        /** SIV tag length in bytes. */
        private const val SIV_TAG_LENGTH = AES_BLOCK_SIZE

        /** Required key length: 512 bits = two 256-bit sub-keys. */
        private const val KEY_LENGTH = 64
    }

    /**
     * Encrypts [plaintext] deterministically using AES-256-SIV with [key].
     *
     * @param plaintext Byte data to encrypt (e.g., a metadata string's UTF-8 bytes).
     * @param key       64-byte SIV key (K1 || K2). MUST NOT be the same as any GCM key.
     * @return Ciphertext = SIV_tag (16 bytes) || encrypted_data (len(plaintext) bytes).
     * @throws IllegalArgumentException if [key] is not 64 bytes.
     */
    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(key.size == KEY_LENGTH) {
            "AES-SIV key must be $KEY_LENGTH bytes (512 bits), got ${key.size}"
        }

        val k1 = key.copyOfRange(0, 32)
        val k2 = key.copyOfRange(32, 64)

        // Step 1: Compute Synthetic IV via S2V
        val siv = s2v(k1, plaintext)

        // Step 2: Encrypt plaintext with AES-CTR using SIV as the IV
        // Clear the top two bits of SIV per RFC 5297 §2.6 to suppress carry
        val ctrIv = siv.clone()
        ctrIv[8] = (ctrIv[8].toInt() and 0x7F).toByte()
        ctrIv[12] = (ctrIv[12].toInt() and 0x7F).toByte()

        val ciphertext = aesCtr(k2, ctrIv, plaintext)

        k1.fill(0)
        k2.fill(0)

        return siv + ciphertext
    }

    /**
     * Decrypts [ciphertext] using AES-256-SIV with [key] and verifies authenticity.
     *
     * @param ciphertext Output of [encrypt]: SIV_tag (16 bytes) || encrypted_data.
     * @param key        64-byte SIV key identical to the one used during encryption.
     * @return Decrypted plaintext.
     * @throws IllegalArgumentException if [key] is not 64 bytes or ciphertext is too short.
     * @throws SecurityException if authentication tag verification fails (tampered data).
     */
    override fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(key.size == KEY_LENGTH) {
            "AES-SIV key must be $KEY_LENGTH bytes (512 bits), got ${key.size}"
        }
        require(ciphertext.size >= SIV_TAG_LENGTH) {
            "Ciphertext too short: expected >= $SIV_TAG_LENGTH bytes, got ${ciphertext.size}"
        }

        val k1 = key.copyOfRange(0, 32)
        val k2 = key.copyOfRange(32, 64)

        val siv = ciphertext.copyOfRange(0, SIV_TAG_LENGTH)
        val encryptedData = ciphertext.copyOfRange(SIV_TAG_LENGTH, ciphertext.size)

        // Step 1: Decrypt with AES-CTR
        val ctrIv = siv.clone()
        ctrIv[8] = (ctrIv[8].toInt() and 0x7F).toByte()
        ctrIv[12] = (ctrIv[12].toInt() and 0x7F).toByte()

        val plaintext = aesCtr(k2, ctrIv, encryptedData)

        // Step 2: Re-compute S2V and verify
        val expectedSiv = s2v(k1, plaintext)

        k1.fill(0)
        k2.fill(0)

        // Constant-time comparison to prevent timing attacks
        if (!constantTimeEquals(siv, expectedSiv)) {
            plaintext.fill(0) // Zero plaintext before throwing
            throw SecurityException("AES-SIV authentication tag mismatch — data may be tampered")
        }

        return plaintext
    }

    // ── RFC 5297 §2.4: S2V — the synthetic IV derivation ─────────────────────

    /**
     * Computes S2V(K, msg) per RFC 5297 §2.4 (single-message, no associated data).
     */
    private fun s2v(
        k1: ByteArray,
        msg: ByteArray,
    ): ByteArray {
        val zero = ByteArray(AES_BLOCK_SIZE)

        // D = AES-CMAC(K, zero)
        var d = cmac(k1, zero)

        if (msg.size >= AES_BLOCK_SIZE) {
            // xorend(msg, D) — XOR D into the last 16 bytes of msg, then CMAC
            val xored = msg.clone()
            for (i in 0 until AES_BLOCK_SIZE) {
                xored[xored.size - AES_BLOCK_SIZE + i] =
                    (xored[xored.size - AES_BLOCK_SIZE + i].toInt() xor d[i].toInt()).toByte()
            }
            d = cmac(k1, xored)
        } else {
            // dbl(D) xor pad(msg)
            d = dbl(d)
            val padded = ByteArray(AES_BLOCK_SIZE)
            msg.copyInto(padded)
            padded[msg.size] = 0x80.toByte()
            for (i in padded.indices) {
                d[i] = (d[i].toInt() xor padded[i].toInt()).toByte()
            }
            d = cmac(k1, d)
        }

        return d
    }

    /** AES-CMAC(key, data) — 16-byte tag. */
    private fun cmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = CMac(AESEngine.newInstance())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(mac.macSize)
        mac.doFinal(out, 0)
        return out
    }

    /** GF(2^128) doubling — left-shift with XOR of 0x87 if MSB was set. */
    private fun dbl(b: ByteArray): ByteArray {
        val result = ByteArray(b.size)
        val msb = (b[0].toInt() and 0x80) != 0
        for (i in 0 until b.size - 1) {
            result[i] = ((b[i].toInt() shl 1) or ((b[i + 1].toInt() ushr 7) and 0x01)).toByte()
        }
        result[b.size - 1] = (b[b.size - 1].toInt() shl 1).toByte()
        if (msb) result[b.size - 1] = (result[b.size - 1].toInt() xor 0x87).toByte()
        return result
    }

    /** AES-CTR encryption/decryption (symmetric). */
    private fun aesCtr(
        key: ByteArray,
        iv: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val engine = SICBlockCipher.newInstance(AESEngine.newInstance())
        engine.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(data.size)
        var offset = 0
        while (offset < data.size) {
            val block = minOf(AES_BLOCK_SIZE, data.size - offset)
            if (block == AES_BLOCK_SIZE) {
                engine.processBlock(data, offset, output, offset)
            } else {
                // Partial final block
                val tmp = ByteArray(AES_BLOCK_SIZE)
                data.copyInto(tmp, 0, offset, offset + block)
                val outTmp = ByteArray(AES_BLOCK_SIZE)
                engine.processBlock(tmp, 0, outTmp, 0)
                outTmp.copyInto(output, offset, 0, block)
            }
            offset += AES_BLOCK_SIZE
        }
        return output
    }

    /** Constant-time byte array comparison to prevent timing side-channels. */
    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
