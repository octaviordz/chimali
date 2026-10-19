package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.SensitiveCredentialText
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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
 * - Major type 3: text string       → String for compatibility decode; erasable owners for sensitive requests
 * - Major type 4: array             → List<Any>
 * - Major type 5: map               → Map<String, Any>  (int keys → "1","2",…)
 * - Major type 7 / simple:          → Boolean / null
 */
@Single
@Req("NFR-SEC-041", "TR-AUTH-031")
class CborCodec {
    /** Explicit CBOR simple values retained so protocol validation can distinguish them from absent fields. */
    internal enum class SimpleValue {
        NULL,
        UNDEFINED,
    }

    /** Test-only observer for retained intermediate output buffers after erasure. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onIntermediateBufferErasedForTest: ((ByteArray) -> Unit)? = null

    /** Test-only observer for decoded byte-string owners at the ownership transfer boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onDecodedByteStringForTest: ((ByteArray) -> Unit)? = null

    /** Test-only observer for decoded text owners, including map keys erased during parsing. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onDecodedTextOwnerForTest: ((SensitiveCredentialText) -> Unit)? = null

    /** Test-only observer for temporary UTF-8 buffers after they are erased. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onTemporaryTextBytesErasedForTest: ((ByteArray) -> Unit)? = null

    private val encodingEngine =
        CborEncodingEngine(
            onIntermediateBufferErased = { onIntermediateBufferErasedForTest?.invoke(it) },
            onTemporaryTextBytesErased = { onTemporaryTextBytesErasedForTest?.invoke(it) },
        )

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

        // ML-DSA-65 raw public key size per NIST FIPS 204 §5

        // CBOR Negative Int Helper (-7 -> 0x26)

        private const val BYTE_SHIFT_8 = 8
        private const val MASK_8_BITS = 0xFF
        private const val SIZE_UINT32 = 4
        private const val SIZE_UINT64 = 8
        private const val INDEX_3 = 3
        private const val INDEX_7 = 7
        private const val MAX_CBOR_INPUT_BYTES = 1_048_576
        private const val MAX_CBOR_NESTING_DEPTH = 64
        private const val MAX_CBOR_CONTAINER_ITEMS = 16_384

        /** Fixed protocol field names accepted by sensitive CTAP decoding; unknown text keys are discarded. */
        private val SENSITIVE_REQUEST_TEXT_KEYS =
            setOf(
                "rp",
                "user",
                "id",
                "name",
                "displayName",
                "icon",
                "type",
                "alg",
                "pubKeyCredParams",
                "clientDataHash",
                "options",
                "uv",
                "rk",
                "extensions",
                "credProtect",
                "hmac-secret",
                "prf",
                "allowList",
                "allowCredentials",
                "userVerification",
                "largeBlob",
                "largeBlobKey",
                "credBlob",
                "uvm",
                "appid",
                "appidExclude",
                "hmac-secret-mc",
                "credentialBlob",
                "getCredBlob",
            )
    }

    // ── Decoding ──────────────────────────────────────────────────────────────

    /**
     * Decodes bounded CTAP2 binary CBOR into a Kotlin map. Integer keys become strings for handler compatibility;
     * malformed or unsupported input returns an empty map, while byte-string owners transfer only on valid decode.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun decodeFromFido2Format(data: ByteArray): Map<String, Any> = decodeMap(data, sensitiveTextValues = false)

    /** Decodes a CTAP request with mutable owners for text values that must be cleared at request end. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun decodeSensitiveRequest(data: ByteArray): Map<String, Any> = decodeMap(data, sensitiveTextValues = true)

    private fun decodeMap(
        data: ByteArray,
        sensitiveTextValues: Boolean,
    ): Map<String, Any> {
        if (data.isEmpty() || data.size > MAX_CBOR_INPUT_BYTES) return emptyMap()
        val decodedByteStrings = mutableListOf<ByteArray>()
        val decodedTextOwners = mutableListOf<SensitiveCredentialText>()
        return try {
            val (value, nextOffset) =
                decodeItem(data, 0, 0, decodedByteStrings, decodedTextOwners, sensitiveTextValues)
            if (nextOffset != data.size) malformedCbor("Trailing CBOR data")
            @Suppress("UNCHECKED_CAST")
            (value as? Map<String, Any>)
                ?: emptyMap<String, Any>().also {
                    decodedByteStrings.eraseAll()
                    decodedTextOwners.forEach(SensitiveCredentialText::erase)
                }
        } catch (_: MalformedCborException) {
            decodedByteStrings.eraseAll()
            decodedTextOwners.forEach(SensitiveCredentialText::erase)
            emptyMap()
        }
    }

    /** Erases decoded byte and text value owners after the consuming request ends. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseDecodedRequestValues(value: Any?) {
        when (value) {
            is ByteArray -> value.fill(0)
            is SensitiveCredentialText -> value.erase()
            is Map<*, *> -> value.values.forEach(::eraseDecodedRequestValues)
            is Iterable<*> -> value.forEach(::eraseDecodedRequestValues)
        }
    }

    // ── Encoding ──────────────────────────────────────────────────────────────

    /** Encodes a Kotlin map as canonical CTAP2 CBOR and erases intermediate buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeToFido2Format(data: Map<String, Any>): ByteArray = encodingEngine.encodeToFido2Format(data)

    /** Encodes a P-256 public key as a COSE_Key map. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeCosePublicKeyFromUncompressed(uncompressedPoint: ByteArray): ByteArray =
        encodingEngine.encodeCosePublicKeyFromUncompressed(uncompressedPoint)

    /** Encodes a raw ML-DSA-65 public key as a COSE_Key map. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeCoseMlDsaPublicKey(publicKeyBytes: ByteArray): ByteArray =
        encodingEngine.encodeCoseMlDsaPublicKey(publicKeyBytes)

    /** Encodes an Ed25519 public key as a COSE_Key map. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeCoseEd25519PublicKey(publicKeyBytes: ByteArray): ByteArray =
        encodingEngine.encodeCoseEd25519PublicKey(publicKeyBytes)

    /** Converts the supported Java public-key encodings to COSE_Key maps. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeCosePublicKeyFromJavaKey(publicKey: java.security.PublicKey): ByteArray =
        encodingEngine.encodeCosePublicKeyFromJavaKey(publicKey)
    // ── Recursive decoder ─────────────────────────────────────────────────────

    /** Returns (decodedValue, nextOffset). */
    private fun decodeItem(
        data: ByteArray,
        offset: Int,
        depth: Int,
        decodedByteStrings: MutableList<ByteArray>,
        decodedTextOwners: MutableList<SensitiveCredentialText>,
        sensitiveTextValues: Boolean,
    ): Pair<Any?, Int> {
        if (depth > MAX_CBOR_NESTING_DEPTH) malformedCbor("CBOR nesting exceeds supported depth")
        requireAvailable(data, offset, 1)
        val initial = data[offset].toInt() and MASK_8_BITS
        val major = initial shr MAJOR_TYPE_SHIFT
        val info = initial and INFO_MASK
        var pos = offset + 1

        val (argument, newPos) = decodeArgument(data, pos, info)
        pos = newPos

        return when (major) {
            MAJOR_TYPE_UNSIGNED_INT -> {
                if (argument < 0) malformedCbor("CBOR unsigned integer exceeds supported range")
                Pair(argument, pos)
            }
            MAJOR_TYPE_NEGATIVE_INT -> Pair(-argument - 1L, pos) // negative integer
            MAJOR_TYPE_BYTE_STRING -> { // byte string
                val len = checkedLength(data, pos, argument)
                val bytes = data.copyOfRange(pos, pos + len)
                decodedByteStrings += bytes
                onDecodedByteStringForTest?.invoke(bytes)
                Pair(bytes, pos + len)
            }

            MAJOR_TYPE_TEXT_STRING -> { // text string
                val len = checkedLength(data, pos, argument)
                if (sensitiveTextValues) {
                    val owner =
                        try {
                            SensitiveCredentialText.fromUtf8Range(data, pos, len)
                        } catch (_: java.nio.charset.CharacterCodingException) {
                            malformedCbor("Malformed CBOR UTF-8 text")
                        }
                    decodedTextOwners += owner
                    onDecodedTextOwnerForTest?.invoke(owner)
                    return Pair(owner, pos + len)
                }
                val str =
                    try {
                        StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(data, pos, len))
                            .toString()
                    } catch (_: java.nio.charset.CharacterCodingException) {
                        malformedCbor("Malformed CBOR UTF-8 text")
                    }
                Pair(str, pos + len)
            }

            MAJOR_TYPE_ARRAY -> { // array
                val count = checkedContainerCount(data, pos, argument, minimumBytesPerItem = 1)
                val list = ArrayList<Any?>(count)
                var cur = pos
                repeat(count) {
                    val (item, next) =
                        decodeItem(data, cur, depth + 1, decodedByteStrings, decodedTextOwners, sensitiveTextValues)
                    list.add(item)
                    cur = next
                }
                Pair(list, cur)
            }

            MAJOR_TYPE_MAP -> { // map
                val count = checkedContainerCount(data, pos, argument, minimumBytesPerItem = 2)
                val map = LinkedHashMap<String, Any>(count)
                val seenKeys = HashSet<String>(count)
                var cur = pos
                repeat(count) {
                    val (key, kNext) =
                        decodeItem(data, cur, depth + 1, decodedByteStrings, decodedTextOwners, sensitiveTextValues)
                    val (value, vNext) =
                        decodeItem(data, kNext, depth + 1, decodedByteStrings, decodedTextOwners, sensitiveTextValues)
                    val keyString =
                        when (key) {
                            is Long -> key.toString()
                            is String -> key
                            is SensitiveCredentialText ->
                                SENSITIVE_REQUEST_TEXT_KEYS.firstOrNull(key::contentEquals)
                            else -> malformedCbor("Unsupported CBOR map key type")
                        }
                    if (key is SensitiveCredentialText) key.erase()
                    if (keyString != null) {
                        if (!seenKeys.add(keyString)) malformedCbor("Duplicate CBOR map key")
                        map[keyString] = value ?: malformedCbor("CBOR map values must retain explicit null markers")
                    } else {
                        // Unknown text-keyed fields are ignored by CTAP callers. Erase their decoded
                        // values now instead of retaining attacker-controlled text until request end.
                        eraseDecodedRequestValues(value)
                    }
                    cur = vNext
                }
                Pair(map, cur)
            }

            MAJOR_TYPE_SIMPLE -> { // float/simple/break
                val value: Any? =
                    when (info) {
                        SIMPLE_FALSE -> false
                        SIMPLE_TRUE -> true
                        SIMPLE_NULL -> SimpleValue.NULL
                        SIMPLE_UNDEFINED -> SimpleValue.UNDEFINED
                        else -> malformedCbor("Unsupported CBOR simple or floating-point value")
                    }
                Pair(value, pos)
            }

            else -> malformedCbor("Unsupported CBOR major type")
        }
    }

    private fun checkedLength(
        data: ByteArray,
        offset: Int,
        argument: Long,
    ): Int {
        if (argument < 0 || argument > Int.MAX_VALUE) malformedCbor("CBOR string length is out of range")
        val length = argument.toInt()
        requireAvailable(data, offset, length)
        return length
    }

    private fun checkedContainerCount(
        data: ByteArray,
        offset: Int,
        argument: Long,
        minimumBytesPerItem: Int,
    ): Int {
        if (argument < 0 || argument > MAX_CBOR_CONTAINER_ITEMS) {
            malformedCbor("CBOR container item count exceeds supported limit")
        }
        val count = argument.toInt()
        if (count > (data.size - offset) / minimumBytesPerItem) {
            malformedCbor("CBOR container count exceeds remaining input")
        }
        return count
    }

    private fun requireAvailable(
        data: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (offset < 0 || length < 0 || offset > data.size - length) {
            malformedCbor("Truncated CBOR input")
        }
    }

    private fun MutableList<ByteArray>.eraseAll() {
        forEach { it.fill(0) }
        clear()
    }

    /**
     * Decode the CBOR additional-info argument (length / small integer).
     * Returns (valueAsLong, nextOffset).
     */
    private fun decodeArgument(
        data: ByteArray,
        offset: Int,
        info: Int,
    ): Pair<Long, Int> =
        when {
            info < HEADER_SMALL_LIMIT -> Pair(info.toLong(), offset)
            info == HEADER_UINT8 -> {
                requireAvailable(data, offset, 1)
                Pair((data[offset].toInt() and MASK_8_BITS).toLong(), offset + 1)
            }
            info == HEADER_UINT16 -> {
                requireAvailable(data, offset, 2)
                val v =
                    ((data[offset].toInt() and MASK_8_BITS) shl BYTE_SHIFT_8) or
                        (data[offset + 1].toInt() and MASK_8_BITS)
                Pair(v.toLong(), offset + 2)
            }

            info == HEADER_UINT32 -> {
                requireAvailable(data, offset, SIZE_UINT32)
                var v = 0L
                for (i in 0..INDEX_3) v = (v shl BYTE_SHIFT_8) or (data[offset + i].toInt() and MASK_8_BITS).toLong()
                Pair(v, offset + SIZE_UINT32)
            }

            info == HEADER_UINT64 -> {
                requireAvailable(data, offset, SIZE_UINT64)
                var v = 0L
                for (i in 0..INDEX_7) v = (v shl BYTE_SHIFT_8) or (data[offset + i].toInt() and MASK_8_BITS).toLong()
                if (v < 0) malformedCbor("CBOR argument exceeds supported range")
                Pair(v, offset + SIZE_UINT64)
            }

            else -> malformedCbor("Indefinite or reserved CBOR argument is unsupported")
        }

    private class MalformedCborException(
        message: String,
    ) : IllegalArgumentException(message)

    private fun malformedCbor(message: String): Nothing = throw MalformedCborException(message)

    /**
     * Owns the intermediate wire buffer and clears it after copying the returned response bytes.
     *
     * Erases every superseded buffer before allowing the stream to continue growing.
     * All writes reserve capacity here first so ByteArrayOutputStream never replaces
     * a sensitive backing array without this class clearing it.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal class ErasableByteArrayOutputStream(
        private val onBufferErased: ((ByteArray) -> Unit)? = null,
    ) : ByteArrayOutputStream() {
        /** Writes one byte after reserving and erasing replaced sensitive backing storage. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Synchronized
        override fun write(b: Int) {
            ensureCapacityFor(1)
            super.write(b)
        }

        /** Writes a byte range after reserving and erasing replaced sensitive backing storage. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Synchronized
        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            if (offset < 0 || length < 0 || length > bytes.size - offset) {
                throw IndexOutOfBoundsException("Invalid byte range")
            }
            ensureCapacityFor(length)
            super.write(bytes, offset, length)
        }

        /** Erases the current intermediate response buffer and resets its logical length. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Synchronized
        fun erase() {
            eraseBuffer(buf)
            reset()
        }

        private fun ensureCapacityFor(additionalBytes: Int) {
            if (additionalBytes == 0) return
            val requiredCapacity = Math.addExact(count, additionalBytes)
            if (requiredCapacity <= buf.size) return

            val doubledCapacity = if (buf.size <= Int.MAX_VALUE / 2) buf.size * 2 else Int.MAX_VALUE
            val replacement = buf.copyOf(maxOf(requiredCapacity, doubledCapacity))
            val previous = buf
            buf = replacement
            eraseBuffer(previous)
        }

        private fun eraseBuffer(buffer: ByteArray) {
            buffer.fill(0)
            onBufferErased?.invoke(buffer)
        }
    }

    // ── Legacy helpers ────────────────────────────────────────────────────────

    /** Encodes the structured attestation object as canonical CTAP2 CBOR. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
                        "rpIdHash" to rpIdHash,
                        "flags" to flags.toInt(),
                        "counter" to counter,
                        "aaguid" to aaguid,
                        "credentialId" to credentialId,
                        "publicKey" to publicKeyBytes,
                    ),
            ),
        )

    /** Encodes the relying-party hash, flags and counter as CTAP2 CBOR. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun encodeAuthenticatorData(
        rpIdHash: ByteArray,
        flags: Byte,
        counter: Int,
    ): ByteArray = encodeToFido2Format(mapOf("rpIdHash" to rpIdHash, "flags" to flags.toInt(), "counter" to counter))

    /** Encodes client-data fields using the legacy CBOR representation retained by this compatibility helper. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
