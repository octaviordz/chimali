package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.util.crypto.BouncyCastleLoader
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Reproduces and validates the WebAuthn "Invalid key type" / "byte string too long" /
 * "Invalid data" bugs triggered against https://demo.yubico.com/webauthn-developers.
 *
 * Root causes fixed:
 *  1. kty was 5 (OKP) — must be 7 (AKP) per IANA COSE Key Types registry.
 *  2. pub (-1) was the full DER SubjectPublicKeyInfo (~1988 bytes) — must be the
 *     raw 1952-byte key per NIST FIPS 204 §5.
 *  3. "Invalid data" — the packed attestation was signed with ES256 instead of ML-DSA.
 *  4. End-to-end sign/verify: the key derived for signing must match the key in authData.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class CborCodecTest {
    init {
        BouncyCastleLoader.ensureRegistered()
    }

    private val codec = CborCodec()
    private val pqCrypto = PostQuantumCrypto()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `sensitive credential text encodes as the same CBOR text string without a String adapter`() {
        val value = SensitiveCredentialText.copyOf("acct-\\\"雪-🗝")
        try {
            val sensitiveEncoded = codec.encodeToFido2Format(mapOf("6" to mapOf("name" to value)))
            val compatibilityEncoded = codec.encodeToFido2Format(mapOf("6" to mapOf("name" to "acct-\\\"雪-🗝")))

            assertContentEquals(compatibilityEncoded, sensitiveEncoded)
        } finally {
            value.erase()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `sensitive request decoding owns mutable text and clears it at request end`() {
        val marker = "synthetic-account-marker"
        val encoded = codec.encodeToFido2Format(mapOf("2" to mapOf("name" to marker)))
        val decoded = codec.decodeSensitiveRequest(encoded)
        val rp = decoded["2"] as Map<*, *>
        val owner = rp["name"] as SensitiveCredentialText

        assertEquals(marker, owner.toBoundaryString())

        codec.eraseDecodedRequestValues(decoded)

        assertContentEquals(CharArray(marker.length), owner.copyCharacters())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `sensitive decoding erases map key owners and ignores unknown nested text fields`() {
        val owners = mutableListOf<SensitiveCredentialText>()
        val observedCodec = CborCodec().apply { onDecodedTextOwnerForTest = owners::add }
        val encoded =
            observedCodec.encodeToFido2Format(
                mapOf(
                    "2" to
                        mapOf(
                            "name" to "retained-rp-name",
                            "synthetic-unknown-secret-key" to
                                mapOf("nested-unknown-key" to "nested-unknown-value"),
                        ),
                ),
            )

        val decoded = observedCodec.decodeSensitiveRequest(encoded)
        val rp = decoded.getValue("2") as Map<*, *>
        val retainedName = rp["name"] as SensitiveCredentialText

        assertEquals("retained-rp-name", retainedName.toBoundaryString())
        assertEquals(null, rp["synthetic-unknown-secret-key"])
        assertTrue(
            owners
                .filterNot { it === retainedName }
                .all { owner -> owner.copyCharacters().all { it == '\u0000' } },
        )

        observedCodec.eraseDecodedRequestValues(decoded)

        assertTrue(owners.all { owner -> owner.copyCharacters().all { it == '\u0000' } })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `decoder rejects trailing truncated oversized deeply nested and malformed UTF-8 input`() {
        val trailing = byteArrayOf(0xA0.toByte(), 0x00)
        val truncatedByteString = byteArrayOf(0x58, 0x04, 0x01)
        val oversizedArray = byteArrayOf(0x9A.toByte(), 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val tooDeep = ByteArray(66) { 0x81.toByte() } + byteArrayOf(0xF6.toByte())
        val malformedUtf8 = byteArrayOf(0x61, 0x80.toByte())

        listOf(trailing, truncatedByteString, oversizedArray, tooDeep, malformedUtf8).forEach { malformed ->
            assertEquals(emptyMap<String, Any>(), codec.decodeFromFido2Format(malformed))
        }
        assertEquals(
            emptyMap<String, Any>(),
            codec.decodeFromFido2Format(byteArrayOf(0xA2.toByte(), 0x01, 0xF6.toByte(), 0x01, 0xF6.toByte())),
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `decoder preserves explicit null and undefined map values`() {
        val decoded = codec.decodeFromFido2Format(byteArrayOf(0xA2.toByte(), 0x01, 0xF6.toByte(), 0x02, 0xF7.toByte()))

        assertEquals(CborCodec.SimpleValue.NULL, decoded["1"])
        assertEquals(CborCodec.SimpleValue.UNDEFINED, decoded["2"])
        assertEquals(
            decoded,
            codec.decodeFromFido2Format(codec.encodeToFido2Format(decoded)),
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CBOR malformed trailing structure clears byte strings decoded before failure`() {
        val retained = mutableListOf<ByteArray>()
        val observedCodec = CborCodec().apply { onDecodedByteStringForTest = retained::add }
        val malformed = byteArrayOf(0xA2.toByte(), 0x01, 0x42, 0x11, 0x22, 0x02)

        assertEquals(emptyMap<String, Any>(), observedCodec.decodeFromFido2Format(malformed))
        assertEquals(1, retained.size)
        assertContentEquals(ByteArray(2), retained.single())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CBOR map encoding is canonical and preserves negative integer key type`() {
        val first = linkedMapOf("10" to "ten", "-1" to "negative", "1" to "one")
        val second = linkedMapOf("1" to "one", "10" to "ten", "-1" to "negative")

        val encoded = codec.encodeToFido2Format(first)

        assertContentEquals(encoded, codec.encodeToFido2Format(second))
        assertContentEquals(
            byteArrayOf(
                0xA3.toByte(),
                0x01,
                0x63,
                0x6F,
                0x6E,
                0x65,
                0x0A,
                0x63,
                0x74,
                0x65,
                0x6E,
                0x20,
                0x68,
                0x6E,
                0x65,
                0x67,
                0x61,
                0x74,
                0x69,
                0x76,
                0x65,
            ),
            encoded,
        )
        assertFailsWith<IllegalArgumentException> {
            codec.encodeToFido2Format(linkedMapOf("1" to "one", "01" to "alias"))
        }
        val decoded = codec.decodeFromFido2Format(encoded)
        assertEquals("negative", decoded["-1"])
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CBOR text encoding erases temporary UTF-8 byte arrays`() {
        val erasedTextBuffers = mutableListOf<ByteArray>()
        val observedCodec = CborCodec().apply { onTemporaryTextBytesErasedForTest = erasedTextBuffers::add }

        observedCodec.encodeToFido2Format(mapOf("synthetic-key" to "synthetic-sensitive-value-雪"))

        assertEquals(2, erasedTextBuffers.size)
        assertTrue(erasedTextBuffers.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CBOR encoding erases every replaced plaintext buffer and preserves the returned response`() {
        val erasedBuffers = mutableListOf<ByteArray>()
        val observedCodec = CborCodec().apply { onIntermediateBufferErasedForTest = erasedBuffers::add }
        val ascii = "account-".repeat(96)
        val escaped = "escaped-\\\"-".repeat(96)
        val unicode = "雪-🗝-".repeat(96)
        val sensitive = SensitiveCredentialText.copyOf(unicode)

        try {
            val encoded =
                observedCodec.encodeToFido2Format(
                    linkedMapOf(
                        "ascii" to ascii,
                        "escaped" to escaped,
                        "unicode" to sensitive,
                    ),
                )

            assertTrue(erasedBuffers.size >= 2, "fixture must force multiple backing-buffer replacements")
            assertTrue(erasedBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
            assertTrue(encoded.any { it != 0.toByte() }, "returned response is independently owned and remains intact")
            val decoded = observedCodec.decodeFromFido2Format(encoded)
            assertEquals(ascii, decoded["ascii"])
            assertEquals(escaped, decoded["escaped"])
            assertEquals(unicode, decoded["unicode"])
        } finally {
            sensitive.erase()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CBOR encoding failure erases grown plaintext buffers before propagating`() {
        val erasedBuffers = mutableListOf<ByteArray>()
        val observedCodec = CborCodec().apply { onIntermediateBufferErasedForTest = erasedBuffers::add }
        val value = SensitiveCredentialText.copyOf("敏感-account-🗝-".repeat(256))
        val values =
            object : AbstractList<Any>() {
                override val size: Int = 2

                override fun get(index: Int): Any =
                    when (index) {
                        0 -> value
                        else -> error("synthetic later-value encoding failure")
                    }
            }

        try {
            assertFailsWith<IllegalStateException> {
                observedCodec.encodeToFido2Format(mapOf("account" to values))
            }
            assertTrue(erasedBuffers.size >= 2, "failure fixture must force multiple backing-buffer replacements")
            assertTrue(erasedBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
        } finally {
            value.erase()
        }
    }

    private companion object {
        private const val COSE_KTY_AKP = 7L
        private const val COSE_ALG_ML_DSA_65 = -49L
        private const val ML_DSA_RAW_KEY_SIZE = 1952
        private const val SEED_SIZE_32 = 32
        private const val DUMMY_DATA_SIZE = 32
        private const val DUMMY_BYTE_11 = 0x11.toByte()
        private const val DUMMY_BYTE_42 = 0x42.toByte()
        private const val DUMMY_BYTE_55 = 0x55.toByte()
        private const val DUMMY_BYTE_03 = 0x03.toByte()
        private const val DUMMY_BYTE_04 = 0x04.toByte()
        private const val DUMMY_BYTE_05 = 0x05.toByte()
        private const val DUMMY_BYTE_06 = 0x06.toByte()
        private const val DUMMY_CID_1 = 0x01.toByte()
        private const val DUMMY_CID_2 = 0x02.toByte()
    }

    // ── kty / alg encoding ────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `encodeCoseMlDsaPublicKey produces kty=7 AKP and alg=-49`() {
        val dummyPubKey = ByteArray(DUMMY_DATA_SIZE) { DUMMY_BYTE_11 }
        val cbor = codec.encodeCoseMlDsaPublicKey(dummyPubKey)
        val map = codec.decodeFromFido2Format(cbor)

        val kty = (map["1"] as? Number)?.toLong()
        val alg = (map["3"] as? Number)?.toLong()
        val pub = map["-1"] as? ByteArray

        assertEquals(COSE_KTY_AKP, kty, "kty must be 7 (AKP) per IANA COSE Key Types")
        assertEquals(COSE_ALG_ML_DSA_65, alg, "alg must be -49 (ML-DSA-65) per IANA COSE Algorithms")
        assertContentEquals(dummyPubKey, pub, "pub (-1) must round-trip correctly")
    }

    // ── DER stripping ─────────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `encodeCosePublicKeyFromJavaKey strips DER header and returns raw 1952 bytes`() {
        val seed = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_42 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair, "ML-DSA key pair must be generated")

        val derEncodedKey = keyPair!!.public.encoded // ~1988-byte SubjectPublicKeyInfo DER
        val coseBytes = codec.encodeCosePublicKeyFromJavaKey(keyPair.public)
        val map = codec.decodeFromFido2Format(coseBytes)

        val pub = map["-1"] as? ByteArray
        val kty = map["1"] as? Long
        val alg = map["3"] as? Long

        assertEquals(COSE_KTY_AKP, kty, "kty must be 7 (AKP)")
        assertEquals(COSE_ALG_ML_DSA_65, alg, "alg must be -49 (ML-DSA-65)")
        assertNotNull(pub, "pub (-1) must be present")
        assertEquals(
            ML_DSA_RAW_KEY_SIZE,
            pub!!.size,
            "pub (-1) must be the raw 1952-byte ML-DSA-65 key per FIPS 204, " +
                "NOT the ${derEncodedKey.size}-byte DER SubjectPublicKeyInfo (causes 'byte string too long')",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `encodeCosePublicKeyFromJavaKey pub matches raw bytes from SubjectPublicKeyInfo`() {
        val seed = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_55 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)!!

        val spki = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val expectedRawBytes = spki.publicKeyData.bytes

        val coseBytes = codec.encodeCosePublicKeyFromJavaKey(keyPair.public)
        val map = codec.decodeFromFido2Format(coseBytes)
        val actualPub = map["-1"] as? ByteArray

        assertNotNull(actualPub)
        assertContentEquals(
            expectedRawBytes,
            actualPub,
            "pub (-1) must exactly match the raw key extracted from SubjectPublicKeyInfo",
        )
    }

    /**
     * CRITICAL regression for "Invalid data":
     * Registration stores the public key from derivation #1.
     * Signing re-derives the key pair (derivation #2) — they MUST be identical.
     * BC 1.80 imports the same 32-byte FIPS 204 xi directly for each key reconstruction.
     * @see NFR-SEC-041 and TR-AUTH-031
     */
    @Test
    fun `generateMlDsaKeyPair is deterministic - same seed produces same key pair`() {
        val seed = ByteArray(SEED_SIZE_32) { 0xAB.toByte() }

        val keyPair1 = pqCrypto.generateMlDsaKeyPair(seed)!!
        val keyPair2 = pqCrypto.generateMlDsaKeyPair(seed)!!

        val rawPub1 = SubjectPublicKeyInfo.getInstance(keyPair1.public.encoded).publicKeyData.bytes
        val rawPub2 = SubjectPublicKeyInfo.getInstance(keyPair2.public.encoded).publicKeyData.bytes

        assertContentEquals(
            rawPub1,
            rawPub2,
            "CRITICAL: same seed must always produce the same public key. " +
                "Non-determinism causes 'Invalid data' on the server.",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `ML-DSA sign with re-derived key verifies against original public key`() {
        val seed = ByteArray(SEED_SIZE_32) { 0xAB.toByte() }

        // Registration: derive key pair, store public key
        val keyPair1 = pqCrypto.generateMlDsaKeyPair(seed)!!

        // Signing: re-derive from same seed (as Fido2CryptoService.sign does)
        val keyPair2 = pqCrypto.generateMlDsaKeyPair(seed)!!

        val dataToSign =
            byteArrayOf(
                DUMMY_CID_1,
                DUMMY_CID_2,
                DUMMY_BYTE_03,
                DUMMY_BYTE_04,
                DUMMY_BYTE_05,
                DUMMY_BYTE_06,
            )
        val sig = pqCrypto.sign(keyPair2.private, dataToSign)
        assertNotNull(sig, "ML-DSA signing must succeed")

        // Verify using the public key from derivation #1 (as the server would)
        val verified = pqCrypto.verify(keyPair1.public, dataToSign, sig!!)
        assertTrue(
            verified,
            "Signature made with re-derived keyPair2 must verify against keyPair1.public. " +
                "Failure here causes 'Invalid data' on the server.",
        )
    }

    // ── Server-side round-trip: COSE pub → reconstruct → verify ──────────────

    /**
     * Mirrors exactly what a WebAuthn server does:
     *  1. Extract raw pub bytes from the COSE key map.
     *  2. Reconstruct a SubjectPublicKeyInfo DER using BouncyCastle ASN.1 builders.
     *  3. Verify the signature produced by our authenticator.
     *
     * If this fails, "Invalid data" is caused by the server being unable to verify
     * our ML-DSA signature even after the COSE encoding is correct.
     * @see NFR-SEC-041 and TR-AUTH-031
     */
    @Test
    fun `server side round-trip - reconstruct ML-DSA key from COSE pub and verify signature`() {
        val seed = ByteArray(SEED_SIZE_32) { 0xCD.toByte() }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)!!

        // 1. COSE-encode the public key (what we embed in authData)
        val coseBytes = codec.encodeCosePublicKeyFromJavaKey(keyPair.public)
        val coseMap = codec.decodeFromFido2Format(coseBytes)

        // 2. Extract raw pub from COSE (as a server would parse kty=7 / alg=-49 / pub=-1)
        val rawPub = coseMap["-1"] as? ByteArray
        assertNotNull(rawPub)
        assertEquals(ML_DSA_RAW_KEY_SIZE, rawPub!!.size, "COSE pub must be exactly 1952 bytes for ML-DSA-65")

        // 3. Reconstruct SubjectPublicKeyInfo using BouncyCastle ASN.1 builders.
        //    The server re-wraps the raw bytes with the ML-DSA-65 OID
        //    (2.16.840.1.101.3.4.3.18) to get a standard Java PublicKey.
        val reconstructedSpki = rebuildSpkiFromRawMlDsa65Key(rawPub)

        val kf = KeyFactory.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        val reconstructedPub = kf.generatePublic(X509EncodedKeySpec(reconstructedSpki))

        // 4. Sign with our private key (as the authenticator does in packed attestation)
        val dataToSign = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val signature = pqCrypto.sign(keyPair.private, dataToSign)
        assertNotNull(signature)

        // 5. Verify with the reconstructed public key (as the server would)
        val sigObj = Signature.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        sigObj.initVerify(reconstructedPub)
        sigObj.update(dataToSign)
        val verified = sigObj.verify(signature!!)
        assertTrue(
            verified,
            "Server-side verification must succeed: reconstruct ML-DSA key from raw COSE pub " +
                "and verify our signature. A failure proves the 'Invalid data' root cause.",
        )
    }

    /**
     * Reconstructs a SubjectPublicKeyInfo DER from a raw ML-DSA-65 public key (1952 bytes).
     * Uses BouncyCastle ASN.1 to avoid manual byte-stuffing for large DER lengths.
     */
    private fun rebuildSpkiFromRawMlDsa65Key(rawKey: ByteArray): ByteArray {
        // Re-use BouncyCastle's own SPKI builder: parse the original SPKI to get the
        // AlgorithmIdentifier, then rebuild with the new key bytes.
        // The simplest approach: generate a fresh key pair and swap the public key bytes.
        val tempSeed = ByteArray(SEED_SIZE_32) { 0x01 }
        val tempKeyPair = pqCrypto.generateMlDsaKeyPair(tempSeed)!!
        val tempSpki = SubjectPublicKeyInfo.getInstance(tempKeyPair.public.encoded)

        // Replace the bitString payload in the SPKI with our rawKey
        val newSpki = SubjectPublicKeyInfo(tempSpki.algorithm, rawKey)
        return newSpki.encoded
    }
}
