package com.chimali.core.security.memory

import com.chimali.core.common.annotation.Req

/**
 * Operation-owned characters for normalization and serialization. Growth erases replaced arrays.
 * Callers must invoke [erase] in finally. Views borrow this owner and never create immutable text.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class ErasableCharBuffer(
    initialCapacity: Int = DEFAULT_CAPACITY,
) : Appendable,
    CharSequence {
    private var characters = CharArray(initialCapacity.also { require(it >= 0) })
    private var size = 0

    /** Number of initialized characters; does not expose the backing storage. */
    override val length: Int get() = size

    /** Returns a borrowed character. Throws if the position is outside the live content. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun get(index: Int): Char {
        require(index in 0 until size)
        return characters[index]
    }

    /** Appends a borrowed character, erasing any replaced buffer. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun append(value: Char): ErasableCharBuffer {
        ensureCapacity(1)
        characters[size++] = value
        return this
    }

    /** Appends borrowed characters; null input is rejected rather than serialized as private text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun append(value: CharSequence?): ErasableCharBuffer {
        val source = requireNotNull(value)
        return append(source, 0, source.length)
    }

    /** Appends the requested borrowed range and rejects invalid bounds. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun append(
        value: CharSequence?,
        startIndex: Int,
        endIndex: Int,
    ): ErasableCharBuffer {
        val source = requireNotNull(value)
        require(startIndex >= 0 && endIndex >= startIndex && endIndex <= source.length)
        ensureCapacity(endIndex - startIndex)
        for (index in startIndex until endIndex) characters[size++] = source[index]
        return this
    }

    /** Returns a borrowed view. Erasing/replacing this owner invalidates its contents. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence {
        require(startIndex >= 0 && endIndex >= startIndex && endIndex <= size)
        return View(this, startIndex, endIndex - startIndex)
    }

    /** Returns a separately owned copy; its recipient must erase it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyToOwnedChars(): CharArray = characters.copyOf(size)

    /** Erases all allocated characters, including unused capacity, and releases the live content. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun erase() {
        characters.fill('\u0000')
        size = 0
    }

    private fun ensureCapacity(additional: Int) {
        val required = Math.addExact(size, additional)
        if (required <= characters.size) return
        val capacity =
            maxOf(
                characters.size.toLong() * 2,
                required.toLong(),
            ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val previous = characters
        characters = previous.copyOf(capacity)
        previous.fill('\u0000')
    }

    private class View(
        private val owner: ErasableCharBuffer,
        private val offset: Int,
        override val length: Int,
    ) : CharSequence {
        override fun get(index: Int): Char {
            require(index in 0 until length)
            return owner[offset + index]
        }

        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence {
            require(startIndex >= 0 && endIndex >= startIndex && endIndex <= length)
            return View(owner, offset + startIndex, endIndex - startIndex)
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 32
    }
}
