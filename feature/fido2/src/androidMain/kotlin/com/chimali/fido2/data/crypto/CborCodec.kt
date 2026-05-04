package com.chimali.fido2.data.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.koin.core.annotation.Single

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
@Single
class CborCodec {
    companion object {
        // CBOR Major Types
        private const val MAJOR_TYPE_UNSIGNED_INT = 0
        private const val MAJOR_TYPE_NEGATIVE_INT = 1
        private const val MAJOR_TYPE_BYTE_STRING = 2
        private const val MAJOR_TYPE_TEXT_STRING = 3
        private const val MAJOR_TYPE_ARRAY = 4
        private const val MAJOR_TYPE_MAP = 5
        private const val MAJOR_TYPE_SIMPLE = 7

        private const val MAJOR_TYPE_SHIFT = 5
        private const val INFO_MASK = 0x1F

        // CBOR Simple Values
        private const val SIMPLE_FALSE = 20
        private const val SIMPLE_TRUE = 21
        private const val SIMPLE_NULL = 22
        private const val SIMPLE_UNDEFINED = 23

        // CBOR Header Constants
        private const val HEADER_SMALL_LIMIT = 24
        private const val HEADER_UINT8 = 24
        private const val HEADER_UINT16 = 25
        private const val HEADER_UINT32 = 26
        private const val HEADER_UINT64 = 27

        // CTAP2/COSE Constants
        private const val COSE_MAP_SIZE_3 = 0xA3
        private const val COSE_MAP_SIZE_4 = 0xA4
        private const val COSE_MAP_SIZE_5 = 0xA5

        private const val BSTR_HEADER_32 = 0x58
        private const val SIZE_32 = 32
        private const val SIZE_65 = 65

        // ML-DSA-65 raw public key size per NIST FIPS 204 §5
        private const val ML_DSA_65_RAW_KEY_SIZE = 1952

        private const val UNCOMPRESSED_EC_PREFIX = 0x04.toByte()

        // CBOR Negative Int Helper (-7 -> 0x26)
        private const val NEG_INT_7 = 0x26
        private const val NEG_INT_8 = 0x27
        private const val NEG_INT_1 = 0x20
        private const val NEG_INT_2 = 0x21
        private const val NEG_INT_3 = 0x22
        private const val NEG_INT_49_LEAD = 0x38
        private const val NEG_INT_49_TAIL = 0x30

        private const val BYTE_SHIFT_8 = 8
        private const val MASK_8_BITS = 0xFF

        private const val COSE_KTY_AKP = 0x07
        private const val COSE_CRV_ED25519 = 0x06

        private const val UINT8_MAX = 0xFFL
        private const val UINT16_MAX = 0xFFFFL
        private const val UINT32_MAX = 0xFFFFFFFFL

        private const val OFFSET_X = 1
        private const val OFFSET_Y = 33

        private const val COSE_KEY_KTY = 0x01
        private const val COSE_KEY_ALG = 0x03
        private const val COSE_KTY_EC2 = 0x02
        private const val COSE_KTY_OKP = 0x01
        private const val COSE_CRV_P256 = 0x01
    }

    // ── Decoding ──────────────────────────────────────────────────────────────

    /**
     * Decode a CTAP2 binary-CBOR byte array into a Kotlin Map<String, Any>.
     * Integer CBOR keys are converted to string representations to remain
     * compatible with handler code that refers to map["1"], map["2"], etc.
     */
    fun decodeFromFido2Format(data: ByteArray): Map<String, Any> =
        try {
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
        require(uncompressedPoint.size == SIZE_65 && uncompressedPoint[0] == UNCOMPRESSED_EC_PREFIX) {
            "Expected $SIZE_65-byte uncompressed point starting with $UNCOMPRESSED_EC_PREFIX, " +
                "got ${uncompressedPoint.size} bytes"
        }
        val x = uncompressedPoint.copyOfRange(OFFSET_X, OFFSET_Y)
        val y = uncompressedPoint.copyOfRange(OFFSET_Y, SIZE_65)
        val out = ByteArrayOutputStream()
        // CBOR map with 5 entries
        out.write(COSE_MAP_SIZE_5) // map(5)

        // kty: 1 = 2 (EC2)
        out.write(COSE_KEY_KTY) // uint(1) - kty
        out.write(COSE_KTY_EC2) // uint(2) - EC2
        // alg: 3 = -7 (ES256)  → CBOR negative = 0x20 | ((-7) - 1 negated) = 0x26
        out.write(COSE_KEY_ALG) // uint(3) - alg
        out.write(NEG_INT_7) // negative int -7 (0x20 | 6)
        // crv: -1 = 1 (P-256)  → key -1 = 0x20 | 0 = 0x20
        out.write(NEG_INT_1) // negative int -1 (key crv)
        out.write(COSE_CRV_P256) // uint(1) - P-256

        // x: -2 as key, then 32-byte bstr
        out.write(NEG_INT_2) // negative int -2 (key x)
        out.write(BSTR_HEADER_32)
        out.write(SIZE_32) // bstr(32)
        out.write(x)

        // y: -3 as key, then 32-byte bstr
        out.write(NEG_INT_3) // negative int -3 (key y)
        out.write(BSTR_HEADER_32)
        out.write(SIZE_32) // bstr(32)
        out.write(y)

        return out.toByteArray()
    }

    /**
     * Encodes an ML-DSA-65 public key as a CBOR COSE_Key map.
     * Based on draft-ietf-cose-dilithium / IANA COSE registry, using AKP key type.
     *
     *   kty  (1)  = 7   (AKP — Algorithm Key Pair)
     *   alg  (3)  = -49 (ML-DSA-65, per IANA COSE Algorithms registry)
     *   pub  (-1) = 1952 raw bytes (NIST FIPS 204 §5 — NOT DER/SubjectPublicKeyInfo)
     *
     * @param publicKeyBytes Raw 1952-byte ML-DSA-65 public key (no DER wrapper).
     */
    fun encodeCoseMlDsaPublicKey(publicKeyBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // CBOR map with 3 entries
        out.write(COSE_MAP_SIZE_3) // map(3)

        // kty: 1 = 7 (AKP)
        out.write(COSE_KEY_KTY) // uint(1) - kty
        out.write(COSE_KTY_AKP) // uint(7) - AKP

        // alg: 3 = -49
        out.write(COSE_KEY_ALG) // uint(3) - alg
        // -49 = 0x38 0x30 (negative 48)
        out.write(NEG_INT_49_LEAD) // negative int
        out.write(NEG_INT_49_TAIL)

        // key parameter: -1 (pub)
        out.write(NEG_INT_1) // negative int -1

        // write byte string for public key
        val bstrHeader = ByteArrayOutputStream()
        writeHeader(bstrHeader, MAJOR_TYPE_BYTE_STRING, publicKeyBytes.size.toLong())
        out.write(bstrHeader.toByteArray())
        out.write(publicKeyBytes)

        return out.toByteArray()
    }

    /**
     * Encodes an Ed25519 public key as a CBOR COSE_Key map.
     * kty(1) = 1 (OKP)
     * alg(3) = -8 (EdDSA / Ed25519, per WebAuthn L3 §5.4)
     * crv(-1) = 6 (Ed25519)
     * x(-2) = publicKeyBytes (32 bytes)
     */
    fun encodeCoseEd25519PublicKey(publicKeyBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // CBOR map with 4 entries
        out.write(COSE_MAP_SIZE_4) // map(4)

        // kty: 1 = 1 (OKP)
        out.write(COSE_KEY_KTY) // uint(1) - kty
        out.write(COSE_KTY_OKP) // uint(1) - OKP

        // alg: 3 = -8
        out.write(COSE_KEY_ALG) // uint(3) - alg
        out.write(NEG_INT_8) // negative int -8 (0x20 | 7)

        // crv: -1 = 6 (Ed25519)
        out.write(NEG_INT_1) // negative int -1
        out.write(COSE_CRV_ED25519) // uint(6) - Ed25519 curve

        // x: -2
        out.write(NEG_INT_2) // negative int -2
        out.write(BSTR_HEADER_32)
        out.write(SIZE_32) // bstr(32)
        out.write(publicKeyBytes)

        return out.toByteArray()
    }

    /**
     * Convenience wrapper for Java PublicKeys.
     * Encodes a Java [java.security.PublicKey] (EC P-256 or ML-DSA-65)
     * to a CBOR COSE_Key map.
     * Extracts the uncompressed point from the SubjectPublicKeyInfo DER encoding.
     */
    fun encodeCosePublicKeyFromJavaKey(publicKey: java.security.PublicKey): ByteArray {
        val derEncoded = publicKey.encoded // SubjectPublicKeyInfo DER
        val algorithm = publicKey.algorithm

        if (algorithm == "ML-DSA" || algorithm == "ML-DSA-65" || algorithm == "Dilithium") {
            // Strip the SubjectPublicKeyInfo DER wrapper — COSE pub (-1) must be the
            // raw 1952-byte key body, NOT the DER-encoded SubjectPublicKeyInfo (~1988 bytes).
            // Sending the full DER causes "byte string too long" on WebAuthn servers.
            val spki = SubjectPublicKeyInfo.getInstance(derEncoded)
            val rawKeyBytes = spki.publicKeyData.bytes
            require(rawKeyBytes.size == ML_DSA_65_RAW_KEY_SIZE) {
                "Expected $ML_DSA_65_RAW_KEY_SIZE-byte raw ML-DSA-65 key, got ${rawKeyBytes.size} bytes"
            }
            return encodeCoseMlDsaPublicKey(rawKeyBytes)
        }

        if (algorithm == "Ed25519" || algorithm == "EdDSA") {
            // Extract the last 32 bytes as the raw public key
            val rawKey = derEncoded.copyOfRange(derEncoded.size - SIZE_32, derEncoded.size)
            return encodeCoseEd25519PublicKey(rawKey)
        }

        // Last 65 bytes of P-256 SubjectPublicKeyInfo = 0x04 || X || Y
        require(derEncoded.size >= SIZE_65 && derEncoded[derEncoded.size - SIZE_65] == UNCOMPRESSED_EC_PREFIX) {
            "Cannot extract uncompressed EC point from key encoding (algo=$algorithm, size=${derEncoded.size})"
        }

        val uncompressed = derEncoded.copyOfRange(derEncoded.size - SIZE_65, derEncoded.size)
        return encodeCosePublicKeyFromUncompressed(uncompressed)
    }

    // ── Recursive decoder ─────────────────────────────────────────────────────

    /** Returns (decodedValue, nextOffset). */
    private fun decodeItem(
        data: ByteArray,
        offset: Int,
    ): Pair<Any?, Int> {
        val initial = data[offset].toInt() and MASK_8_BITS
        val major = initial shr MAJOR_TYPE_SHIFT
        val info = initial and INFO_MASK
        var pos = offset + 1

        val (argument, newPos) = decodeArgument(data, pos, info)
        pos = newPos

        return when (major) {
            MAJOR_TYPE_UNSIGNED_INT -> Pair(argument, pos) // unsigned integer
            MAJOR_TYPE_NEGATIVE_INT -> Pair(-argument - 1L, pos) // negative integer
            MAJOR_TYPE_BYTE_STRING -> { // byte string
                val len = argument.toInt()
                val bytes = data.copyOfRange(pos, pos + len)
                Pair(bytes, pos + len)
            }

            MAJOR_TYPE_TEXT_STRING -> { // text string
                val len = argument.toInt()
                val str = String(data, pos, len, Charsets.UTF_8)
                Pair(str, pos + len)
            }

            MAJOR_TYPE_ARRAY -> { // array
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

            MAJOR_TYPE_MAP -> { // map
                val count = argument.toInt()
                val map = mutableMapOf<String, Any>()
                var cur = pos
                repeat(count) {
                    val (k, kNext) = decodeItem(data, cur)
                    val (v, vNext) = decodeItem(data, kNext)
                    // Normalize key to String
                    val keyStr =
                        when (k) {
                            is Long -> k.toString()
                            is String -> k
                            else -> k.toString()
                        }
                    if (v != null) map[keyStr] = v
                    cur = vNext
                }
                Pair(map, cur)
            }

            MAJOR_TYPE_SIMPLE -> { // float/simple/break
                val value: Any? =
                    when (info) {
                        SIMPLE_FALSE -> false
                        SIMPLE_TRUE -> true
                        SIMPLE_NULL -> null
                        SIMPLE_UNDEFINED -> null
                        else -> argument // half/single/double — treat as Long
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
    private fun decodeArgument(
        data: ByteArray,
        offset: Int,
        info: Int,
    ): Pair<Long, Int> {
        return when {
            info < HEADER_SMALL_LIMIT -> Pair(info.toLong(), offset)
            info == HEADER_UINT8 -> Pair((data[offset].toInt() and MASK_8_BITS).toLong(), offset + 1)
            info == HEADER_UINT16 -> {
                val v =
                    ((data[offset].toInt() and MASK_8_BITS) shl BYTE_SHIFT_8) or
                        (data[offset + 1].toInt() and MASK_8_BITS)
                Pair(v.toLong(), offset + 2)
            }

            info == HEADER_UINT32 -> {
                var v = 0L
                for (i in 0..3) v = (v shl BYTE_SHIFT_8) or (data[offset + i].toInt() and MASK_8_BITS).toLong()
                Pair(v, offset + 4)
            }

            info == HEADER_UINT64 -> {
                var v = 0L
                for (i in 0..7) v = (v shl BYTE_SHIFT_8) or (data[offset + i].toInt() and MASK_8_BITS).toLong()
                Pair(v, offset + 8)
            }

            else -> Pair(0L, offset)
        }
    }

    // ── Recursive encoder ─────────────────────────────────────────────────────

    private fun writeValue(
        out: ByteArrayOutputStream,
        value: Any?,
    ) {
        when (value) {
            is Boolean -> writeByte(out, if (value) 0xF5.toByte() else 0xF4.toByte())
            null -> writeByte(out, 0xF6.toByte())
            is ByteArray -> {
                writeHeader(out, MAJOR_TYPE_BYTE_STRING, value.size.toLong())
                out.write(value)
            }

            is String -> {
                val b = value.toByteArray(Charsets.UTF_8)
                writeHeader(
                    out,
                    MAJOR_TYPE_TEXT_STRING,
                    b.size.toLong(),
                )
                out.write(b)
            }

            is Long -> if (value >= 0) writeUInt(out, value) else writeNegInt(out, value)
            is Int -> writeValue(out, value.toLong())
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(out, value as Map<String, Any>)
            }

            is List<*> -> {
                writeHeader(out, MAJOR_TYPE_ARRAY, value.size.toLong())
                value.forEach { writeValue(out, it) }
            }

            else -> {
                val str = value.toString()
                val b = str.toByteArray(Charsets.UTF_8)
                writeHeader(out, MAJOR_TYPE_TEXT_STRING, b.size.toLong())
                out.write(b)
            }
        }
    }

    /** CBOR major-type 1: negative integer. Encodes N as -(N+1), e.g. -7 → argument=6 → 0x26. */
    private fun writeNegInt(
        out: ByteArrayOutputStream,
        value: Long,
    ) = writeHeader(out, 1, -(value + 1))

    private fun writeMap(
        out: ByteArrayOutputStream,
        map: Map<String, Any>,
    ) {
        writeHeader(out, MAJOR_TYPE_MAP, map.size.toLong())
        for ((k, v) in map) {
            val asLong = k.toLongOrNull()
            if (asLong != null) writeUInt(out, asLong) else writeValue(out, k)
            writeValue(out, v)
        }
    }

    private fun writeUInt(
        out: ByteArrayOutputStream,
        value: Long,
    ) = writeHeader(out, MAJOR_TYPE_UNSIGNED_INT, value)

    private fun writeHeader(
        out: ByteArrayOutputStream,
        major: Int,
        argument: Long,
    ) {
        val hi = major shl MAJOR_TYPE_SHIFT
        when {
            argument < HEADER_SMALL_LIMIT -> writeByte(out, (hi or argument.toInt()).toByte())
            argument <= UINT8_MAX -> {
                writeByte(out, (hi or HEADER_UINT8).toByte())
                writeByte(out, argument.toByte())
            }

            argument <= UINT16_MAX -> {
                writeByte(out, (hi or HEADER_UINT16).toByte())
                writeShort(out, argument.toInt())
            }

            argument <= UINT32_MAX -> {
                writeByte(out, (hi or HEADER_UINT32).toByte())
                writeInt(out, argument.toInt())
            }

            else -> {
                writeByte(out, (hi or HEADER_UINT64).toByte())
                writeLong(out, argument)
            }
        }
    }

    private fun writeByte(
        out: ByteArrayOutputStream,
        b: Byte,
    ) = out.write(b.toInt())

    private fun writeShort(
        out: ByteArrayOutputStream,
        v: Int,
    ) {
        out.write(v shr BYTE_SHIFT_8)
        out.write(v)
    }

    private fun writeInt(
        out: ByteArrayOutputStream,
        v: Int,
    ) {
        DataOutputStream(out).writeInt(v)
    }

    private fun writeLong(
        out: ByteArrayOutputStream,
        v: Long,
    ) {
        DataOutputStream(out).writeLong(v)
    }

    // ── Legacy helpers ────────────────────────────────────────────────────────

    fun encodeAttestationObject(
        rpIdHash: ByteArray,
        flags: Byte,
        counter: Int,
        aaguid: ByteArray,
        credentialId: ByteArray,
        publicKeyBytes: ByteArray,
    ): ByteArray =
        encodeToFido2Format(
            mapOf(
                "fmt" to "packed",
                "authData" to
                    mapOf(
                        "rpIdHash" to rpIdHash, "flags" to flags.toInt(), "counter" to counter,
                        "aaguid" to aaguid, "credentialId" to credentialId, "publicKey" to publicKeyBytes,
                    ),
            ),
        )

    fun encodeAuthenticatorData(
        rpIdHash: ByteArray,
        flags: Byte,
        counter: Int,
    ): ByteArray = encodeToFido2Format(mapOf("rpIdHash" to rpIdHash, "flags" to flags.toInt(), "counter" to counter))

    fun encodeClientDataJson(
        type: String,
        challenge: ByteArray,
        origin: String,
        crossOrigin: Boolean = false,
    ): ByteArray =
        encodeToFido2Format(
            mapOf(
                "type" to type,
                "challenge" to challenge,
                "origin" to origin,
                "crossOrigin" to crossOrigin,
            ),
        )
}
