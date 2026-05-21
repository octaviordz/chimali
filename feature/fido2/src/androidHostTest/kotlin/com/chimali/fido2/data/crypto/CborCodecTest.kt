package com.chimali.fido2.data.crypto

import com.chimali.fido2.util.crypto.BouncyCastleLoader
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
class CborCodecTest {
    init {
        BouncyCastleLoader.ensureRegistered()
    }

    private val codec = CborCodec()
    private val pqCrypto = PostQuantumCrypto()

    private companion object {
        private const val COSE_KTY_AKP = 7L
        private const val COSE_ALG_ML_DSA_65 = -49L
        private const val ML_DSA_RAW_KEY_SIZE = 1952
        private const val SEED_SIZE_64 = 64
        private const val SEED_SIZE_32 = 32
        private const val BUFFER_SIZE_128 = 128
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
        private const val ENTROPY_999 = 999L
    }

    // ── kty / alg encoding ────────────────────────────────────────────────────

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

    @Test
    fun `encodeCosePublicKeyFromJavaKey strips DER header and returns raw 1952 bytes`() {
        val seed = ByteArray(SEED_SIZE_64) { DUMMY_BYTE_42 }
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

    @Test
    fun `encodeCosePublicKeyFromJavaKey pub matches raw bytes from SubjectPublicKeyInfo`() {
        val seed = ByteArray(SEED_SIZE_64) { DUMMY_BYTE_55 }
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

    // ── Determinism: same seed must always produce the same key ───────────────

    /**
     * Directly validates [DeterministicSecureRandom]: the same seed must always produce
     * the same byte stream regardless of JVM vs Android runtime.
     *
     * This is the root fix for "Invalid data": Android's SHA1PRNG accumulated system entropy
     * even after setSeed(), producing different key pairs between registration and signing.
     */
    @Test
    fun `DeterministicSecureRandom produces identical bytes from same seed on any runtime`() {
        val seed = ByteArray(SEED_SIZE_32) { 0xDE.toByte() }

        val rng1 = DeterministicSecureRandom(seed)
        val rng2 = DeterministicSecureRandom(seed)

        val out1 = ByteArray(BUFFER_SIZE_128).also { rng1.nextBytes(it) }
        val out2 = ByteArray(BUFFER_SIZE_128).also { rng2.nextBytes(it) }

        assertContentEquals(
            out1,
            out2,
            "DeterministicSecureRandom must produce identical bytes from the same seed " +
                "on any JVM/Android runtime — no system entropy should be injected.",
        )
    }

    @Test
    fun `DeterministicSecureRandom rejects setSeed`() {
        val seed = ByteArray(SEED_SIZE_32) { 0xAA.toByte() }
        val rng = DeterministicSecureRandom(seed)
        val before = ByteArray(SEED_SIZE_32).also { DeterministicSecureRandom(seed).nextBytes(it) }

        // Calling setSeed with different data must NOT change output
        rng.setSeed(ByteArray(SEED_SIZE_32) { 0xFF.toByte() })
        rng.setSeed(ENTROPY_999)

        val after = ByteArray(SEED_SIZE_32).also { rng.nextBytes(it) }
        assertContentEquals(
            before,
            after,
            "setSeed must be a no-op — external entropy must never alter the deterministic stream.",
        )
    }

    /**
     * CRITICAL regression for "Invalid data":
     * Registration stores the public key from derivation #1.
     * Signing re-derives the key pair (derivation #2) — they MUST be identical.
     * With DeterministicSecureRandom replacing SHA1PRNG, this now holds on Android too.
     */
    @Test
    fun `generateMlDsaKeyPair is deterministic - same seed produces same key pair`() {
        val seed = ByteArray(SEED_SIZE_64) { 0xAB.toByte() }

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

    @Test
    fun `ML-DSA sign with re-derived key verifies against original public key`() {
        val seed = ByteArray(SEED_SIZE_64) { 0xAB.toByte() }

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
     */
    @Test
    fun `server side round-trip - reconstruct ML-DSA key from COSE pub and verify signature`() {
        val seed = ByteArray(SEED_SIZE_64) { 0xCD.toByte() }
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
        val tempSeed = ByteArray(SEED_SIZE_64) { 0x01 }
        val tempKeyPair = pqCrypto.generateMlDsaKeyPair(tempSeed)!!
        val tempSpki = SubjectPublicKeyInfo.getInstance(tempKeyPair.public.encoded)

        // Replace the bitString payload in the SPKI with our rawKey
        val newSpki = SubjectPublicKeyInfo(tempSpki.algorithm, rawKey)
        return newSpki.encoded
    }
}
