package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req

/** Mutable text owner shared by RP, consent and candidate metadata; borrowed views reflect terminal erasure. */
class SensitiveText private constructor(
    private val characters: CharArray,
) : CharSequence {
    /** Number of UTF-16 units in this owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override val length: Int get() = characters.size

    /** Reads a borrowed code unit without creating text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun get(index: Int): Char = characters[index]

    /** Returns a borrowed view; the caller must not outlive the owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence {
        require(startIndex in 0..endIndex && endIndex <= length)
        return View(this, startIndex, endIndex - startIndex)
    }

    /** Returns a separately owned character array; its caller must erase it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyCharacters(): CharArray = characters.copyOf()

    /** Returns an independent mutable owner with the same value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyOwner(): SensitiveText = takeOwnership(characters.copyOf())

    /** Erases all owned characters; borrowed views immediately reflect erasure. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun erase() = characters.fill('\u0000')

    /** Materializes only the minimum text required by a compatibility or audited rendering caller. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toBoundaryString(): String = characters.concatToString()

    /** Compares borrowed text without an immutable intermediate. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun contentEquals(
        other: CharSequence,
        ignoreCase: Boolean = false,
    ): Boolean = length == other.length && characters.indices.all { characters[it].equals(other[it], ignoreCase) }

    /** Keeps diagnostic conversion free of RP/account data. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "[sensitive text]"

    /** Compares owned text by content. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean = other is SensitiveText && contentEquals(other)

    /** Hashes current characters without a String. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int = characters.contentHashCode()

    companion object {
        /** Copies borrowed text into a new owner; the source remains caller-owned. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun copyOf(value: CharSequence): SensitiveText {
            val characters = CharArray(value.length)
            copyCharacters(value, characters)
            return takeOwnership(characters)
        }

        /** Copies borrowed text into an owned destination and erases it if the copy does not complete. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        internal fun copyCharacters(
            value: CharSequence,
            destination: CharArray,
        ) {
            var copied = false
            try {
                require(destination.size == value.length)
                for (index in destination.indices) destination[index] = value[index]
                copied = true
            } finally {
                if (!copied) destination.fill('\u0000')
            }
        }

        /** Consumes an already allocated buffer, including its terminal erasure obligation. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun takeOwnership(value: CharArray): SensitiveText {
            var transferred = false
            try {
                return SensitiveText(value).also { transferred = true }
            } finally {
                if (!transferred) value.fill('\u0000')
            }
        }
    }

    private class View(
        private val owner: SensitiveText,
        private val start: Int,
        override val length: Int,
    ) : CharSequence {
        /** Reads one code unit through the live owner. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun get(index: Int): Char {
            require(index in 0 until length)
            return owner[start + index]
        }

        /** Returns another view into the same owner. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence {
            require(startIndex in 0..endIndex && endIndex <= length)
            return View(owner, start + startIndex, endIndex - startIndex)
        }

        /** Redacts diagnostics for borrowed views. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun toString(): String = "[sensitive text view]"
    }
}
