package com.chimali.core.security.memory

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies application-controlled backing storage rather than a cleanup-state flag. */
class ErasableCharBufferTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `growth erases replaced arrays and final erasure includes unused capacity`() {
        val owner = ErasableCharBuffer(2)
        val field = ErasableCharBuffer::class.java.getDeclaredField("characters").apply { isAccessible = true }
        owner.append("ab")
        val first = field.get(owner) as CharArray
        owner.append("cdef")
        val second = field.get(owner) as CharArray
        owner.append("ghijklmnop")
        val last = field.get(owner) as CharArray
        assertContentEquals(CharArray(first.size), first)
        assertContentEquals(CharArray(second.size), second)
        val copy = owner.copyToOwnedChars()
        assertContentEquals("abcdefghijklmnop".toCharArray(), copy)
        owner.erase()
        assertContentEquals(CharArray(last.size), last)
        assertEquals(0, owner.length)
        // Recipient copies are separately owned and are not erased by the producer.
        assertContentEquals("abcdefghijklmnop".toCharArray(), copy)
        copy.fill('\u0000')
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `range views borrow the owner and invalid ranges cannot mutate its content`() {
        val owner = ErasableCharBuffer(0)
        try {
            owner.append("prefix-value", 7, 12)
            val view = owner.subSequence(1, 4)
            assertEquals(3, view.length)
            assertEquals('a', view[0])
            assertFailsWith<IllegalArgumentException> { owner.append("bad", -1, 1) }
            assertFailsWith<IllegalArgumentException> { owner.subSequence(0, 6) }
            assertContentEquals("value".toCharArray(), owner.copyToOwnedChars())
            owner.erase()
            assertFailsWith<IllegalArgumentException> { view[0] }
        } finally {
            owner.erase()
        }
    }
}
