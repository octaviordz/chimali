package com.chimali.fido2.data.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal RFC 7049 (CBOR) codec for the CTAP2 HID protocol.
 *
 * CTAP2 messages from Windows are binary CBOR with **integer keys**.
 * The previous JSON-based implementation silently returned emptyMap() on every
 * incoming CTAP2 payload, causing all credential lookups to fail.
 *
 * This implementation supports the CBOR types used by CTAP2:
 * - Major type 0: unsigned integer  → Long / Int
 * - Major type 1: negative integer  → Long
 * - Major type 2: byte string       → ByteArray
 * - Major type 3: text string       → String
 * - Major type 4: array             → List<Any>
 * - Major type 5: map               → Map<String, Any>  (int keys → "1","2",…)
 * - Major type 7 / simple:          → Boolean / null
 */
@Singleton
class CborCodec @Inject constructor() {

    // ── Decoding ──────────────────────────────────────────────────────────────

    /**
     * Decode a CTAP2 binary-CBOR byte array into a Kotlin Map<String, Any>.
     * Integer CBOR keys are converted to string representations to remain
     * compatible with handler code that refers to map["1"], map["2"], etc.
     */
    fun decodeFromFido2Format(data: ByteArray): Map<String, Any> = try {
        val (value, _) = decodeItem(data, 0)
        @Suppress("UNCHECKED_CAST")
        (value as? Map<String, Any>) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }

    // ── Encoding ──────────────────────────────────────────────────────────────

    /**
     * Encode a Kotlin Map to binary CBOR. Keys that look like integers are
     * encoded as CBOR unsigned-integer keys; others as text strings.
     */
    fun encodeToFido2Format(data: Map<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        writeMap(out, data)
        return out.toByteArray()
    }

    // ── Recursive decoder ─────────────────────────────────────────────────────

    /** Returns (decodedValue, nextOffset). */
    private fun decodeItem(data: ByteArray, offset: Int): Pair<Any?, Int> {
        val initial = data[offset].toInt() and 0xFF
        val major   = initial shr 5
        val info    = initial and 0x1F
        var pos     = offset + 1

        val (argument, newPos) = decodeArgument(data, pos, info)
        pos = newPos

        return when (major) {
            0 -> Pair(argument, pos)                            // unsigned integer
            1 -> Pair(-(argument as Long) - 1L, pos)            // negative integer
            2 -> {                                              // byte string
                val len = (argument as Long).toInt()
                val bytes = data.copyOfRange(pos, pos + len)
                Pair(bytes, pos + len)
            }
            3 -> {                                              // text string
                val len = (argument as Long).toInt()
                val str = String(data, pos, len, Charsets.UTF_8)
                Pair(str, pos + len)
            }
            4 -> {                                              // array
                val count = (argument as Long).toInt()
                val list = mutableListOf<Any?>()
                var cur = pos
                repeat(count) {
                    val (item, next) = decodeItem(data, cur)
                    list.add(item)
                    cur = next
                }
                Pair(list, cur)
            }
            5 -> {                                              // map
                val count = (argument as Long).toInt()
                val map = mutableMapOf<String, Any>()
                var cur = pos
                repeat(count) {
                    val (k, kNext) = decodeItem(data, cur)
                    val (v, vNext) = decodeItem(data, kNext)
                    // Normalise key to String
                    val keyStr = when (k) {
                        is Long   -> k.toString()
                        is String -> k
                        else      -> k.toString()
                    }
                    if (v != null) map[keyStr] = v
                    cur = vNext
                }
                Pair(map, cur)
            }
            7 -> {                                              // float/simple/break
                val value: Any? = when (info) {
                    20   -> false        // false
                    21   -> true         // true
                    22   -> null         // null
                    23   -> null         // undefined
                    else -> argument     // half/single/double — treat as Long
                }
                Pair(value, pos)
            }
            else -> Pair(null, pos)
        }
    }

    /**
     * Decode the CBOR additional-info argument (length / small integer).
     * Returns (valueAsLong, nextOffset).
     */
    private fun decodeArgument(data: ByteArray, offset: Int, info: Int): Pair<Long, Int> {
        return when {
            info < 24 -> Pair(info.toLong(), offset)
            info == 24 -> Pair((data[offset].toInt() and 0xFF).toLong(), offset + 1)
            info == 25 -> {
                val v = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                Pair(v.toLong(), offset + 2)
            }
            info == 26 -> {
                var v = 0L
                for (i in 0..3) v = (v shl 8) or (data[offset + i].toInt() and 0xFF).toLong()
                Pair(v, offset + 4)
            }
            info == 27 -> {
                var v = 0L
                for (i in 0..7) v = (v shl 8) or (data[offset + i].toInt() and 0xFF).toLong()
                Pair(v, offset + 8)
            }
            else -> Pair(0L, offset)
        }
    }

    // ── Recursive encoder ─────────────────────────────────────────────────────

    private fun writeValue(out: ByteArrayOutputStream, value: Any?) {
        when (value) {
            is Boolean   -> writeByte(out, if (value) 0xF5.toByte() else 0xF4.toByte())
            null         -> writeByte(out, 0xF6.toByte())
            is ByteArray -> { writeHeader(out, 2, value.size.toLong()); out.write(value) }
            is String    -> { val b = value.toByteArray(Charsets.UTF_8); writeHeader(out, 3, b.size.toLong()); out.write(b) }
            is Long      -> writeUInt(out, value)
            is Int       -> writeUInt(out, value.toLong())
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(out, value as Map<String, Any>)
            }
            is List<*>   -> {
                writeHeader(out, 4, value.size.toLong())
                value.forEach { writeValue(out, it) }
            }
            else         -> {
                val str = value.toString()
                val b = str.toByteArray(Charsets.UTF_8)
                writeHeader(out, 3, b.size.toLong())
                out.write(b)
            }
        }
    }

    private fun writeMap(out: ByteArrayOutputStream, map: Map<String, Any>) {
        writeHeader(out, 5, map.size.toLong())
        for ((k, v) in map) {
            val asLong = k.toLongOrNull()
            if (asLong != null) writeUInt(out, asLong) else writeValue(out, k)
            writeValue(out, v)
        }
    }

    private fun writeUInt(out: ByteArrayOutputStream, value: Long) = writeHeader(out, 0, value)

    private fun writeHeader(out: ByteArrayOutputStream, major: Int, argument: Long) {
        val hi = major shl 5
        when {
            argument < 24          -> writeByte(out, (hi or argument.toInt()).toByte())
            argument <= 0xFF       -> { writeByte(out, (hi or 24).toByte()); writeByte(out, argument.toByte()) }
            argument <= 0xFFFF     -> { writeByte(out, (hi or 25).toByte()); writeShort(out, argument.toInt()) }
            argument <= 0xFFFFFFFFL -> { writeByte(out, (hi or 26).toByte()); writeInt(out, argument.toInt()) }
            else                   -> { writeByte(out, (hi or 27).toByte()); writeLong(out, argument) }
        }
    }

    private fun writeByte(out: ByteArrayOutputStream, b: Byte)  = out.write(b.toInt())
    private fun writeShort(out: ByteArrayOutputStream, v: Int)  { out.write(v shr 8); out.write(v) }
    private fun writeInt(out: ByteArrayOutputStream, v: Int)    { DataOutputStream(out).writeInt(v) }
    private fun writeLong(out: ByteArrayOutputStream, v: Long)  { DataOutputStream(out).writeLong(v) }

    // ── Legacy helpers ────────────────────────────────────────────────────────

    fun encodeAttestationObject(
        rpIdHash: ByteArray, flags: Byte, counter: Int,
        aaguid: ByteArray, credentialId: ByteArray, publicKeyBytes: ByteArray
    ): ByteArray = encodeToFido2Format(mapOf(
        "fmt" to "packed",
        "authData" to mapOf(
            "rpIdHash" to rpIdHash, "flags" to flags.toInt(), "counter" to counter,
            "aaguid" to aaguid, "credentialId" to credentialId, "publicKey" to publicKeyBytes
        )
    ))

    fun encodeAuthenticatorData(rpIdHash: ByteArray, flags: Byte, counter: Int): ByteArray =
        encodeToFido2Format(mapOf("rpIdHash" to rpIdHash, "flags" to flags.toInt(), "counter" to counter))

    fun encodeClientDataJson(type: String, challenge: ByteArray, origin: String, crossOrigin: Boolean = false): ByteArray =
        encodeToFido2Format(mapOf("type" to type, "challenge" to challenge, "origin" to origin, "crossOrigin" to crossOrigin))
}
