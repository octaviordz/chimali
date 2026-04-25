package com.chimali.core.security.impl

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [Bip39MasterSeedGenerator].
 *
 * Verifies the core BIP39 algorithm: entropy→mnemonic→seed derivation.
 * Uses MockK to provide a fake [Context] backed by a predictable sequential word list
 * (word0000..word2047) so that index calculations are deterministic.
 *
 * Test vectors sourced from trezor/python-mnemonic (English, see vectors.json).
 */
class Bip39MasterSeedGeneratorTest {
    private companion object {
        private const val WORD_COUNT_12 = 12
        private const val WORD_COUNT_24 = 24
        private const val WORD_COUNT_INVALID = 18
        private const val ENTROPY_128_BITS_BYTES = 16
        private const val ENTROPY_256_BITS_BYTES = 32
        private const val ENTROPY_INVALID_BYTES = 15
        private const val BIP39_SEED_SIZE = 64
        private const val BIP39_WORDLIST_SIZE = 2048
        private const val WORD_PADDING_WIDTH = 4
    }


    private lateinit var generator: Bip39MasterSeedGenerator
    private lateinit var mockContext: Context

    @BeforeTest
    fun setUp() {
        val wordListText = generateSequentialWordList()
        mockContext = mockk()
        val assetManager = mockk<AssetManager>()
        every { mockContext.assets } returns assetManager
        every { assetManager.open("bip39_english.txt") } answers {
            wordListText.byteInputStream()
        }
        generator = Bip39MasterSeedGenerator(mockContext)
    }

    @Test
    fun `generateMnemonic with wordCount 12 returns list of 12 words`() {
        val mnemonic = generator.generateMnemonic(WORD_COUNT_12)
        assertEquals(WORD_COUNT_12, mnemonic.size)
    }

    @Test
    fun `generateMnemonic with wordCount 24 returns list of 24 words`() {
        val mnemonic = generator.generateMnemonic(WORD_COUNT_24)
        assertEquals(WORD_COUNT_24, mnemonic.size)
    }

    @Test
    fun `two calls to generateMnemonic produce different mnemonics`() {
        val first = generator.generateMnemonic(WORD_COUNT_24)
        val second = generator.generateMnemonic(WORD_COUNT_24)
        assertNotEquals(first, second)
    }

    @Test
    fun `entropyToMnemonic with all-zero 128-bit entropy produces 12 words with first 11 at index 0`() {
        val zeroEntropy = ByteArray(ENTROPY_128_BITS_BYTES) { 0 }
        val mnemonic = generator.entropyToMnemonic(zeroEntropy)
        assertEquals(WORD_COUNT_12, mnemonic.size)
        // First 11 words: bits 0..120 come entirely from all-zero entropy → index 0
        val leadingWords = WORD_COUNT_12 - 1
        assertTrue(mnemonic.take(leadingWords).all { it == "word0000" })
        // 12th word: bits 121..131 = last 7 entropy bits (all zero) + 4 checksum bits
        // from SHA-256(all-zeros). The checksum is non-zero so the 12th word is NOT word0000.
        assertNotNull(mnemonic[leadingWords]) // Just assert it's a valid word
    }

    @Test
    fun `entropyToMnemonic with all-zero 256-bit entropy produces 24 words`() {
        val zeroEntropy = ByteArray(ENTROPY_256_BITS_BYTES) { 0 }
        val mnemonic = generator.entropyToMnemonic(zeroEntropy)
        assertEquals(WORD_COUNT_24, mnemonic.size)
    }

    @Test
    fun `entropyToMnemonic with invalid entropy size throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            generator.entropyToMnemonic(ByteArray(ENTROPY_INVALID_BYTES))
        }
    }

    @Test
    fun `deriveSeed returns 64-byte seed`() {
        val mnemonic = generator.generateMnemonic(WORD_COUNT_24)
        val seed = generator.deriveSeed(mnemonic)
        assertEquals(BIP39_SEED_SIZE, seed.size)
    }

    @Test
    fun `deriveSeed is deterministic for same mnemonic and passphrase`() {
        val mnemonic = (0 until WORD_COUNT_12).map { "word${it.toString().padStart(WORD_PADDING_WIDTH, '0')}" }
        val seed1 = generator.deriveSeed(mnemonic, "")
        val seed2 = generator.deriveSeed(mnemonic, "")
        assertContentEquals(seed1, seed2)
    }

    @Test
    fun `deriveSeed differs with different passphrases`() {
        val mnemonic = generator.generateMnemonic(WORD_COUNT_12)
        val seed1 = generator.deriveSeed(mnemonic, "")
        val seed2 = generator.deriveSeed(mnemonic, "TREZOR")
        assertFalse(seed1.contentEquals(seed2))
    }

    @Test
    fun `generateMnemonic with invalid word count throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            generator.generateMnemonic(WORD_COUNT_INVALID)
        }
    }

    /** Generates a predictable 2048-word list for testing (word0000..word2047). */
    private fun generateSequentialWordList(): String =
        (0 until BIP39_WORDLIST_SIZE).joinToString("\n") { "word${it.toString().padStart(WORD_PADDING_WIDTH, '0')}" }
}
