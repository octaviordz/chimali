package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Mutable owner for sensitive credential identity and account text held by a domain credential. */
class SensitiveCredentialText private constructor(
    private val characters: CharArray,
) : CharSequence {
    /** Returns the current owner length without copying its characters. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override val length: Int get() = characters.size

    /** Reads one UTF-16 code unit from the current owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun get(index: Int): Char = characters[index]

    /** Returns a non-owning view that reflects later owner erasure. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence {
        require(startIndex >= 0 && endIndex >= startIndex && endIndex <= characters.size)
        return View(this, startIndex, endIndex - startIndex)
    }

    /** Returns an independently erasable character copy for an operation boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyCharacters(): CharArray = characters.copyOf()

    /** Compares to borrowed text without creating an immutable copy. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun contentEquals(
        other: CharSequence,
        ignoreCase: Boolean = false,
    ): Boolean =
        length == other.length &&
            characters.indices.all { index ->
                if (ignoreCase) {
                    characters[index].equals(other[index], ignoreCase = true)
                } else {
                    characters[index] == other[index]
                }
            }

    /** Searches borrowed query text without materializing the complete sensitive field as a String. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun containsIgnoreCase(query: CharSequence): Boolean {
        if (query.isEmpty()) return true
        if (query.length > length) return false
        for (start in 0..(length - query.length)) {
            if (query.indices.all { offset -> characters[start + offset].equals(query[offset], ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    /** Creates text for a documented platform or protocol API that requires String. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toBoundaryString(): String = String(characters)

    /** Encodes this owner's characters into a caller-owned byte array. The caller must erase it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toUtf8Bytes(): ByteArray {
        val output = ByteBuffer.allocate(Math.multiplyExact(length, MAX_UTF8_BYTES_PER_CHARACTER))
        try {
            val encoder =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val encoded = encoder.encode(CharBuffer.wrap(characters), output, true)
            if (encoded.isError) encoded.throwException()
            val flushed = encoder.flush(output)
            if (flushed.isError) flushed.throwException()
            output.flip()
            return ByteArray(output.remaining()).also(output::get)
        } finally {
            output.array().fill(0)
        }
    }

    /** Creates a short-lived value object for an API that still requires one. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toRpId(): RpId = RpId(toBoundaryString())

    /** Creates a short-lived value object for an API that still requires one. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun toUserId(): UserId = UserId(toBoundaryString())

    /** Erases this owner's complete storage. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun erase() {
        characters.fill('\u0000')
    }

    /** Creates an independent owner for a copied domain value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyOwner(): SensitiveCredentialText = SensitiveCredentialText(characters.copyOf())

    /** Keeps diagnostic conversion redacted. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "[sensitive credential text]"

    /** Compares character contents without materializing text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean = other is SensitiveCredentialText && contentEquals(other)

    /** Hashes the current character contents without materializing text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int = characters.contentHashCode()

    companion object {
        private const val MAX_UTF8_BYTES_PER_CHARACTER = 4

        /** Decodes a database-owned UTF-8 byte buffer into a mutable owner without a String. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromUtf8Bytes(bytes: ByteArray): SensitiveCredentialText {
            val output = CharBuffer.allocate(bytes.size)
            return try {
                val decoder =
                    StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                val decoded = decoder.decode(ByteBuffer.wrap(bytes), output, true)
                if (decoded.isError) decoded.throwException()
                val flushed = decoder.flush(output)
                if (flushed.isError) flushed.throwException()
                SensitiveCredentialText(output.array().copyOf(output.position()))
            } finally {
                output.array().fill('\u0000')
            }
        }

        /** Decodes one borrowed UTF-8 range into an owned mutable character buffer. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromUtf8Range(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): SensitiveCredentialText {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            val output = CharBuffer.allocate(length)
            return try {
                val decoder =
                    StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                val decoded = decoder.decode(ByteBuffer.wrap(bytes, offset, length), output, true)
                if (decoded.isError) decoded.throwException()
                val flushed = decoder.flush(output)
                if (flushed.isError) flushed.throwException()
                SensitiveCredentialText(output.array().copyOf(output.position()))
            } finally {
                output.array().fill('\u0000')
            }
        }

        /** Copies borrowed input into mutable domain-owned storage. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun copyOf(value: CharSequence): SensitiveCredentialText {
            val characters = CharArray(value.length)
            for (index in characters.indices) characters[index] = value[index]
            return SensitiveCredentialText(characters)
        }

        /** Encodes borrowed ID bytes as the existing unpadded Base64URL UserId representation. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromBase64UrlBytes(bytes: ByteArray): SensitiveCredentialText {
            val encoded = Base64.getUrlEncoder().withoutPadding().encode(bytes)
            return try {
                SensitiveCredentialText(CharArray(encoded.size) { encoded[it].toInt().toChar() })
            } finally {
                encoded.fill(0)
            }
        }
    }

    private class View(
        private val owner: SensitiveCredentialText,
        private val offset: Int,
        /** Length of this borrowed view. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override val length: Int,
    ) : CharSequence {
        /** Reads one code unit from the borrowed owner. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun get(index: Int): Char {
            require(index in 0 until length)
            return owner[offset + index]
        }

        /** Creates another non-owning view into the same owner. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence {
            require(startIndex >= 0 && endIndex >= startIndex && endIndex <= length)
            return View(owner, offset + startIndex, endIndex - startIndex)
        }

        /** Returns only a fixed redacted diagnostic value. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun toString(): String = "[sensitive credential text view]"
    }
}
