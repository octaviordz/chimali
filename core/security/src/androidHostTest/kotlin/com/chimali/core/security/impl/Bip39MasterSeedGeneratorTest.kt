package com.chimali.core.security.impl

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Unit tests for [Bip39MasterSeedGenerator].
 *
 * Verifies the core BIP39 algorithm: entropy→mnemonic→seed derivation.
 * Uses real BIP39 wordlist through KMP abstraction, enabling execution without Context/AssetManager mocking.
 * Test vectors sourced from official Trezor/BIP39 specification (vectors.json).
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
    }

    private lateinit var generator: Bip39MasterSeedGenerator

    @BeforeTest
    fun setUp() {
        generator = Bip39MasterSeedGenerator()
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
    fun `entropyToMnemonic with all-zero 128-bit entropy produces correct BIP39 test vector`() {
        val zeroEntropy = ByteArray(ENTROPY_128_BITS_BYTES) { 0 }
        val mnemonic = generator.entropyToMnemonic(zeroEntropy)
        assertEquals(WORD_COUNT_12, mnemonic.size)
        // From official BIP39 test vectors: all 0 entropy gives "abandon" 11 times, and "about" as checksum word.
        val expected = List(11) { "abandon" } + "about"
        assertContentEquals(expected, mnemonic)
    }

    @Test
    fun `entropyToMnemonic with all-zero 256-bit entropy produces correct BIP39 test vector`() {
        val zeroEntropy = ByteArray(ENTROPY_256_BITS_BYTES) { 0 }
        val mnemonic = generator.entropyToMnemonic(zeroEntropy)
        assertEquals(WORD_COUNT_24, mnemonic.size)
        // From official BIP39 test vectors: all 0 entropy gives "abandon" 23 times, and "art" as checksum word.
        val expected = List(23) { "abandon" } + "art"
        assertContentEquals(expected, mnemonic)
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
        val mnemonic = List(11) { "abandon" } + "about"
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
}
