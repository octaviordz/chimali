package com.chimali.core.security.impl

import android.icu.text.Normalizer2
import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.memory.ErasableCharBuffer
import com.chimali.core.security.platform.loadResourceLines
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

/** Mutable normalization boundary so JVM host tests can supply deterministic normalization. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun interface MutableNfkdNormalizer {
    /**
     * Writes NFKD-normalized [source] characters into the caller-owned [destination].
     *
     * The source is borrowed and must remain valid for this call; the destination remains owned
     * by its caller, which must erase it after use, including when normalization fails.
     * Implementations may propagate normalization or destination-append failures.
     *
     * @param source borrowed text to normalize
     * @param destination caller-owned mutable output buffer
     * @return [Unit] after normalized text has been written
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun normalize(
        source: CharSequence,
        destination: ErasableCharBuffer,
    )
}

private object AndroidIcuNfkdNormalizer : MutableNfkdNormalizer {
    /**
     * Appends Android ICU's NFKD normalization of borrowed [source] into [destination].
     * The caller retains destination ownership and must erase it after use or on failure.
     * ICU normalization and destination-append failures propagate to the caller.
     *
     * @param source borrowed text to normalize
     * @param destination caller-owned mutable output buffer
     * @return [Unit] after normalized text has been written
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun normalize(
        source: CharSequence,
        destination: ErasableCharBuffer,
    ) {
        Normalizer2.getNFKDInstance().normalize(source, destination)
    }
}

/** BIP39 English generation and fixed empty-passphrase seed derivation using mutable owners. */
@Single
class Bip39MasterSeedGenerator() : MasterSeedGenerator {
    private var nfkdNormalizer: MutableNfkdNormalizer = AndroidIcuNfkdNormalizer

    /** Host-test seam; production construction always uses Android ICU. */
    internal constructor(nfkdNormalizer: MutableNfkdNormalizer) : this() {
        this.nfkdNormalizer = nfkdNormalizer
    }

    private val wordList: List<String> by lazy {
        loadResourceLines("bip39_english.txt")
            .also { require(it.size == WORD_LIST_SIZE) { "BIP39 wordlist must contain 2048 words" } }
    }

    /** Generates a mutable 24-word English BIP39 phrase and erases temporary entropy before return. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun generateMnemonic(wordCount: Int): CharArray {
        require(wordCount == PROFILE_WORD_COUNT) { "The wallet profile requires exactly 24 words" }
        val entropy = ByteArray(ENTROPY_LENGTH)
        return try {
            SecureRandom().nextBytes(entropy)
            entropyToMnemonic(entropy)
        } finally {
            entropy.fill(0)
        }
    }

    /** Validates a borrowed English mnemonic and returns its full 64-byte, empty-passphrase seed. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun deriveSeed(mnemonic: CharArray): ByteArray {
        val normalized = ErasableCharBuffer(mnemonic.size)
        var indices: IntArray? = null
        var entropy: ByteArray? = null
        var password: ByteArray? = null
        val salt = EMPTY_PASSPHRASE_SALT.copyOf()
        var output: ByteArray? = null
        try {
            nfkdNormalizer.normalize(CharBuffer.wrap(mnemonic), normalized)
            indices = parseAndValidateMnemonic(normalized)
            entropy = indicesToEntropy(indices)
            password = asciiPhraseBytes(normalized)

            val derived = pbkdf2HmacSha512(password, salt, PBKDF2_ITERATIONS)
            output = derived
            return derived.also { output = null }
        } finally {
            normalized.erase()
            indices?.fill(0)
            entropy?.fill(0)
            password?.fill(0)
            salt.fill(0)
            output?.fill(0)
        }
    }

    /** Converts borrowed 256-bit entropy to an owned 24-word phrase. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun entropyToMnemonic(entropy: ByteArray): CharArray {
        require(entropy.size == ENTROPY_LENGTH) { "The current wallet profile requires 256-bit entropy" }
        val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)
        val indices = IntArray(PROFILE_WORD_COUNT)
        try {
            for (word in indices.indices) {
                var value = 0
                for (bitInWord in 0 until BITS_PER_WORD) {
                    val sourceBit = word * BITS_PER_WORD + bitInWord
                    val bit =
                        if (sourceBit < ENTROPY_LENGTH * BITS_PER_BYTE) {
                            bitAt(entropy, sourceBit)
                        } else {
                            bitAt(checksum, sourceBit - ENTROPY_LENGTH * BITS_PER_BYTE)
                        }
                    value = (value shl 1) or bit
                }
                indices[word] = value
            }

            val words = wordList
            val length = indices.sumOf { words[it].length } + indices.lastIndex
            val phrase = CharArray(length)
            var offset = 0
            indices.forEachIndexed { wordIndex, wordIndexValue ->
                val word = words[wordIndexValue]
                word.forEach { character -> phrase[offset++] = character }
                if (wordIndex != indices.lastIndex) phrase[offset++] = ' '
            }
            return phrase
        } finally {
            checksum.fill(0)
            indices.fill(0)
        }
    }

    private fun parseAndValidateMnemonic(normalized: CharSequence): IntArray {
        require(normalized.isNotEmpty() && normalized.first() != ' ' && normalized.last() != ' ') {
            "Mnemonic must not have leading or trailing whitespace"
        }
        val indices = IntArray(PROFILE_WORD_COUNT)
        var wordNumber = 0
        var start = 0
        var completed = false
        try {
            for (position in 0..normalized.length) {
                if (position == normalized.length || normalized[position] == ' ') {
                    require(position > start) { "Mnemonic words must use single spaces" }
                    require(wordNumber < PROFILE_WORD_COUNT) { "Mnemonic must contain exactly 24 words" }
                    val found = findWordIndex(normalized, start, position)
                    require(found >= 0) { "Mnemonic contains a word outside the English BIP39 list" }
                    indices[wordNumber++] = found
                    start = position + 1
                }
            }
            require(wordNumber == PROFILE_WORD_COUNT) { "Mnemonic must contain exactly 24 words" }
            val entropy = indicesToEntropy(indices)
            try {
                val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)
                try {
                    for (bit in 0 until CHECKSUM_BITS) {
                        val concatenatedBit = ENTROPY_LENGTH * BITS_PER_BYTE + bit
                        val wordIndex = concatenatedBit / BITS_PER_WORD
                        val bitInWord = concatenatedBit % BITS_PER_WORD
                        val mnemonicBit = (indices[wordIndex] ushr (BITS_PER_WORD - bitInWord - 1)) and 1
                        require(mnemonicBit == bitAt(checksum, bit)) {
                            "Mnemonic checksum is invalid"
                        }
                    }
                } finally {
                    checksum.fill(0)
                }
            } finally {
                entropy.fill(0)
            }
            completed = true
            return indices
        } finally {
            if (!completed) indices.fill(0)
        }
    }

    private fun findWordIndex(
        phrase: CharSequence,
        start: Int,
        end: Int,
    ): Int {
        val words = wordList
        for (wordIndex in words.indices) {
            val candidate = words[wordIndex]
            if (candidate.length != end - start) continue
            var matches = true
            for (offset in candidate.indices) {
                if (phrase[start + offset] != candidate[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return wordIndex
        }
        return -1
    }

    private fun indicesToEntropy(indices: IntArray): ByteArray {
        require(indices.size == PROFILE_WORD_COUNT) { "The wallet profile requires 24 words" }
        val entropy = ByteArray(ENTROPY_LENGTH)
        for (word in indices.indices) {
            for (bitInWord in 0 until BITS_PER_WORD) {
                val value = (indices[word] ushr (BITS_PER_WORD - bitInWord - 1)) and 1
                val targetBit = word * BITS_PER_WORD + bitInWord
                if (targetBit < ENTROPY_LENGTH * BITS_PER_BYTE) setBit(entropy, targetBit, value)
            }
        }
        return entropy
    }

    private fun asciiPhraseBytes(phrase: CharSequence): ByteArray =
        ByteArray(phrase.length) { index ->
            val character = phrase[index]
            require(character.code in 0x00..0x7f) { "Validated English mnemonic must be ASCII" }
            character.code.toByte()
        }

    private fun pbkdf2HmacSha512(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(password, HMAC_SHA512))
        val block = ByteArray(salt.size + INT_LENGTH)
        salt.copyInto(block)
        block[block.lastIndex] = 1
        val output = ByteArray(SEED_LENGTH)
        var current: ByteArray? = null
        var completed = false
        try {
            val first = mac.doFinal(block)
            current = first
            first.copyInto(output)
            repeat(iterations - 1) {
                val previous = requireNotNull(current)
                val next = mac.doFinal(previous)
                previous.fill(0)
                current = next
                for (index in output.indices) output[index] = (output[index].toInt() xor next[index].toInt()).toByte()
            }
            completed = true
            return output
        } finally {
            if (!completed) output.fill(0)
            block.fill(0)
            current?.fill(0)
        }
    }

    private fun bitAt(
        bytes: ByteArray,
        bitIndex: Int,
    ): Int = (bytes[bitIndex / BITS_PER_BYTE].toInt() ushr (BITS_PER_BYTE - 1 - bitIndex % BITS_PER_BYTE)) and 1

    private fun setBit(
        bytes: ByteArray,
        bitIndex: Int,
        value: Int,
    ) {
        if (value != 0) {
            val byteIndex = bitIndex / BITS_PER_BYTE
            bytes[byteIndex] =
                (bytes[byteIndex].toInt() or (1 shl (BITS_PER_BYTE - 1 - bitIndex % BITS_PER_BYTE))).toByte()
        }
    }

    private companion object {
        const val WORD_LIST_SIZE = 2048
        const val PROFILE_WORD_COUNT = 24
        const val ENTROPY_LENGTH = 32
        const val SEED_LENGTH = 64
        const val BITS_PER_BYTE = 8
        const val BITS_PER_WORD = 11
        const val CHECKSUM_BITS = 8
        const val PBKDF2_ITERATIONS = 2048
        const val INT_LENGTH = 4
        const val HMAC_SHA512 = "HmacSHA512"
        val EMPTY_PASSPHRASE_SALT = byteArrayOf(0x6d, 0x6e, 0x65, 0x6d, 0x6f, 0x6e, 0x69, 0x63)
    }
}
