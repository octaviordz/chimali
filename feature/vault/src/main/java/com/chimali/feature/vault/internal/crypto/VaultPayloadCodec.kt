package com.chimali.feature.vault.internal.crypto

import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** FR-VAULT-026/035: legacy JSON strings on the wire, owned erasable arrays in memory. */
internal class VaultPayloadCodec(
    private val observeAllocation: (Any) -> Unit = {},
) {
    fun encode(payload: PasswordPayload): ByteArray =
        encode(
            listOf(
                TITLE to payload.title,
                "username" to payload.username,
                "password" to payload.password,
                "uri" to payload.uri,
                NOTES to payload.notes,
            ),
            payload.customFields,
        )

    fun encode(payload: CreditCardPayload): ByteArray =
        encode(
            listOf(
                TITLE to payload.title,
                "cardholderName" to payload.cardholderName,
                "cardNumber" to payload.cardNumber,
                "expirationDate" to payload.expirationDate,
                "cvv" to payload.cvv,
                NOTES to payload.notes,
            ),
            payload.customFields,
        )

    fun encode(payload: SecureNotePayload): ByteArray =
        encode(
            listOf(TITLE to payload.title, "content" to payload.content),
            payload.customFields,
        )

    private fun encode(
        values: List<Pair<String, CharArray?>>,
        fields: List<CustomField>?,
    ): ByteArray {
        val scope = Buffers(observeAllocation)
        try {
            val capacity =
                JSON_FIXED_SIZE +
                    values.sumOf {
                        it.first.length + (it.second?.size ?: 0).toLong() * JSON_ESCAPE_SIZE
                    } +
                    (
                        fields?.sumOf {
                            CUSTOM_FIELD_FIXED_SIZE +
                                (it.name.size.toLong() + it.value.size) * JSON_ESCAPE_SIZE
                        }
                            ?: 0
                    )
            require(
                capacity <= Int.MAX_VALUE / UTF8_MAX_BYTES_PER_CHAR,
            ) { "Vault payload exceeds addressable buffer size" }
            val chars = scope.chars(capacity.toInt())
            val writer = Writer(chars)
            writer.literal("{")
            values.forEachIndexed { index, (key, value) ->
                if (index != 0) writer.literal(",")
                writer.literal("\"$key\":")
                if (value == null) writer.literal("null") else writer.string(value)
            }
            writer.literal(",\"customFields\":")
            if (fields == null) {
                writer.literal("null")
            } else {
                writer.literal("[")
                fields.forEachIndexed { index, field ->
                    if (index != 0) writer.literal(",")
                    writer.literal("{\"name\":")
                    writer.string(field.name)
                    writer.literal(",\"value\":")
                    writer.string(field.value)
                    writer.literal(",\"isConcealed\":${field.isConcealed}}")
                }
                writer.literal("]")
            }
            writer.literal("}")
            val bytes = scope.bytes(writer.position * UTF8_MAX_BYTES_PER_CHAR)
            val output = ByteBuffer.wrap(bytes)
            val encoder =
                Charsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
            throwIfCoderError(encoder.encode(CharBuffer.wrap(chars, 0, writer.position), output, true))
            throwIfCoderError(encoder.flush(output))
            return bytes.copyOf(output.position())
        } finally {
            scope.clear()
        }
    }

    fun decodePassword(bytes: ByteArray): PasswordPayload =
        decode(bytes, setOf(TITLE, "username", "password", "uri", NOTES)) { fields ->
            PasswordPayload(
                fields.required(TITLE),
                fields.required("username"),
                fields.required("password"),
                fields.required("uri"),
                fields.text[NOTES],
                fields.custom,
            )
        }

    fun decodeCreditCard(bytes: ByteArray): CreditCardPayload =
        decode(bytes, setOf(TITLE, "cardholderName", "cardNumber", "expirationDate", "cvv", NOTES)) { fields ->
            CreditCardPayload(
                fields.required(TITLE),
                fields.required("cardholderName"),
                fields.required("cardNumber"),
                fields.required("expirationDate"),
                fields.required("cvv"),
                fields.text[NOTES],
                fields.custom,
            )
        }

    fun decodeSecureNote(bytes: ByteArray): SecureNotePayload =
        decode(bytes, setOf(TITLE, "content")) { fields ->
            SecureNotePayload(fields.required(TITLE), fields.required("content"), fields.custom)
        }

    private fun <T> decode(
        bytes: ByteArray,
        names: Set<String>,
        create: (Fields) -> T,
    ): T {
        val scope = Buffers(observeAllocation)
        try {
            val chars = scope.chars(bytes.size)
            val target = CharBuffer.wrap(chars)
            val decoder =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
            throwIfCoderError(decoder.decode(ByteBuffer.wrap(bytes), target, true))
            throwIfCoderError(decoder.flush(target))
            val reader = Reader(chars, target.position(), scope)
            val fields = reader.payload(names)
            val result = create(fields)
            reader.end()
            // Only validated final fields transfer; duplicate/unknown/partial arrays remain owned here.
            scope.transfer(fields.text.values.toList(), fields.custom)
            return result
        } finally {
            scope.clear()
        }
    }

    private class Fields {
        val text = mutableMapOf<String, CharArray?>()
        var custom: List<CustomField>? = null

        fun required(key: String): CharArray = text[key] ?: throw invalidPayload()
    }

    private class Buffers(
        private val observe: (Any) -> Unit,
    ) {
        private val chars = mutableListOf<CharArray>()
        private val bytes = mutableListOf<ByteArray>()

        fun chars(size: Int): CharArray =
            CharArray(size).also {
                chars.add(it)
                observe(it)
            }

        fun bytes(size: Int): ByteArray =
            ByteArray(size).also {
                bytes.add(it)
                observe(it)
            }

        fun transfer(
            values: List<CharArray?>,
            fields: List<CustomField>?,
        ) {
            values.forEach { value -> chars.removeAll { it === value } }
            fields?.forEach { field -> chars.removeAll { it === field.name || it === field.value } }
        }

        fun clear() {
            chars.forEach { it.fill('\u0000') }
            bytes.forEach { it.fill(0) }
        }
    }

    private class Writer(
        private val chars: CharArray,
    ) {
        var position = 0
            private set

        fun literal(text: String) {
            text.forEach { chars[position++] = it }
        }

        fun string(value: CharArray) {
            literal("\"")
            value.forEach { c ->
                when (c) {
                    '"' -> literal("\\\"")
                    '\\' -> literal("\\\\")
                    '\b' -> literal("\\b")
                    '\u000c' -> literal("\\f")
                    '\n' -> literal("\\n")
                    '\r' -> literal("\\r")
                    '\t' -> literal("\\t")
                    else ->
                        if (c < ' ') {
                            literal("\\u00")
                            chars[position++] = HEX[c.code shr NIBBLE_BITS]
                            chars[position++] = HEX[c.code and NIBBLE_MASK]
                        } else {
                            chars[position++] = c
                        }
                }
            }
            literal("\"")
        }
    }

    private class Reader(
        private val input: CharArray,
        private val length: Int,
        private val scope: Buffers,
    ) {
        private var position = 0

        private fun space() {
            while (position < length && input[position] in " \r\n\t") position++
        }

        private fun take(c: Char): Boolean {
            space()
            return if (position < length &&
                input[position] == c
            ) {
                position++
                true
            } else {
                false
            }
        }

        private fun expect(c: Char) {
            if (!take(c)) throw invalidPayload()
        }

        fun end() {
            space()
            if (position != length) throw invalidPayload()
        }

        private fun matches(
            value: CharArray,
            literal: String,
        ): Boolean = value.size == literal.length && value.indices.all { value[it] == literal[it] }

        private fun keyword(value: String) {
            space()
            value.forEach { if (position >= length || input[position++] != it) throw invalidPayload() }
        }

        private fun isNull(): Boolean {
            space()
            return if (position < length &&
                input[position] == 'n'
            ) {
                keyword("null")
                true
            } else {
                false
            }
        }

        private fun nullableString(): CharArray? = if (isNull()) null else string()

        private fun string(): CharArray {
            expect('"')
            val start = position
            var scan = position
            while (scan < length && input[scan] != '"') {
                if (input[scan] == '\\') scan++
                scan++
            }
            if (scan >= length) throw invalidPayload()
            val scratch = scope.chars(scan - start)
            var count = 0
            while (position < scan) {
                val c = input[position++]
                scratch[count++] =
                    when {
                        c == '\\' -> escaped(scan)
                        c < ' ' -> throw invalidPayload()
                        else -> c
                    }
            }
            position++
            return if (count == scratch.size) {
                scratch
            } else {
                scope.chars(count).also {
                    scratch.copyInto(it, endIndex = count)
                    scratch.fill('\u0000')
                }
            }
        }

        private fun escaped(scan: Int): Char =
            when (val value = input[position++]) {
                '"', '\\', '/' -> value
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> unicode(scan)
                else -> throw invalidPayload()
            }

        private fun unicode(scan: Int): Char {
            if (scan - position < UNICODE_DIGITS) throw invalidPayload()
            var code = 0
            repeat(UNICODE_DIGITS) {
                code = code * HEX_RADIX +
                    (input[position++].digitToIntOrNull(HEX_RADIX) ?: throw invalidPayload())
            }
            return code.toChar()
        }

        private fun obj(field: (CharArray) -> Unit) {
            expect('{')
            if (take('}')) return
            do {
                val name = string()
                expect(':')
                try {
                    field(name)
                } finally {
                    name.fill('\u0000')
                }
                if (take('}')) return
                expect(',')
            } while (position < length)
            throw invalidPayload()
        }

        fun payload(names: Set<String>): Fields {
            val result = Fields()
            obj { key ->
                val known = names.firstOrNull { matches(key, it) }
                when {
                    known != null -> {
                        result.text[known]?.fill('\u0000')
                        result.text[known] = if (known == NOTES) nullableString() else string()
                    }
                    matches(key, "customFields") -> {
                        result.custom?.forEach { it.clearMemory() }
                        result.custom = customFields()
                    }
                    else -> skip()
                }
            }
            return result
        }

        private fun customFields(): List<CustomField>? {
            if (isNull()) return null
            expect('[')
            val result = mutableListOf<CustomField>()
            if (take(']')) return result
            do {
                var name: CharArray? = null
                var value: CharArray? = null
                var concealed: Boolean? = null
                obj { key ->
                    when {
                        matches(key, "name") -> {
                            name?.fill('\u0000')
                            name = string()
                        }
                        matches(key, "value") -> {
                            value?.fill('\u0000')
                            value = string()
                        }
                        matches(key, "isConcealed") -> concealed = boolean()
                        else -> skip()
                    }
                }
                val finalName = name
                val finalValue = value
                val finalConcealed = concealed
                if (finalName == null || finalValue == null || finalConcealed == null) throw invalidPayload()
                result.add(CustomField(finalName, finalValue, finalConcealed))
                if (take(']')) return result
                expect(',')
            } while (position < length)
            throw invalidPayload()
        }

        private fun boolean(): Boolean {
            space()
            if (position < length && input[position] == 't') {
                keyword("true")
                return true
            }
            keyword("false")
            return false
        }

        // Iterative grammar: nesting is bounded by input length, not the call stack.
        private enum class ContainerState {
            OBJECT_START,
            OBJECT_KEY,
            OBJECT_COLON,
            OBJECT_VALUE,
            OBJECT_END,
            ARRAY_START,
            ARRAY_VALUE,
            ARRAY_END,
        }

        private fun skip() {
            val states = mutableListOf<ContainerState>()

            fun value() {
                space()
                if (position >= length) throw invalidPayload()
                when (input[position]) {
                    '"' -> string().fill('\u0000')
                    '{' -> {
                        position++
                        states.add(ContainerState.OBJECT_START)
                    }
                    '[' -> {
                        position++
                        states.add(ContainerState.ARRAY_START)
                    }
                    'n' -> keyword("null")
                    't', 'f' -> boolean()
                    else -> number()
                }
            }
            value()
            while (states.isNotEmpty()) {
                val last = states.lastIndex
                when (states[last]) {
                    ContainerState.OBJECT_START ->
                        if (take('}')) {
                            states.removeAt(last)
                        } else {
                            string().fill('\u0000')
                            states[last] = ContainerState.OBJECT_COLON
                        }
                    ContainerState.OBJECT_KEY -> {
                        string().fill('\u0000')
                        states[last] = ContainerState.OBJECT_COLON
                    }
                    ContainerState.OBJECT_COLON -> {
                        expect(':')
                        states[last] = ContainerState.OBJECT_VALUE
                    }
                    ContainerState.OBJECT_VALUE -> {
                        states[last] = ContainerState.OBJECT_END
                        value()
                    }
                    ContainerState.OBJECT_END ->
                        if (take('}')) {
                            states.removeAt(last)
                        } else {
                            expect(',')
                            states[last] = ContainerState.OBJECT_KEY
                        }
                    ContainerState.ARRAY_START ->
                        if (take(']')) {
                            states.removeAt(last)
                        } else {
                            states[last] = ContainerState.ARRAY_END
                            value()
                        }
                    ContainerState.ARRAY_VALUE -> {
                        states[last] = ContainerState.ARRAY_END
                        value()
                    }
                    ContainerState.ARRAY_END ->
                        if (take(']')) {
                            states.removeAt(last)
                        } else {
                            expect(',')
                            states[last] = ContainerState.ARRAY_VALUE
                        }
                }
            }
        }

        private fun number() {
            take('-')
            if (position >= length) throw invalidPayload()
            if (input[position] == '0') position++ else digits(true)
            if (position < length && input[position] == '.') {
                position++
                digits(false)
            }
            if (position < length && input[position] in "eE") {
                position++
                if (position < length && input[position] in "+-") position++
                digits(false)
            }
        }

        private fun digits(nonzero: Boolean) {
            val start = position
            if (nonzero) requireNonzeroDigit()
            while (position < length && input[position] in '0'..'9') position++
            if (position == start) throw invalidPayload()
        }

        private fun requireNonzeroDigit() {
            if (position >= length) throw invalidPayload()
            if (input[position] !in '1'..'9') throw invalidPayload()
        }
    }

    private companion object {
        const val TITLE = "title"
        const val NOTES = "notes"
        const val JSON_FIXED_SIZE = 256L
        const val CUSTOM_FIELD_FIXED_SIZE = 64L
        const val JSON_ESCAPE_SIZE = 6
        const val UTF8_MAX_BYTES_PER_CHAR = 3
        const val NIBBLE_BITS = 4
        const val NIBBLE_MASK = 15
        const val UNICODE_DIGITS = 4
        const val HEX_RADIX = 16

        const val HEX = "0123456789abcdef"

        fun invalidPayload(): IllegalArgumentException = IllegalArgumentException("Invalid vault payload encoding")
    }
}

/** Kept testable because malformed coder results are otherwise unavailable from fixed UTF-8 mode. */
internal fun throwIfCoderError(result: java.nio.charset.CoderResult) {
    if (!result.isError) return
    throw coderError(result)
}

/** An error CoderResult is specified to throw CharacterCodingException; retain that exact error. */
private fun coderError(result: java.nio.charset.CoderResult): java.nio.charset.CharacterCodingException =
    runCatching { result.throwException() }.exceptionOrNull() as java.nio.charset.CharacterCodingException
