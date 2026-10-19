package com.chimali.fido2.data.service

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.MetadataLookupTokenService
import com.chimali.core.security.memory.ErasableCharBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.koin.core.annotation.Single

/**
 * Orchestrates searchable metadata protection for the FIDO2 feature.
 *
 * This service wraps [MetadataLookupTokenService] and [EncryptedMetadataService]
 * to provide FIDO2-specific token generation and metadata payload encryption.
 */
@Single
class CredentialMetadataProtectionService(
    private val lookupTokenService: MetadataLookupTokenService,
    private val encryptedMetadataService: EncryptedMetadataService,
) {
    private var newCharacterBuffer: () -> ErasableCharBuffer = { ErasableCharBuffer() }

    /** Host-test seam for retaining operation buffers; production uses the audited mutable owner. */
    internal constructor(
        lookupTokenService: MetadataLookupTokenService,
        encryptedMetadataService: EncryptedMetadataService,
        bufferFactory: () -> ErasableCharBuffer,
    ) : this(lookupTokenService, encryptedMetadataService) {
        newCharacterBuffer = bufferFactory
    }

    /** Decrypted field owners transferred to the caller, which must invoke [erase] in finally. */
    class CredentialMetadata(
        val rpId: CharArray,
        val userId: CharArray,
    ) {
        /** Compares borrowed projection identifiers without making immutable decrypted copies. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun matches(
            rpId: CharSequence,
            userId: CharSequence,
        ): Boolean =
            this.rpId.size == rpId.length &&
                this.userId.size == userId.length &&
                this.rpId.indices.all { this.rpId[it] == rpId[it] } &&
                this.userId.indices.all { this.userId[it] == userId[it] }

        /** Erases every owned field; repeated cleanup is safe. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun erase() {
            rpId.fill('\u0000')
            userId.fill('\u0000')
        }
    }

    /** Serialize and encrypt credential projection metadata using erasable working buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encryptCredentialMetadata(
        rpId: CharSequence,
        userId: CharSequence,
    ): ByteArray {
        val json = newCharacterBuffer()
        var plaintext: ByteArray? = null
        var encodedBuffer: ByteBuffer? = null
        try {
            json.append("{\"rpId\":")
            appendJsonString(json, rpId)
            json.append(",\"userId\":")
            appendJsonString(json, userId)
            json.append('}')
            val encoded = ByteBuffer.allocate(Math.multiplyExact(json.length, MAX_UTF8_BYTES_PER_CHARACTER))
            encodedBuffer = encoded
            val encoder = StandardCharsets.UTF_8.newEncoder()
            requireConversionComplete(encoder.encode(CharBuffer.wrap(json), encoded, true))
            requireConversionComplete(encoder.flush(encoded))
            encoded.flip()
            val bytes = ByteArray(encoded.remaining())
            plaintext = bytes
            encoded.get(bytes)
            return encryptOwnedMetadata(bytes, rpId)
        } finally {
            json.erase()
            plaintext?.fill(0)
            encodedBuffer?.takeIf { it.hasArray() }?.array()?.fill(0)
        }
    }

    /** Decrypt, parse and erase the complete metadata payload within this call. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun decryptCredentialMetadata(
        encryptedBlob: ByteArray,
        rpId: CharSequence,
    ): CredentialMetadata {
        val aad = encodeUtf8(rpId)
        val plaintext =
            try {
                encryptedMetadataService.decrypt(encryptedBlob, aad)
            } finally {
                aad.fill(0)
            }
        var chars: CharArray? = null
        return try {
            val ownedChars = CharArray(plaintext.size)
            chars = ownedChars
            val decoder =
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val output = CharBuffer.wrap(ownedChars)
            requireConversionComplete(decoder.decode(ByteBuffer.wrap(plaintext), output, true))
            requireConversionComplete(decoder.flush(output))
            parseCredentialMetadata(ownedChars, output.position())
        } finally {
            plaintext.fill(0)
            chars?.fill('\u0000')
        }
    }

    private fun encryptOwnedMetadata(
        plaintext: ByteArray,
        rpId: CharSequence,
    ): ByteArray {
        val aad = encodeUtf8(rpId)
        return try {
            encryptedMetadataService.encrypt(plaintext, aad)
        } finally {
            aad.fill(0)
        }
    }

    private fun requireConversionComplete(result: CoderResult) {
        if (result.isError) result.throwException()
        check(result.isUnderflow) { "Credential metadata conversion exceeded its owned buffer" }
    }

    private fun encodeUtf8(value: CharSequence): ByteArray {
        val output = ByteBuffer.allocate(Math.multiplyExact(value.length, MAX_UTF8_BYTES_PER_CHARACTER))
        try {
            val encoder =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            requireConversionComplete(encoder.encode(CharBuffer.wrap(value), output, true))
            requireConversionComplete(encoder.flush(output))
            output.flip()
            return ByteArray(output.remaining()).also(output::get)
        } finally {
            output.array().fill(0)
        }
    }

    private fun appendJsonString(
        target: ErasableCharBuffer,
        value: CharSequence,
    ) {
        target.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> target.append("\\\"")
                '\\' -> target.append("\\\\")
                '\b' -> target.append("\\b")
                '\u000C' -> target.append("\\f")
                '\n' -> target.append("\\n")
                '\r' -> target.append("\\r")
                '\t' -> target.append("\\t")
                else ->
                    if (char < ' ') {
                        target.append("\\u")
                        for (shift in (UNICODE_DIGITS - 1) downTo 0) {
                            target.append(HEX[(char.code ushr (shift * BITS_PER_HEX_DIGIT)) and HEX_DIGIT_MASK])
                        }
                    } else {
                        target.append(char)
                    }
            }
        }
        target.append('"')
    }

    private fun parseCredentialMetadata(
        input: CharArray,
        length: Int,
    ): CredentialMetadata = MetadataParser(input, length, newCharacterBuffer).parse()

    private class MetadataParser(
        private val input: CharArray,
        private val length: Int,
        private val newCharacterBuffer: () -> ErasableCharBuffer,
    ) {
        private var index = 0

        fun parse(): CredentialMetadata {
            var rpId: CharArray? = null
            var userId: CharArray? = null
            try {
                expect('{')
                while (true) {
                    val key = readString()
                    try {
                        expect(':')
                        when {
                            key.contentEquals(RP_ID_FIELD) -> {
                                require(rpId == null) { "Duplicate credential metadata field" }
                                rpId = readString()
                            }
                            key.contentEquals(USER_ID_FIELD) -> {
                                require(userId == null) { "Duplicate credential metadata field" }
                                userId = readString()
                            }
                            else -> throw IllegalArgumentException("Unexpected credential metadata field")
                        }
                    } finally {
                        key.fill('\u0000')
                    }
                    skipWhitespace()
                    if (index >= length || input[index] != ',') break
                    index++
                }
                expect('}')
                skipWhitespace()
                require(index == length) { "Trailing credential metadata" }
                val result = CredentialMetadata(requireNotNull(rpId), requireNotNull(userId))
                rpId = null
                userId = null
                return result
            } finally {
                rpId?.fill('\u0000')
                userId?.fill('\u0000')
            }
        }

        private fun readString(): CharArray {
            expect('"')
            val value = newCharacterBuffer()
            try {
                while (index < length) {
                    val character = input[index++]
                    when {
                        character == '"' -> return value.copyToOwnedChars()
                        character == '\\' -> value.append(readEscape())
                        character < ' ' -> throw IllegalArgumentException("Unescaped credential metadata control")
                        else -> value.append(character)
                    }
                }
                throw IllegalArgumentException("Unterminated credential metadata string")
            } finally {
                value.erase()
            }
        }

        private fun readEscape(): Char {
            require(index < length) { "Invalid credential metadata escape" }
            return when (val escaped = input[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(length - index >= UNICODE_DIGITS) { "Invalid credential metadata unicode escape" }
                    var code = 0
                    repeat(UNICODE_DIGITS) {
                        val digit = input[index++].digitToIntOrNull(HEX_RADIX)
                        require(digit != null) { "Invalid credential metadata unicode escape" }
                        code = code * HEX_RADIX + digit
                    }
                    code.toChar()
                }
                else -> throw IllegalArgumentException("Invalid credential metadata escape")
            }
        }

        private fun skipWhitespace() {
            while (index < length && input[index] in JSON_WHITESPACE) index++
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            require(index < length && input[index] == expected) { "Invalid credential metadata" }
            index++
        }
    }

    private companion object {
        const val HEX = "0123456789abcdef"
        const val HEX_RADIX = 16
        const val HEX_DIGIT_MASK = 0xF
        const val BITS_PER_HEX_DIGIT = 4
        const val MAX_UTF8_BYTES_PER_CHARACTER = 3
        const val UNICODE_DIGITS = 4
        const val JSON_WHITESPACE = " \t\n\r"
        val RP_ID_FIELD = "rpId".toCharArray()
        val USER_ID_FIELD = "userId".toCharArray()
    }

    /**
     * Generates a deterministic exact-match index token for an RP ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRpIdIndex(rpId: CharSequence): ByteArray =
        lookupTokenService.generateToken(MetadataLookupTokenService.DOMAIN_RP_ID, rpId)

    /**
     * Generates a deterministic exact-match index token for a User ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getUserIdIndex(userId: CharSequence): ByteArray =
        lookupTokenService.generateToken(MetadataLookupTokenService.DOMAIN_USER_ID, userId)

    /** Encrypts a relying-party projection without constructing immutable plaintext JSON. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encryptRelyingPartyMetadata(
        rpId: CharSequence,
        name: CharSequence? = null,
    ): ByteArray {
        val json = newCharacterBuffer()
        var plaintext: ByteArray? = null
        var encodedBuffer: ByteBuffer? = null
        try {
            json.append("{\"id\":")
            appendJsonString(json, rpId)
            if (name != null) {
                json.append(",\"name\":")
                appendJsonString(json, name)
            }
            json.append('}')
            val encoded = ByteBuffer.allocate(Math.multiplyExact(json.length, MAX_UTF8_BYTES_PER_CHARACTER))
            encodedBuffer = encoded
            val encoder = StandardCharsets.UTF_8.newEncoder()
            requireConversionComplete(encoder.encode(CharBuffer.wrap(json), encoded, true))
            requireConversionComplete(encoder.flush(encoded))
            encoded.flip()
            val bytes = ByteArray(encoded.remaining())
            plaintext = bytes
            encoded.get(bytes)
            return encryptedMetadataService.encrypt(bytes, null)
        } finally {
            json.erase()
            plaintext?.fill(0)
            encodedBuffer?.takeIf { it.hasArray() }?.array()?.fill(0)
        }
    }
}
