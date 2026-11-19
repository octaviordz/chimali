package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.security.memory.ErasableCharBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException

/**
 * Mutable-buffer JSON codec for Vault persistence. Keeps the legacy JSON field layout.
 * Parser catches erase partially built mutable trees before translating malformed input to fixed errors.
 */
@Suppress(
    "InstanceOfCheckForException",
    "MagicNumber",
    "StringLiteralDuplication",
    "SwallowedException",
    "ThrowsCount",
    "TooGenericExceptionCaught",
)
internal object VaultMutableJsonCodec {
    private const val CREATED = "com.chimali.core.domain.eventsourcing.vault.VaultEvent.Created"
    private const val UPDATED = "com.chimali.core.domain.eventsourcing.vault.VaultEvent.Updated"
    private const val DELETED = "com.chimali.core.domain.eventsourcing.vault.VaultEvent.Deleted"

    fun encodeEvent(event: VaultEvent): ByteArray =
        encode { out ->
            out.append('{')
            when (event) {
                is VaultEvent.Created -> {
                    out.field("eventType", CREATED)
                    out.append(',')
                    out.field("aggregateId", event.aggregateId)
                    out.append(',')
                    out.numberField("sequenceNumber", event.sequenceNumber)
                    out.append(',')
                    out.field("timestamp", event.timestamp.toString())
                    out.append(',')
                    out.field("type", event.type)
                    out.append(',')
                    out.charField("title", event.title)
                    out.append(',')
                    out.byteArrayField("payload", event.payload)
                    out.append(',')
                    out.field("identityId", event.identityId)
                }
                is VaultEvent.Updated -> {
                    out.field("eventType", UPDATED)
                    out.append(',')
                    out.field("aggregateId", event.aggregateId)
                    out.append(',')
                    out.numberField("sequenceNumber", event.sequenceNumber)
                    out.append(',')
                    out.field("timestamp", event.timestamp.toString())
                    out.append(',')
                    out.nullableCharField("title", event.title)
                    out.append(',')
                    out.nullableByteArrayField("payload", event.payload)
                }
                is VaultEvent.Deleted -> {
                    out.field("eventType", DELETED)
                    out.append(',')
                    out.field("aggregateId", event.aggregateId)
                    out.append(',')
                    out.numberField("sequenceNumber", event.sequenceNumber)
                    out.append(',')
                    out.field("timestamp", event.timestamp.toString())
                }
            }
            out.append('}')
        }

    fun encodeSnapshot(snapshot: Snapshot<VaultState>): ByteArray =
        encode { out ->
            out.append('{')
            out.field("aggregateId", snapshot.aggregateId)
            out.append(',')
            out.numberField("sequenceNumber", snapshot.sequenceNumber)
            out.append(',')
            out.append('"').append("state").append("\":{")
            val state = snapshot.state
            var hasStateField = false

            fun separator() {
                if (hasStateField) out.append(',')
                hasStateField = true
            }

            fun emitField(
                present: Boolean,
                write: () -> Unit,
            ) {
                if (present) {
                    separator()
                    write()
                }
            }

            emitField(state.id.isNotEmpty()) { out.field("id", state.id) }
            emitField(state.type.isNotEmpty()) { out.field("type", state.type) }
            emitField(state.title.isNotEmpty()) { out.charField("title", state.title) }
            emitField(state.payload.isNotEmpty()) { out.byteArrayField("payload", state.payload) }
            emitField(state.identityId.isNotEmpty()) { out.field("identityId", state.identityId) }
            emitField(state.isDeleted) { out.booleanField("isDeleted", true) }
            emitField(state.sequenceNumber != 0L) { out.numberField("sequenceNumber", state.sequenceNumber) }
            out.append('}').append(',')
            out.field("timestamp", snapshot.timestamp.toString())
            out.append('}')
        }

    fun decodeEvent(bytes: ByteArray): VaultEvent =
        decode(bytes) { root ->
            val kind = root.string("eventType")
            val aggregate = root.string("aggregateId")
            val sequence = root.long("sequenceNumber")
            val timestamp = Instant.parse(root.string("timestamp"))
            when (kind) {
                CREATED -> {
                    var title: CharArray? = null
                    var payload: ByteArray? = null
                    try {
                        val event =
                            VaultEvent.Created(
                                aggregate,
                                sequence,
                                timestamp,
                                root.string("type"),
                                root.chars("title").also { title = it },
                                root.bytes("payload").also { payload = it },
                                root.string("identityId"),
                            )
                        title = null
                        payload = null
                        event
                    } finally {
                        title?.fill('\u0000')
                        payload?.fill(0)
                    }
                }
                UPDATED -> {
                    var title: CharArray? = null
                    var payload: ByteArray? = null
                    try {
                        val event =
                            VaultEvent.Updated(
                                aggregate,
                                sequence,
                                timestamp,
                                root.nullableChars("title").also { title = it },
                                root.nullableBytes("payload").also { payload = it },
                            )
                        title = null
                        payload = null
                        event
                    } finally {
                        title?.fill('\u0000')
                        payload?.fill(0)
                    }
                }
                DELETED -> VaultEvent.Deleted(aggregate, sequence, timestamp)
                else -> throw SerializationException("Unsupported Vault event type")
            }
        }

    fun decodeSnapshot(bytes: ByteArray): Snapshot<VaultState> =
        decode(bytes) { root ->
            val stateNode = root.obj("state")
            val aggregateId = root.string("aggregateId")
            val sequenceNumber = root.long("sequenceNumber")
            val timestamp = Instant.parse(root.string("timestamp"))
            var title: CharArray? = null
            var payload: ByteArray? = null
            var transferred = false
            try {
                val state =
                    VaultState(
                        id = stateNode.optionalString("id") ?: "",
                        type = stateNode.optionalString("type") ?: "",
                        title = (stateNode.optionalChars("title") ?: charArrayOf()).also { title = it },
                        payload = (stateNode.optionalBytes("payload") ?: byteArrayOf()).also { payload = it },
                        identityId = stateNode.optionalString("identityId") ?: "",
                        isDeleted = stateNode.optionalBoolean("isDeleted") ?: false,
                        sequenceNumber = stateNode.optionalLong("sequenceNumber") ?: 0,
                    )
                val snapshot = Snapshot(aggregateId, sequenceNumber, state, timestamp)
                transferred = true
                snapshot
            } finally {
                if (!transferred) {
                    title?.fill('\u0000')
                    payload?.fill(0)
                }
            }
        }

    private fun encode(write: (ErasableCharBuffer) -> Unit): ByteArray {
        val chars = ErasableCharBuffer()
        var charCopy: CharArray? = null
        var encoded: java.nio.ByteBuffer? = null
        try {
            write(chars)
            charCopy = chars.copyToOwnedChars()
            encoded =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(charCopy))
            val result = ByteArray(encoded.remaining())
            encoded.get(result)
            return result
        } catch (e: Exception) {
            if (e is SerializationException) throw e
            throw SerializationException("Unable to encode Vault metadata")
        } finally {
            chars.erase()
            charCopy?.fill('\u0000')
            encoded?.let { if (it.hasArray()) it.array().fill(0) }
        }
    }

    private inline fun <T> decode(
        bytes: ByteArray,
        convert: (JObject) -> T,
    ): T {
        val chars =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
            } catch (e: Exception) {
                throw SerializationException("Invalid Vault metadata encoding")
            }
        val input = CharArray(chars.remaining())
        chars.get(input)
        if (chars.hasArray()) chars.array().fill('\u0000')
        val root =
            try {
                JsonParser(input).parse() as? JObject ?: throw SerializationException("Invalid Vault metadata")
            } catch (
                e: Exception,
            ) {
                input.fill('\u0000')
                throw if (e is SerializationException) e else SerializationException("Invalid Vault metadata")
            }
        try {
            return try {
                convert(root)
            } catch (e: Exception) {
                if (e is SerializationException) throw e
                throw SerializationException("Invalid Vault metadata")
            }
        } finally {
            root.erase()
            input.fill('\u0000')
        }
    }

    private fun ErasableCharBuffer.field(
        name: String,
        value: String,
    ) {
        quote(name)
        append(':')
        quote(value)
    }

    private fun ErasableCharBuffer.numberField(
        name: String,
        value: Long,
    ) {
        quote(name)
        append(':')
        append(value.toString())
    }

    private fun ErasableCharBuffer.booleanField(
        name: String,
        value: Boolean,
    ) {
        quote(name)
        append(':')
        append(value.toString())
    }

    private fun ErasableCharBuffer.charField(
        name: String,
        value: CharArray,
    ) {
        quote(name)
        append(':')
        quote(value)
    }

    private fun ErasableCharBuffer.nullableCharField(
        name: String,
        value: CharArray?,
    ) {
        quote(name)
        append(':')
        if (value ==
            null
        ) {
            append("null")
        } else {
            quote(value)
        }
    }

    private fun ErasableCharBuffer.byteArrayField(
        name: String,
        value: ByteArray,
    ) {
        quote(name)
        append(':')
        append('[')
        value.forEachIndexed { i, b ->
            if (i >
                0
            ) {
                append(',')
            }
            ; append(b.toString())
        }
        append(']')
    }

    private fun ErasableCharBuffer.nullableByteArrayField(
        name: String,
        value: ByteArray?,
    ) {
        if (value ==
            null
        ) {
            quote(name)
            append(":null")
        } else {
            byteArrayField(name, value)
        }
    }

    private fun ErasableCharBuffer.quote(value: String) {
        append('"')
        value.forEach { escaped(it) }
        append('"')
    }

    private fun ErasableCharBuffer.quote(value: CharArray) {
        append('"')
        value.forEach { escaped(it) }
        append('"')
    }

    private fun ErasableCharBuffer.escaped(c: Char) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else ->
                if (c <
                    ' '
                ) {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
        }
    }

    private sealed interface JValue {
        fun erase()
    }

    private data class JText(
        val value: CharArray,
    ) : JValue {
        override fun erase() {
            value.fill('\u0000')
        }
    }

    private data class JNumber(
        val value: String,
    ) : JValue {
        override fun erase() = Unit
    }

    private data class JBool(
        val value: Boolean,
    ) : JValue {
        override fun erase() = Unit
    }

    private data object JNull : JValue {
        override fun erase() = Unit
    }

    private data class JArray(
        val values: List<JValue>,
    ) : JValue {
        override fun erase() = values.forEach(JValue::erase)
    }

    private data class JObject(
        val values: Map<String, JValue>,
    ) : JValue {
        override fun erase() = values.values.forEach(JValue::erase)

        fun required(key: String): JValue = values[key] ?: throw SerializationException("Missing Vault metadata field")

        fun string(key: String): String =
            (required(key) as? JText)?.value?.concatToString()
                ?: throw SerializationException("Invalid Vault metadata field")

        fun optionalString(key: String): String? =
            when (val value = values[key]) {
                null -> null
                is JText -> value.value.concatToString()
                else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun chars(key: String): CharArray =
            (
                (required(key) as? JText)?.value
                    ?: throw SerializationException("Invalid Vault metadata field")
            ).copyOf()

        fun optionalChars(key: String): CharArray? =
            when (val value = values[key]) {
                null -> null
                is JText -> value.value.copyOf()
                else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun nullableChars(key: String): CharArray? =
            when (val value = required(key)) {
                JNull -> null
                is JText -> value.value.copyOf()
                else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun bytes(key: String): ByteArray =
            (required(key) as? JArray)
                ?.values
                ?.map {
                    (it as? JNumber)?.value?.toIntOrNull()?.toByte()
                        ?: throw SerializationException("Invalid Vault metadata field")
                }?.toByteArray()
                ?: throw SerializationException("Invalid Vault metadata field")

        fun optionalBytes(key: String): ByteArray? =
            when (val value = values[key]) {
                null -> null
                is JArray ->
                    value.values
                        .map {
                            (it as? JNumber)?.value?.toIntOrNull()?.toByte()
                                ?: throw SerializationException("Invalid Vault metadata field")
                        }.toByteArray()
                ; else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun nullableBytes(key: String): ByteArray? =
            when (val value = required(key)) {
                JNull -> null
                is JArray ->
                    value.values
                        .map {
                            (it as? JNumber)?.value?.toIntOrNull()?.toByte()
                                ?: throw SerializationException("Invalid Vault metadata field")
                        }.toByteArray()
                ; else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun long(key: String): Long =
            (values[key] as? JNumber)?.value?.toLongOrNull()
                ?: throw SerializationException("Invalid Vault metadata field")

        fun optionalLong(key: String): Long? =
            when (val value = values[key]) {
                null -> null
                is JNumber -> value.value.toLongOrNull() ?: throw SerializationException("Invalid Vault metadata field")
                else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun optionalBoolean(key: String): Boolean? =
            when (val value = values[key]) {
                null -> null
                is JBool -> value.value
                else -> throw SerializationException("Invalid Vault metadata field")
            }

        fun obj(key: String): JObject =
            required(key) as? JObject ?: throw SerializationException("Invalid Vault metadata field")
    }

    private class JsonParser(
        private val input: CharArray,
    ) {
        private var at = 0

        fun parse(): JValue {
            whitespace()
            val result = value()
            try {
                whitespace()
                if (at != input.size) fail()
                return result
            } catch (e: Exception) {
                result.erase()
                throw e
            }
        }

        private fun value(): JValue {
            whitespace()
            if (at >=
                input.size
            ) {
                fail()
            }
            return when (input[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> JText(string())
                't' -> {
                    literal("true")
                    JBool(true)
                }
                'f' -> {
                    literal("false")
                    JBool(false)
                }
                'n' -> {
                    literal("null")
                    JNull
                }
                else -> number()
            }
        }

        private fun obj(): JObject {
            at++
            whitespace()
            val map = linkedMapOf<String, JValue>()
            if (take('}')) return JObject(map)
            try {
                while (true) {
                    whitespace()
                    if (at >=
                        input.size ||
                        input[at] != '"'
                    ) {
                        fail()
                    }
                    val keyChars = string()
                    val key =
                        try {
                            keyChars.concatToString()
                        } finally {
                            keyChars.fill('\u0000')
                        }
                    whitespace()
                    expect(':')
                    val v = value()
                    map.put(key, v)?.erase()
                    whitespace()
                    if (take('}')) return JObject(map)
                    expect(',')
                }
            } catch (e: Exception) {
                map.values.forEach(JValue::erase)
                throw e
            }
        }

        private fun array(): JArray {
            at++
            whitespace()
            val list = mutableListOf<JValue>()
            if (take(']')) return JArray(list)
            try {
                while (true) {
                    list += value()
                    whitespace()
                    if (take(']')) return JArray(list)
                    expect(',')
                }
            } catch (e: Exception) {
                list.forEach(JValue::erase)
                throw e
            }
        }

        private fun string(): CharArray {
            expect('"')
            val out = CharArrayBuilder()
            try {
                while (at < input.size) {
                    val c = input[at++]
                    when {
                        c == '"' -> return out.finish()
                        c == '\\' -> {
                            if (at >= input.size) fail()
                            when (val e = input[at++]) {
                                '"', '\\', '/' -> out.add(e)
                                'b' -> out.add('\b')
                                'f' -> out.add('\u000c')
                                'n' -> out.add('\n')
                                'r' -> out.add('\r')
                                't' -> out.add('\t')
                                'u' -> out.add(readHexChar())
                                else -> fail()
                            }
                        }
                        c < ' ' -> fail()
                        else -> out.add(c)
                    }
                }
                fail()
            } catch (e: Exception) {
                out.erase()
                throw e
            }
        }

        private fun readHexChar(): Char {
            if (at + 4 > input.size) fail()
            var value = 0
            repeat(4) {
                val digit = input[at++].digitToIntOrNull(16) ?: fail()
                value = (value shl 4) or digit
            }
            return value.toChar()
        }

        private fun number(): JNumber {
            val start = at
            if (take('-')) Unit
            while (at < input.size &&
                input[at].isDigit()
            ) {
                at++
            }
            if (take('.')) {
                while (at < input.size &&
                    input[at].isDigit()
                ) {
                    at++
                }
            }
            if (at < input.size &&
                (input[at] == 'e' || input[at] == 'E')
            ) {
                at++
                if (at < input.size &&
                    (input[at] == '+' || input[at] == '-')
                ) {
                    at++
                }
                while (at < input.size &&
                    input[at].isDigit()
                ) {
                    at++
                }
            }
            if (at ==
                start
            ) {
                fail()
            }
            return JNumber(input.concatToString(start, at))
        }

        private fun literal(value: String) {
            value.forEach { if (at >= input.size || input[at++] != it) fail() }
        }

        private fun whitespace() {
            while (at < input.size && input[at] in " \n\r\t") at++
        }

        private fun expect(c: Char) {
            if (!take(c)) fail()
        }

        private fun take(c: Char): Boolean =
            if (at < input.size && input[at] == c) {
                at++
                true
            } else {
                false
            }

        private fun fail(): Nothing = throw SerializationException("Invalid Vault metadata")
    }

    private class CharArrayBuilder {
        private var data = CharArray(32)
        private var size = 0

        fun add(c: Char) {
            if (size ==
                data.size
            ) {
                val old = data
                data = old.copyOf(maxOf(32, old.size * 2))
                old.fill('\u0000')
            }
            data[size++] =
                c
        }

        fun finish(): CharArray =
            data.copyOf(size).also {
                data.fill('\u0000')
                size = 0
            }

        fun erase() {
            data.fill('\u0000')
            size = 0
        }
    }
}
