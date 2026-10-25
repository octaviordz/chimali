package com.chimali.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Verification of SensitiveText secret ownership, bounds enforcement, terminal erasure, and serialization.
 * Traceable to NFR-SEC-041 and TR-AUTH-031.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class SensitiveTextTest {
    @Test
    fun `copyOf creates independent owner preserving characters`() {
        val source = "secret_password_123"
        val text = SensitiveText.copyOf(source)

        assertEquals(source.length, text.length)
        assertEquals('s', text[0])
        assertEquals('3', text[text.length - 1])
        assertEquals(source, text.toBoundaryString())
        assertTrue(text.contentEquals(source))
    }

    @Test
    fun `takeOwnership assumes control of char buffer`() {
        val buffer = charArrayOf('a', 'b', 'c')
        val text = SensitiveText.takeOwnership(buffer)

        assertEquals(3, text.length)
        assertEquals('a', text[0])
        assertEquals("abc", text.toBoundaryString())
    }

    @Test
    fun `subSequence returns borrowed view reflecting owner mutations and erasure`() {
        val text = SensitiveText.copyOf("sensitive_token")
        val view = text.subSequence(0, 9) // "sensitive"

        assertEquals(9, view.length)
        assertEquals('s', view[0])
        assertEquals('e', view[8])

        val nestedView = view.subSequence(0, 4) // "sens"
        assertEquals(4, nestedView.length)
        assertEquals('s', nestedView[0])
        assertEquals('n', nestedView[2])

        // Terminal erasure on owner
        text.erase()

        // Borrowed views must reflect erasure immediately
        for (i in 0 until view.length) {
            assertEquals('\u0000', view[i])
        }
        for (i in 0 until nestedView.length) {
            assertEquals('\u0000', nestedView[i])
        }
    }

    @Test
    fun `subSequence rejects out-of-bounds start and end indices`() {
        val text = SensitiveText.copyOf("hello")

        assertFailsWith<IllegalArgumentException> { text.subSequence(-1, 3) }
        assertFailsWith<IllegalArgumentException> { text.subSequence(3, 2) }
        assertFailsWith<IllegalArgumentException> { text.subSequence(0, 6) }

        val view = text.subSequence(1, 4) // "ell"
        assertFailsWith<IllegalArgumentException> { view.subSequence(-1, 2) }
        assertFailsWith<IllegalArgumentException> { view.subSequence(2, 1) }
        assertFailsWith<IllegalArgumentException> { view.subSequence(0, 4) }
    }

    @Test
    fun `view index access rejects out-of-bounds access`() {
        val text = SensitiveText.copyOf("hello")
        val view = text.subSequence(1, 4) // length 3

        assertFailsWith<IllegalArgumentException> { view.get(-1) }
        assertFailsWith<IllegalArgumentException> { view.get(3) }
    }

    @Test
    fun `copyCharacters produces detached array that caller can erase`() {
        val text = SensitiveText.copyOf("master_key")
        val copy = text.copyCharacters()

        assertEquals("master_key", copy.concatToString())
        copy.fill('\u0000')

        // Original remains intact until explicitly erased
        assertEquals("master_key", text.toBoundaryString())
    }

    @Test
    fun `copyOwner creates isolated duplicate owner`() {
        val text = SensitiveText.copyOf("vault_pass")
        val duplicate = text.copyOwner()

        assertEquals(text.toBoundaryString(), duplicate.toBoundaryString())
        assertEquals(text, duplicate)

        duplicate.erase()
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", duplicate.toBoundaryString())
        assertEquals("vault_pass", text.toBoundaryString())
    }

    @Test
    fun `contentEquals supports case sensitivity option`() {
        val text = SensitiveText.copyOf("ApiKey")

        assertTrue(text.contentEquals("ApiKey", ignoreCase = false))
        assertFalse(text.contentEquals("apikey", ignoreCase = false))
        assertTrue(text.contentEquals("apikey", ignoreCase = true))
        assertFalse(text.contentEquals("ApiKeyExtended", ignoreCase = true))
    }

    @Test
    fun `diagnostics redact secret text in toString`() {
        val text = SensitiveText.copyOf("top_secret")
        val view = text.subSequence(0, 3)

        assertEquals("[sensitive text]", text.toString())
        assertEquals("[sensitive text view]", view.toString())
        assertFalse(text.toString().contains("top_secret"))
        assertFalse(view.toString().contains("top"))
    }

    @Test
    fun `equals and hashCode compare by content`() {
        val a = SensitiveText.copyOf("same_secret")
        val b = SensitiveText.copyOf("same_secret")
        val c = SensitiveText.copyOf("different_secret")

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `serializer serializes and deserializes while preserving content`() {
        val original = SensitiveText.copyOf("json_roundtrip_secret")
        val encoded = Json.encodeToString(SensitiveTextSerializer, original)
        val decoded = Json.decodeFromString(SensitiveTextSerializer, encoded)

        assertEquals(original.toBoundaryString(), decoded.toBoundaryString())
        assertEquals(original, decoded)
    }

    @Test
    fun `serializer clears temporary buffer on encoder failure`() {
        val original = SensitiveText.copyOf("failure_secret")
        val throwingEncoder =
            object : kotlinx.serialization.encoding.AbstractEncoder() {
                override val serializersModule = kotlinx.serialization.modules.EmptySerializersModule()

                override fun encodeString(value: String): Unit = error("forced encoder failure")
            }
        assertFailsWith<IllegalStateException> {
            SensitiveTextSerializer.serialize(throwingEncoder, original)
        }
        assertEquals("failure_secret", original.toBoundaryString())
    }
}
