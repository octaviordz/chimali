package com.chimali.core.security.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceLoaderTest {
    @Test
    fun `loadResourceLines returns exactly 2048 words for bip39_english`() {
        val lines = loadResourceLines("bip39_english.txt")

        assertEquals(2048, lines.size, "BIP39 wordlist must contain exactly 2048 words")
        assertTrue(lines.none { it.isBlank() }, "BIP39 wordlist must not contain blank entries")
        assertEquals("abandon", lines.first(), "First word must be 'abandon'")
        assertEquals("zoo", lines.last(), "Last word must be 'zoo'")
    }
}
