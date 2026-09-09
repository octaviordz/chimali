package com.chimali.feature.vault.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.CharBuffer

/** One editor owns this field. Snapshot versions share only arrays which replacement/disposal wipe. */
internal class MutableDraftField(
    initial: CharArray?,
) : CharSequence {
    private var chars by mutableStateOf(initial?.copyOf() ?: charArrayOf())
    var isClosed = false
        private set

    override val length: Int get() = chars.size

    override fun get(index: Int): Char = chars[index]

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = CharBuffer.wrap(chars, startIndex, endIndex - startIndex)

    fun replace(value: CharSequence) {
        if (isClosed) return
        val next = CharArray(value.length)
        // Copying CharSequence may fail after allocation, and every failure must erase `next`.
        try {
            for (index in next.indices) next[index] = value[index]
            val retired = chars
            chars = next
            retired.fill('\u0000')
        } catch (
            @Suppress("TooGenericExceptionCaught")
            failure: Throwable,
        ) {
            next.fill('\u0000')
            throw failure
        }
    }

    fun copyChars(): CharArray = chars.copyOf()

    /** Borrowed until the next replacement or cleanup; callers must not retain as an independent owner. */
    internal fun borrowChars(): CharArray = chars

    // T055/T059 unresolved: current framework text widgets retain their own immutable display copies.
    fun displayText(): String = String(chars)

    fun clear() {
        if (isClosed) return
        isClosed = true
        chars.fill('\u0000')
        chars = charArrayOf()
    }

    override fun toString(): String = "MutableDraftField([redacted])"
}
