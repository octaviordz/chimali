package com.chimali.core.security.impl

import android.content.Context
import com.chimali.core.security.api.MasterSeedGenerator
import org.koin.core.annotation.Single
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * BIP39-compliant implementation of [MasterSeedGenerator].
 *
 * Uses the official 2048-word BIP39 English wordlist from assets/bip39_english.txt.
 * Mnemonic generation follows the BIP39 spec:
 *   1. Generate N bytes of cryptographically secure entropy.
 *   2. Compute SHA-256 hash; take the first (N*8/32) bits as the checksum.
 *   3. Concatenate entropy bits + checksum bits.
 *   4. Split into groups of 11 bits; each group is an index into the 2048-word wordlist.
 *
 * Seed derivation uses PBKDF2-HMAC-SHA512 with 2048 iterations and the "mnemonic[passphrase]"
 * salt as specified in BIP39.
 */
@Single
class Bip39MasterSeedGenerator(
    private val context: Context,
) : MasterSeedGenerator {
    private val wordList: List<String> by lazy {
        context.assets.open("bip39_english.txt").bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .also { require(it.size == 2048) { "BIP39 wordlist must contain exactly 2048 words, found ${it.size}" } }
    }

    override fun generateMnemonic(wordCount: Int): List<String> {
        require(wordCount == 12 || wordCount == 24) { "Word count must be 12 or 24" }

        val entropyBytes = if (wordCount == 12) 16 else 32 // 128 or 256 bits
        val entropy = ByteArray(entropyBytes)
        SecureRandom().nextBytes(entropy)

        return entropyToMnemonic(entropy)
    }

    override fun deriveSeed(
        mnemonic: List<String>,
        passphrase: String,
    ): ByteArray {
        val mnemonicString = mnemonic.joinToString(" ")
        val salt = "mnemonic$passphrase"

        val spec =
            PBEKeySpec(
                mnemonicString.toCharArray(),
                salt.toByteArray(Charsets.UTF_8),
                2048,
                512,
            )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
    }

    /**
     * Converts raw entropy bytes into a BIP39 mnemonic word list.
     */
    internal fun entropyToMnemonic(entropy: ByteArray): List<String> {
        require(entropy.size == 16 || entropy.size == 32) {
            "Entropy must be 16 bytes (128-bit) or 32 bytes (256-bit)"
        }

        // Step 1: Compute SHA-256 checksum
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = entropy.size * 8 / 32 // 4 bits for 128-bit, 8 bits for 256-bit

        // Step 2: Build a bit array: entropy bits + checksum bits
        val totalBits = entropy.size * 8 + checksumBits
        val bits = BooleanArray(totalBits)

        // Fill entropy bits (MSB first)
        for (byteIndex in entropy.indices) {
            for (bitIndex in 0..7) {
                bits[byteIndex * 8 + bitIndex] = (entropy[byteIndex].toInt() ushr (7 - bitIndex) and 1) == 1
            }
        }

        // Fill checksum bits from the first byte of the SHA-256 hash (MSB first)
        for (i in 0 until checksumBits) {
            bits[entropy.size * 8 + i] = (hash[0].toInt() ushr (7 - i) and 1) == 1
        }

        // Step 3: Group into 11-bit chunks and map to words
        val wordCount = totalBits / 11
        return (0 until wordCount).map { i ->
            var index = 0
            for (j in 0..10) {
                index = (index shl 1) or (if (bits[i * 11 + j]) 1 else 0)
            }
            wordList[index] // index is always in [0, 2047] for a 2048-word list
        }
    }
}
