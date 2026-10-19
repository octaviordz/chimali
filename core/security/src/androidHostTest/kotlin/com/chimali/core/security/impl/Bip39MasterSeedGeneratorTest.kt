package com.chimali.core.security.impl

import com.chimali.core.security.memory.ErasableCharBuffer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test-first contract for the mutable, English BIP39 boundary used by the local HDK profile.
 * Test vector material is public and intentionally does not contain a production wallet secret.
 */
class Bip39MasterSeedGeneratorTest {
    private lateinit var generator: Bip39MasterSeedGenerator

    @BeforeTest
    fun setUp() {
        // Android ICU is exercised on device; this deterministic test seam covers the NFKD
        // compatibility mapping while keeping the host suite independent of Android stubs.
        generator =
            Bip39MasterSeedGenerator(
                MutableNfkdNormalizer { source, destination ->
                    source.forEach { character ->
                        destination.append(
                            if (character.code in FULLWIDTH_ASCII_START..FULLWIDTH_ASCII_END) {
                                (character.code - FULLWIDTH_ASCII_OFFSET).toChar()
                            } else {
                                character
                            },
                        )
                    }
                },
            )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `generated mnemonic is an owned mutable 24 word phrase`() {
        val first = generator.generateMnemonic()
        val second = generator.generateMnemonic()
        try {
            assertEquals(MNEMONIC_24_WORDS, first.count { it == ' ' } + 1)
            assertEquals(MNEMONIC_24_WORDS, second.count { it == ' ' } + 1)
            assertFalse(first.contentEquals(second), "independent entropy should produce independent phrases")

            val originalSecond = second.copyOf()
            first.fill('\u0000')
            assertContentEquals(originalSecond, second, "each returned phrase has a distinct mutable owner")
            originalSecond.fill('\u0000')
        } finally {
            first.fill('\u0000')
            second.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `derives full 64 byte BIP39 seed for the empty production passphrase`() {
        val mnemonic = ALL_ABANDON_24.toCharArray()
        val expectedSeed =
            (
                "408b285c123836004f4b8842c89324c1f01382450c0d439af345ba7fc49acf70" +
                    "5489c6fc77dbd4e3dc1dd8cc6bc9f043db8ada1e243c4a0eafb290d399480840"
            ).hexToBytes()

        try {
            val actual = generator.deriveSeed(mnemonic)
            try {
                assertEquals(BIP39_SEED_BYTES, actual.size)
                assertContentEquals(expectedSeed, actual)
            } finally {
                actual.fill(0)
            }
        } finally {
            mnemonic.fill('\u0000')
            expectedSeed.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `derivation borrows and preserves the caller mnemonic`() {
        val mnemonic = ALL_ABANDON_24.toCharArray()
        val borrowedCopy = mnemonic.copyOf()
        try {
            generator.deriveSeed(mnemonic).fill(0)
            assertContentEquals(borrowedCopy, mnemonic)
        } finally {
            mnemonic.fill('\u0000')
            borrowedCopy.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `normalization working phrase is cleared when normalization fails`() {
        var normalizedPhrase: ErasableCharBuffer? = null
        val failingGenerator =
            Bip39MasterSeedGenerator(
                MutableNfkdNormalizer { source, destination ->
                    normalizedPhrase = destination
                    source.forEach(destination::append)
                    error("synthetic normalizer failure")
                },
            )
        val mnemonic = ALL_ABANDON_24.toCharArray()
        val borrowedCopy = mnemonic.copyOf()

        try {
            assertFailsWith<IllegalStateException> { failingGenerator.deriveSeed(mnemonic) }
            assertEquals(0, assertNotNull(normalizedPhrase).length)
            assertContentEquals(borrowedCopy, mnemonic)
        } finally {
            mnemonic.fill('\u0000')
            borrowedCopy.fill('\u0000')
            normalizedPhrase?.erase()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `expanding normalization erases replaced and final arrays on failure`() {
        val backing = ErasableCharBuffer::class.java.getDeclaredField("characters").apply { isAccessible = true }
        val retained = mutableListOf<CharArray>()
        val generator =
            Bip39MasterSeedGenerator(
                MutableNfkdNormalizer { source, destination ->
                    source.forEach(destination::append)
                    retained.add(backing.get(destination) as CharArray)
                    repeat(4) { source.forEach(destination::append) }
                    retained.add(backing.get(destination) as CharArray)
                    error("synthetic failure after normalization expansion")
                },
            )
        val mnemonic = ALL_ABANDON_24.toCharArray()
        try {
            assertFailsWith<IllegalStateException> { generator.deriveSeed(mnemonic) }
            assertEquals(2, retained.size)
            assertTrue(retained[1].size > retained[0].size)
            retained.forEach { assertContentEquals(CharArray(it.size), it) }
            assertContentEquals(ALL_ABANDON_24.toCharArray(), mnemonic)
        } finally {
            mnemonic.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `NFKD normalizes compatibility characters before UTF-8 seed derivation`() {
        val compatibilityMnemonic =
            CharArray(ALL_ABANDON_24.length) { index ->
                val character = ALL_ABANDON_24[index]
                if (character == ' ') character else (character.code + FULLWIDTH_ASCII_OFFSET).toChar()
            }
        val canonicalMnemonic = ALL_ABANDON_24.toCharArray()
        val borrowedCopy = compatibilityMnemonic.copyOf()
        var expected: ByteArray? = null
        try {
            expected = generator.deriveSeed(canonicalMnemonic)
            val actual = generator.deriveSeed(compatibilityMnemonic)
            try {
                assertContentEquals(requireNotNull(expected), actual)
            } finally {
                actual.fill(0)
            }
            assertContentEquals(borrowedCopy, compatibilityMnemonic, "normalization must use owned working storage")
        } finally {
            expected?.fill(0)
            canonicalMnemonic.fill('\u0000')
            compatibilityMnemonic.fill('\u0000')
            borrowedCopy.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `rejects non 24 word mnemonic`() {
        val twelveWords = ALL_ABANDON_12.toCharArray()
        try {
            assertFailsWith<IllegalArgumentException> { generator.deriveSeed(twelveWords) }
        } finally {
            twelveWords.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `rejects unknown English words`() {
        val unknownWord = ALL_ABANDON_24.replaceFirst("abandon", "notaword").toCharArray()
        try {
            assertFailsWith<IllegalArgumentException> { generator.deriveSeed(unknownWord) }
        } finally {
            unknownWord.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `rejects a 24 word phrase with invalid checksum`() {
        val invalidChecksum = ALL_ABANDON_24.replace(" art", " abandon").toCharArray()
        val borrowedCopy = invalidChecksum.copyOf()
        try {
            assertFailsWith<IllegalArgumentException> { generator.deriveSeed(invalidChecksum) }
            assertContentEquals(borrowedCopy, invalidChecksum, "failed derivation must preserve borrowed input")
        } finally {
            invalidChecksum.fill('\u0000')
            borrowedCopy.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `rejects non canonical spacing without modifying borrowed mnemonic`() {
        val malformed = (" " + ALL_ABANDON_24 + "  ").toCharArray()
        val borrowedCopy = malformed.copyOf()
        try {
            assertFailsWith<IllegalArgumentException> { generator.deriveSeed(malformed) }
            assertContentEquals(borrowedCopy, malformed)
        } finally {
            malformed.fill('\u0000')
            borrowedCopy.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `derivation is deterministic and returned seeds have independent ownership`() {
        val mnemonic = ALL_ABANDON_24.toCharArray()
        try {
            val first = generator.deriveSeed(mnemonic)
            val second = generator.deriveSeed(mnemonic)
            try {
                assertContentEquals(first, second)
                assertEquals(BIP39_SEED_BYTES, first.size)
                first.fill(0)
                assertFalse(first.contentEquals(second), "clearing one result must not clear another result")
            } finally {
                first.fill(0)
                second.fill(0)
            }
        } finally {
            mnemonic.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `entropy conversion includes the BIP39 checksum for 256 bit entropy`() {
        val entropy = ByteArray(32)
        try {
            val mnemonic = generator.entropyToMnemonic(entropy)
            try {
                assertEquals(MNEMONIC_24_WORDS, mnemonic.count { it == ' ' } + 1)
                val expected = ALL_ABANDON_24.toCharArray()
                try {
                    assertContentEquals(expected, mnemonic)
                } finally {
                    expected.fill('\u0000')
                }
            } finally {
                mnemonic.fill('\u0000')
            }
        } finally {
            entropy.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `generation rejects every word count except the selected 24 word profile`() {
        assertFailsWith<IllegalArgumentException> { generator.generateMnemonic(12) }
        assertFailsWith<IllegalArgumentException> { generator.generateMnemonic(18) }
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val MNEMONIC_24_WORDS = 24
        const val BIP39_SEED_BYTES = 64
        const val FULLWIDTH_ASCII_START = 0xFF01
        const val FULLWIDTH_ASCII_END = 0xFF5E
        const val FULLWIDTH_ASCII_OFFSET = 0xFEE0
        const val ALL_ABANDON_12 =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val ALL_ABANDON_24 =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
    }
}
