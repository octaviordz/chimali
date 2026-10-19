package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Mutable JSON bridge that keeps selected string values out of immutable serializer intermediates. */
internal object SensitiveJsonFields {
    private const val HEX = "0123456789abcdef"
    private const val MAX_ESCAPED_UTF8_BYTES_PER_CHARACTER = 6
    private const val JSON_DELIMITER_COUNT = 2
    private const val BYTE_MASK = 0xFF
    private const val UTF8_MAX_BYTES_PER_UTF16_UNIT = 4
    private const val HEX_NIBBLE_SHIFT = 4
    private const val HEX_NIBBLE_MASK = 0x0F
    private const val HEX_RADIX = 16
    private const val JSON_BACKSPACE = 0x08
    private const val JSON_TAB = 0x09
    private const val JSON_LINE_FEED = 0x0A
    private const val JSON_FORM_FEED = 0x0C
    private const val JSON_CARRIAGE_RETURN = 0x0D
    private const val JSON_CONTROL_LIMIT = 0x20
    private const val JSON_MIN_CONTROL_CODE = 0x00
    private const val UNICODE_ESCAPE_DIGITS = 4
    private const val MAX_JSON_DOCUMENT_BYTES = 1_048_576
    private const val MAX_JSON_NESTING_DEPTH = 64
    private const val MAX_JSON_STRING_CHARACTERS = 1_048_576
    private const val INITIAL_JSON_STRING_CAPACITY = 32
    private val JSON_WHITESPACE = charArrayOf(' ', '\t', '\r', '\n')
    private val JSON_PRIMITIVE_DELIMITERS = charArrayOf(',', '}', ']', ' ', '\t', '\r', '\n')

    /** Serializes a sanitized JSON object, replacing each empty selected string with mutable text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encode(
        fields: Map<String, CharArray>,
        serializeSanitized: (OutputStream) -> Unit,
    ): ByteArray {
        val sanitized = encodeToErasableJsonBytes(serializeSanitized)
        val output =
            ErasableJsonBuffer(
                sanitized.size +
                    fields.values.sumOf { it.size * MAX_ESCAPED_UTF8_BYTES_PER_CHARACTER + JSON_DELIMITER_COUNT },
            )
        return try {
            var cursor = 0
            fields.forEach { (name, value) ->
                val placeholder = "\"$name\":\"\"".encodeToByteArray()
                val match = sanitized.indexOf(placeholder, cursor)
                require(match >= cursor) { "Sensitive JSON field placeholder missing" }
                val prefixLength = placeholder.size - JSON_DELIMITER_COUNT
                output.write(sanitized, cursor, match + prefixLength - cursor)
                writeJsonStringContents(output, value)
                output.write(placeholder.last().toInt())
                cursor = match + placeholder.size
                placeholder.fill(0)
            }
            output.write(sanitized, cursor, sanitized.size - cursor)
            output.copyToOwnedBytes()
        } finally {
            sanitized.fill(0)
            output.erase()
        }
    }

    /** Decodes selected JSON string fields directly into mutable owners and erases its work buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun <T> decode(
        input: ByteArray,
        fieldNames: Set<String>,
        fieldObjectPath: List<String> = emptyList(),
        deserializeSanitized: (ByteArray) -> T,
        attachFields: (T, Map<String, CharArray>) -> T,
    ): T {
        require(input.size <= MAX_JSON_DOCUMENT_BYTES) { "JSON document exceeds the supported size" }
        val document = decodeUtf8(input)
        val redactions = mutableListOf<Redaction>()
        val fields = linkedMapOf<String, CharArray>()
        var sanitized: ByteArray? = null
        var transferred = false
        try {
            JsonFieldParser(document, fieldNames, fieldObjectPath, fields, redactions).parse()
            val sanitizedCharacters = redact(document, redactions)
            try {
                sanitized = encodeUtf8(sanitizedCharacters)
            } finally {
                sanitizedCharacters.fill('\u0000')
            }
            val decoded = deserializeSanitized(sanitized)
            val result = attachFields(decoded, fields)
            transferred = true
            return result
        } finally {
            document.fill('\u0000')
            sanitized?.fill(0)
            redactions.clear()
            if (!transferred) fields.values.forEach { it.fill('\u0000') }
            fields.clear()
        }
    }

    private fun writeJsonStringContents(
        output: OutputStream,
        characters: CharArray,
    ) {
        output.write('"'.code)
        val utf8 = encodeUtf8(characters)
        try {
            utf8.forEach { byte ->
                val value = byte.toInt() and BYTE_MASK
                when (value) {
                    '"'.code -> output.write("\\\"".encodeToByteArray())
                    '\\'.code -> output.write("\\\\".encodeToByteArray())
                    JSON_BACKSPACE -> output.write("\\b".encodeToByteArray())
                    JSON_TAB -> output.write("\\t".encodeToByteArray())
                    JSON_LINE_FEED -> output.write("\\n".encodeToByteArray())
                    JSON_FORM_FEED -> output.write("\\f".encodeToByteArray())
                    JSON_CARRIAGE_RETURN -> output.write("\\r".encodeToByteArray())
                    in JSON_MIN_CONTROL_CODE until JSON_CONTROL_LIMIT -> {
                        output.write("\\u00".encodeToByteArray())
                        output.write(HEX[value ushr HEX_NIBBLE_SHIFT].code)
                        output.write(HEX[value and HEX_NIBBLE_MASK].code)
                    }
                    else -> output.write(value)
                }
            }
        } finally {
            utf8.fill(0)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): CharArray {
        val output = CharBuffer.allocate(bytes.size)
        try {
            val decoder =
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val result = decoder.decode(ByteBuffer.wrap(bytes), output, true)
            if (result.isError) result.throwException()
            val flushed = decoder.flush(output)
            if (flushed.isError) flushed.throwException()
            return output.array().copyOf(output.position())
        } finally {
            output.array().fill('\u0000')
        }
    }

    private fun encodeUtf8(characters: CharArray): ByteArray {
        val output = ByteBuffer.allocate(Math.multiplyExact(characters.size, UTF8_MAX_BYTES_PER_UTF16_UNIT))
        try {
            val encoder =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val result = encoder.encode(CharBuffer.wrap(characters), output, true)
            if (result.isError) result.throwException()
            val flushed = encoder.flush(output)
            if (flushed.isError) flushed.throwException()
            output.flip()
            return ByteArray(output.remaining()).also(output::get)
        } finally {
            output.array().fill(0)
        }
    }

    private fun redact(
        document: CharArray,
        redactions: List<Redaction>,
    ): CharArray {
        val removedCharacters = redactions.sumOf { it.endExclusive - it.start - JSON_DELIMITER_COUNT }
        val output = CharArray(document.size - removedCharacters)
        var sourceIndex = 0
        var destinationIndex = 0
        redactions.forEach { redaction ->
            val prefixLength = redaction.start - sourceIndex
            document.copyInto(output, destinationIndex, sourceIndex, redaction.start)
            destinationIndex += prefixLength
            output[destinationIndex++] = '"'
            output[destinationIndex++] = '"'
            sourceIndex = redaction.endExclusive
        }
        document.copyInto(output, destinationIndex, sourceIndex, document.size)
        return output
    }

    private fun ByteArray.indexOf(
        needle: ByteArray,
        fromIndex: Int,
    ): Int {
        if (needle.isEmpty()) return fromIndex.coerceAtMost(size)
        val lastStart = size - needle.size
        for (start in fromIndex..lastStart) {
            var matches = true
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private data class Redaction(
        val start: Int,
        val endExclusive: Int,
    )

    private class JsonFieldParser(
        private val document: CharArray,
        private val fieldNames: Set<String>,
        private val fieldObjectPath: List<String>,
        private val fields: MutableMap<String, CharArray>,
        private val redactions: MutableList<Redaction>,
    ) {
        private var cursor = 0

        fun parse() {
            skipWhitespace()
            parseValue(objectPath = emptyList(), depth = 0, propertyName = null, capture = false)
            skipWhitespace()
            require(cursor == document.size) { "Trailing JSON data" }
        }

        private fun parseValue(
            objectPath: List<String>?,
            depth: Int,
            propertyName: String?,
            capture: Boolean,
        ) {
            skipWhitespace()
            require(!capture || current() == '"') { "Sensitive JSON field must be a string" }
            when (current()) {
                '{' -> parseObject(objectPath, depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> {
                    if (capture) {
                        val fieldKey = propertyName ?: error("Missing selected JSON field name")
                        require(fieldKey !in fields) { "Duplicate sensitive JSON field" }
                        val parsed = parseString()
                        fields[fieldKey] = parsed.characters
                        redactions += Redaction(parsed.start, parsed.endExclusive)
                    } else {
                        parseString().characters.fill('\u0000')
                    }
                }
                else -> {
                    require(!capture) { "Sensitive JSON field must be a string" }
                    parsePrimitive()
                }
            }
        }

        private fun parseObject(
            objectPath: List<String>?,
            depth: Int,
        ) {
            require(depth <= MAX_JSON_NESTING_DEPTH) { "JSON nesting exceeds the supported depth" }
            expect('{')
            skipWhitespace()
            if (take('}')) return
            while (true) {
                skipWhitespace()
                require(current() == '"') { "Expected object property" }
                val name = parseString().characters
                val sensitiveName = fieldNames.firstOrNull { name.contentEqualsText(it) }
                val propertyName = name.concatToString()
                name.fill('\u0000')
                skipWhitespace()
                expect(':')
                val selectedPath = objectPath != null && objectPath == fieldObjectPath
                val capture = selectedPath && sensitiveName != null
                val childPath = objectPath?.let { it + propertyName }
                parseValue(childPath, depth, sensitiveName, capture)
                skipWhitespace()
                if (take('}')) return
                expect(',')
            }
        }

        private fun parseArray(depth: Int) {
            require(depth <= MAX_JSON_NESTING_DEPTH) { "JSON nesting exceeds the supported depth" }
            expect('[')
            skipWhitespace()
            if (take(']')) return
            while (true) {
                parseValue(objectPath = null, depth = depth, propertyName = null, capture = false)
                skipWhitespace()
                if (take(']')) return
                expect(',')
            }
        }

        private fun parsePrimitive() {
            val start = cursor
            while (cursor < document.size && document[cursor] !in JSON_PRIMITIVE_DELIMITERS) {
                cursor++
            }
            require(cursor > start) { "Expected JSON value" }
        }

        private fun parseString(): ParsedString {
            val start = cursor
            expect('"')
            var characters = CharArray(minOf(INITIAL_JSON_STRING_CAPACITY, document.size - cursor).coerceAtLeast(1))
            var length = 0

            fun append(character: Char) {
                require(length < MAX_JSON_STRING_CHARACTERS) { "JSON string exceeds the supported size" }
                if (length == characters.size) {
                    val replacement = characters.copyOf(minOf(MAX_JSON_STRING_CHARACTERS, characters.size * 2))
                    characters.fill('\u0000')
                    characters = replacement
                }
                characters[length++] = character
            }

            try {
                while (true) {
                    require(cursor < document.size) { "Unterminated JSON string" }
                    val character = document[cursor++]
                    when {
                        character == '"' -> return ParsedString(start, cursor, characters.copyOf(length))
                        character == '\\' -> {
                            require(cursor < document.size) { "Incomplete JSON escape" }
                            when (val escaped = document[cursor++]) {
                                '"', '\\', '/' -> append(escaped)
                                'b' -> append('\b')
                                'f' -> append('\u000C')
                                'n' -> append('\n')
                                'r' -> append('\r')
                                't' -> append('\t')
                                'u' -> append(parseUnicodeEscape())
                                else -> error("Invalid JSON escape")
                            }
                        }
                        character.code < JSON_CONTROL_LIMIT -> error("Unescaped JSON control character")
                        else -> append(character)
                    }
                }
            } finally {
                characters.fill('\u0000')
            }
        }

        private fun parseUnicodeEscape(): Char {
            require(cursor <= document.size - UNICODE_ESCAPE_DIGITS) { "Incomplete Unicode escape" }
            var value = 0
            repeat(UNICODE_ESCAPE_DIGITS) {
                val digit = document[cursor++].digitToIntOrNull(HEX_RADIX) ?: error("Invalid Unicode escape")
                value = (value shl HEX_NIBBLE_SHIFT) or digit
            }
            return value.toChar()
        }

        private fun skipWhitespace() {
            while (cursor < document.size && document[cursor] in JSON_WHITESPACE) cursor++
        }

        private fun current(): Char = document.getOrElse(cursor) { '\u0000' }

        private fun take(expected: Char): Boolean {
            if (current() != expected) return false
            cursor++
            return true
        }

        private fun expect(expected: Char) {
            require(take(expected)) { "Expected JSON delimiter" }
        }

        private fun CharArray.contentEqualsText(text: String): Boolean =
            size == text.length && indices.all { this[it] == text[it] }
    }

    private data class ParsedString(
        val start: Int,
        val endExclusive: Int,
        val characters: CharArray,
    )
}
