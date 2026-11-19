package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Req("NFR-SEC-041", "TR-AUTH-031")
class SensitiveCredentialTextTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `erase overwrites owned characters and leaves independent copy intact`() {
        val owner = SensitiveCredentialText.copyOf("synthetic-user")
        val copy = owner.copyOwner()
        owner.erase()

        assertEquals("\u0000".repeat("synthetic-user".length), owner.toBoundaryString())
        assertEquals("synthetic-user", copy.toBoundaryString())
        assertEquals("[sensitive credential text]", owner.toString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `subsequence borrows owner and reflects erasure without creating plaintext string`() {
        val owner = SensitiveCredentialText.copyOf("synthetic-name")
        val view = owner.subSequence(10, 14)

        assertEquals("name", String(CharArray(view.length) { view[it] }))
        owner.erase()

        assertEquals("\u0000\u0000\u0000\u0000", String(CharArray(view.length) { view[it] }))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `text comparison supports value equality without exposing its string representation`() {
        val owner = SensitiveCredentialText.copyOf("Synthetic-RP")

        assertTrue(owner.contentEquals("Synthetic-RP"))
        assertFalse(owner.contentEquals("synthetic-rp"))
        assertTrue(owner.contentEquals("synthetic-rp", ignoreCase = true))
        assertTrue("Synthetic-RP" !in owner.toString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `owner does not expose an unused immutable UserId conversion`() {
        assertFalse(SensitiveCredentialText::class.java.declaredMethods.any { it.name.startsWith("toUserId") })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed borrowed text conversion erases partially written destination`() {
        val destination = CharArray(12) { 'x' }
        val source =
            object : CharSequence {
                override val length: Int = destination.size

                override fun get(index: Int): Char {
                    if (index == 5) error("synthetic conversion failure")
                    return "marker-value"[index]
                }

                override fun subSequence(
                    startIndex: Int,
                    endIndex: Int,
                ): CharSequence = throw UnsupportedOperationException("not used by conversion")
            }

        assertFailsWith<IllegalStateException> {
            SensitiveCredentialText.copyCharacters(source, destination)
        }

        assertContentEquals(CharArray(destination.size), destination)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed borrowed text conversion erases destination when source length changes`() {
        val source =
            object : CharSequence {
                private var lengthReads = 0

                override val length: Int
                    get() = if (lengthReads++ == 0) 4 else 3

                override fun get(index: Int): Char = "mark"[index]

                override fun subSequence(
                    startIndex: Int,
                    endIndex: Int,
                ): CharSequence = throw UnsupportedOperationException("not used by conversion")
            }
        val destination = CharArray(source.length) { 'x' }

        assertFailsWith<IllegalArgumentException> {
            SensitiveCredentialText.copyCharacters(source, destination)
        }

        assertContentEquals(CharArray(destination.size), destination)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed owner wrapper construction erases staged decoded characters`() {
        val staged = "synthetic-marker".toCharArray()

        assertFailsWith<IllegalStateException> {
            SensitiveCredentialText.fromOwnedCharacters(staged) {
                error("synthetic owner-wrapper construction failure")
            }
        }

        assertContentEquals(CharArray(staged.size), staged)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed UTF-8 result transfer erases its partially written byte array`() {
        var staged: ByteArray? = null
        val owner = SensitiveCredentialText.copyOf("synthetic-marker")
        owner.beforeUtf8OutputTransferForTest = { output ->
            staged = output
            output.fill(0x53.toByte())
            error("synthetic UTF-8 output transfer failure")
        }

        assertFailsWith<IllegalStateException> { owner.toUtf8Bytes() }

        assertContentEquals(ByteArray(requireNotNull(staged).size), staged)
        owner.erase()
    }
}
