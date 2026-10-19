package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.vault.SensitiveCharArraySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Mutable text owner shared by RP, consent and candidate metadata; borrowed views reflect terminal erasure. */
@Serializable(with = SensitiveTextSerializer::class)
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
    fun copyOwner(): SensitiveText = SensitiveText(characters.copyOf())

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
        fun copyOf(value: CharSequence): SensitiveText = SensitiveText(CharArray(value.length) { value[it] })

        /** Consumes an already allocated buffer, including its terminal erasure obligation. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun takeOwnership(value: CharArray): SensitiveText = SensitiveText(value)
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

/** Generic model compatibility only; production FIDO2 persistence uses mutable byte/JSON adapters instead. */
object SensitiveTextSerializer : KSerializer<SensitiveText> {
    /** Preserves the model's historical JSON string shape. */
    override val descriptor: SerialDescriptor = SensitiveCharArraySerializer.descriptor

    /** Borrows the owner, erasing the compatibility serializer's temporary array on every exit. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun serialize(
        encoder: Encoder,
        value: SensitiveText,
    ) {
        val characters = value.copyCharacters()
        try {
            SensitiveCharArraySerializer.serialize(encoder, characters)
        } finally {
            characters.fill('\u0000')
        }
    }

    /** Transfers the decoded mutable characters to a new owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun deserialize(decoder: Decoder): SensitiveText =
        SensitiveText.takeOwnership(SensitiveCharArraySerializer.deserialize(decoder))
}
