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
    } catch (_: Exception) {
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

    /**
     * Encodes a P-256 EC public key as a CBOR COSE_Key map per RFC 8152 / WebAuthn spec.
     *
     * The credentialPublicKey in authenticatorData MUST be CBOR with integer keys:
     *   COSE_KEY_KTY (1) = COSE_KEY_TYPE_EC2 (2)
     *   COSE_KEY_ALG (3) = COSE_ALG_ES256 (-7)
     *   COSE_KEY_CRV (-1) = COSE_CRV_P256 (1)
     *   COSE_KEY_X (-2)   = 32 bytes   (X coordinate)
     *   COSE_KEY_Y (-3)   = 32 bytes   (Y coordinate)
     *
     * @param uncompressedPoint  65-byte uncompressed EC point: 0x04 || X(32) || Y(32)
     */
    fun encodeCosePublicKeyFromUncompressed(uncompressedPoint: ByteArray): ByteArray {
        require(uncompressedPoint.size == 65 && uncompressedPoint[0] == 0x04.toByte()) {
            "Expected 65-byte uncompressed point starting with 0x04, got ${uncompressedPoint.size} bytes"
        }
        val x = uncompressedPoint.copyOfRange(1, 33)
        val y = uncompressedPoint.copyOfRange(33, 65)
        val out = ByteArrayOutputStream()
        // CBOR map with 5 entries
        out.write(0xA5)              // map(5)
        // kty: 1 = 2 (EC2)
        out.write(0x01)              // uint(1) - kty
        out.write(0x02)              // uint(2) - EC2
        // alg: 3 = -7 (ES256)  → CBOR negative = 0x20 | ((-7) - 1 negated) = 0x26
        out.write(0x03)              // uint(3) - alg
        out.write(0x26)              // negative int -7 (0x20 | 6)
        // crv: -1 = 1 (P-256)  → key -1 = 0x20 | 0 = 0x20
        out.write(0x20)              // negative int -1 (key crv)
        out.write(0x01)              // uint(1) - P-256
        // x: -2 as key, then 32-byte bstr
        out.write(0x21)              // negative int -2 (key x)
        out.write(0x58); out.write(32)  // bstr(32)
        out.write(x)
        // y: -3 as key, then 32-byte bstr
        out.write(0x22)              // negative int -3 (key y)
        out.write(0x58); out.write(32)  // bstr(32)
        out.write(y)
        return out.toByteArray()
    }

    /**
     * Encodes an ML-DSA-65 public key as a CBOR COSE_Key map.
     * Based on draft-ietf-cose-dilithium, using AKP (Algorithm Key Pair).
     *   COSE_KEY_KTY (1) = COSE_KEY_TYPE_AKP (5)
     *   COSE_KEY_ALG (3) = COSE_ALG_ML_DSA_65 (-49)
     *   COSE_KEY_PUB (-1) = bytes
     * 
     * However, since CTAP2 clients might not fully parse AKP yet, we provide what's
     * defined in the COSE extensions or FIDO parameters.
     * We'll use:
     * kty(1) = 5 (AKP/OKP)
     * alg(3) = -49
     * -1 = publicKeyBytes
     */
    fun encodeCoseMlDsaPublicKey(publicKeyBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // CBOR map with 3 entries
        out.write(0xA3)              // map(3)
        // kty: 1
        out.write(0x01)              // uint(1) - kty
        out.write(0x05)              // uint(5) - AKP or OKP 
        // alg: 3
        out.write(0x03)              // uint(3) - alg
        // -49 = 0x38 0x30 (negative 48)
        out.write(0x38)              // negative int
        out.write(0x30)
        
        // key parameter: -1 (pub)
        out.write(0x20)              // negative int -1
        // write byte string for public key
        val bstrHeader = ByteArrayOutputStream()
        writeHeader(bstrHeader, 2, publicKeyBytes.size.toLong())
        out.write(bstrHeader.toByteArray())
        out.write(publicKeyBytes)
        return out.toByteArray()
    }

    /**
     * Encodes an Ed25519 public key as a CBOR COSE_Key map.
     * kty(1) = 1 (OKP)
     * alg(3) = -19 (Ed25519)
     * crv(-1) = 6 (Ed25519)
     * x(-2) = publicKeyBytes (32 bytes)
     */
    fun encodeCoseEd25519PublicKey(publicKeyBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // CBOR map with 4 entries
        out.write(0xA4)              // map(4)
        
        // kty: 1
        out.write(0x01)              // uint(1) - kty
        out.write(0x01)              // uint(1) - OKP
        
        // alg: 3
        out.write(0x03)              // uint(3) - alg
        out.write(0x32)              // negative int -19 (0x20 | 18)
        
        // crv: -1
        out.write(0x20)              // negative int -1
        out.write(0x06)              // uint(6) - Ed25519 curve
        
        // x: -2
        out.write(0x21)              // negative int -2
        out.write(0x58); out.write(32) // bstr(32)
        out.write(publicKeyBytes)
        
        return out.toByteArray()
    }

    /**
     * Convenience wrapper: encodes a Java [java.security.PublicKey] (EC P-256 or ML-DSA-65)
     * to a CBOR COSE_Key map.
     * Extracts the uncompressed point from the SubjectPublicKeyInfo DER encoding.
     */
    fun encodeCosePublicKeyFromJavaKey(publicKey: java.security.PublicKey): ByteArray {
        val derEncoded = publicKey.encoded   // SubjectPublicKeyInfo DER
        val algorithm = publicKey.algorithm

        if (algorithm == "ML-DSA" || algorithm == "ML-DSA-65" || algorithm == "Dilithium") {
            // ML-DSA public key bytes are the raw bytes or DER. we usually just use the encoded value
            return encodeCoseMlDsaPublicKey(derEncoded)
        }

        if (algorithm == "Ed25519" || algorithm == "EdDSA") {
            // Extract the last 32 bytes as the raw public key
            val rawKey = derEncoded.copyOfRange(derEncoded.size - 32, derEncoded.size)
            return encodeCoseEd25519PublicKey(rawKey)
        }

        // Last 65 bytes of P-256 SubjectPublicKeyInfo = 0x04 || X || Y
        return if (derEncoded.size >= 65 && derEncoded[derEncoded.size - 65] == 0x04.toByte()) {
            val uncompressed = derEncoded.copyOfRange(derEncoded.size - 65, derEncoded.size)
            encodeCosePublicKeyFromUncompressed(uncompressed)
        } else {
            throw IllegalArgumentException(
                "Cannot extract uncompressed EC point from key encoding (algo=$algorithm, size=${derEncoded.size})"
            )
        }
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
            1 -> Pair(-argument - 1L, pos)            // negative integer
            2 -> {                                              // byte string
                val len = argument.toInt()
                val bytes = data.copyOfRange(pos, pos + len)
                Pair(bytes, pos + len)
            }
            3 -> {                                              // text string
                val len = argument.toInt()
                val str = String(data, pos, len, Charsets.UTF_8)
                Pair(str, pos + len)
            }
            4 -> {                                              // array
                val count = argument.toInt()
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
                val count = argument.toInt()
                val map = mutableMapOf<String, Any>()
                var cur = pos
                repeat(count) {
                    val (k, kNext) = decodeItem(data, cur)
                    val (v, vNext) = decodeItem(data, kNext)
                    // Normalize key to String
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
            is Long      -> if (value >= 0) writeUInt(out, value) else writeNegInt(out, value)
            is Int       -> writeValue(out, value.toLong())
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

    /** CBOR major-type 1: negative integer. Encodes N as -(N+1), e.g. -7 → argument=6 → 0x26. */
    private fun writeNegInt(out: ByteArrayOutputStream, value: Long) = writeHeader(out, 1, -(value + 1))

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
