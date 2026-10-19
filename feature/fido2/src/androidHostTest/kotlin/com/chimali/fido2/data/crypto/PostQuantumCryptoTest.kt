package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec
import org.bouncycastle.jcajce.spec.MLDSAPrivateKeySpec
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * T017a — Smoke-test that the PostQuantumCrypto class initializes and
 * provides the expected ML-DSA-65 API.
 *
 * Full functional tests (KAT, sign/verify, determinism) live in [PqcSigningTest].
 * These legacy-replacement tests ensure the old Kyber/ML-KEM methods are gone
 * and the new ML-DSA API is wired up correctly.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PostQuantumCryptoTest {
    private lateinit var pqCrypto: PostQuantumCrypto

    @BeforeTest
    fun setUp() {
        pqCrypto = PostQuantumCrypto()
    }

    private companion object {
        private const val SEED_SIZE_32 = 32
        private const val DUMMY_BYTE_11 = 0x11.toByte()
        private const val DUMMY_BYTE_22 = 0x22.toByte()
        private const val DUMMY_BYTE_42 = 0x42.toByte()
        private const val FIXTURE_SEED = "a0d88b267f49065eadd6c3df70d8bea90821522c60d51496de9992cf2902791e"
        private const val FIXTURE_PUBLIC_KEY_SHA256 = "bd673eba4feb26964b9d1435692b042fc77c80af3a13fc59d632bccda40a16a4"
        private const val NIST_ACVP_SEED = "a991fd42b071d49c48ae3e75c647459e0daad1e1ba356a04801912d3294bcff8"
        private const val NIST_ACVP_PUBLIC_KEY_SHA256 =
            "b1a7d0d2f0d7a04b9d5ffccd9bd57886" +
                "4dab4a01cdd7f70a05cd1f4f0672e43a"
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ML-DSA provider support detection returns true on JVM`() {
        assertTrue(pqCrypto.isMlDsaSupported(), "BouncyCastle BC provider must be available on JVM")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ML-DSA key pair generation returns non-null keypair`() {
        val seed = ByteArray(SEED_SIZE_32) { it.toByte() }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair, "generateMlDsaKeyPair must return a non-null KeyPair")
        assertNotNull(keyPair?.public, "Public key must not be null")
        assertNotNull(keyPair?.private, "Private key must not be null")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ML-DSA key algorithm name contains ML-DSA`() {
        val seed = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_42 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        assertTrue(
            keyPair!!.public.algorithm.contains("ML-DSA", ignoreCase = true) ||
                keyPair.public.algorithm.contains("Dilithium", ignoreCase = true),
            "Expected ML-DSA or Dilithium algorithm, got: ${keyPair.public.algorithm}",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `publicKeyBytes returns non-empty byte array`() {
        val seed = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_11 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        val bytes = pqCrypto.publicKeyBytes(keyPair!!)
        assertTrue(bytes.isNotEmpty(), "Public key bytes must not be empty")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `sign returns non-null signature`() {
        val seed = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_22 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        val message = "hello-fido2".toByteArray()
        val sig = pqCrypto.sign(keyPair!!.private, message)
        assertNotNull(sig, "sign() must return a non-null signature")
        assertTrue(sig!!.isNotEmpty(), "Signature must not be empty")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `BC direct 32-byte seed import matches independent ML-DSA-65 fixture`() {
        val seed = FIXTURE_SEED.hexToBytes()
        try {
            val privateKey =
                KeyFactory
                    .getInstance("ML-DSA-65", "BC")
                    .generatePrivate(MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, seed)) as MLDSAPrivateKey
            val publicKey = privateKey.getPublicKey() as MLDSAPublicKey

            assertContentEquals(seed, privateKey.seed)
            assertEquals(FIXTURE_PUBLIC_KEY_SHA256, sha256Hex(publicKey.publicData))
        } finally {
            seed.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `BC direct seed import matches NIST ACVP ML-DSA-65 key-generation vector`() {
        val seed = NIST_ACVP_SEED.hexToBytes()
        try {
            val privateKey =
                KeyFactory
                    .getInstance("ML-DSA-65", "BC")
                    .generatePrivate(MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, seed)) as MLDSAPrivateKey
            val publicKey = privateKey.getPublicKey() as MLDSAPublicKey

            assertContentEquals(seed, privateKey.seed)
            assertEquals(NIST_ACVP_PUBLIC_KEY_SHA256, sha256Hex(publicKey.publicData))
        } finally {
            seed.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `production 32-byte seed generation matches independent ML-DSA-65 public fixture`() {
        val seed = FIXTURE_SEED.hexToBytes()
        try {
            val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
            assertNotNull(keyPair)
            val publicKey = keyPair!!.public as MLDSAPublicKey

            assertEquals(FIXTURE_PUBLIC_KEY_SHA256, sha256Hex(publicKey.publicData))
            assertContentEquals(FIXTURE_SEED.hexToBytes(), seed, "The seed is borrowed and must remain unchanged")

            val message = "synthetic ML-DSA signature fixture".toByteArray(Charsets.US_ASCII)
            val signature = pqCrypto.sign(keyPair.private, message)
            assertNotNull(signature)
            val verifier = Signature.getInstance("ML-DSA-65", "BC")
            verifier.initVerify(publicKey)
            verifier.update(message)
            assertTrue(verifier.verify(signature!!), "Signature must verify through the provider API")
        } finally {
            seed.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `BC verifies independent ML-DSA-65 signature fixture`() {
        val fixtureJson =
            requireNotNull(javaClass.getResourceAsStream("/hdk/mldsa65-cross-engine-v1.json")) {
                "Independent ML-DSA-65 reference signature fixture is missing"
            }.bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(fixtureJson).jsonObject
        val seed =
            fixture
                .getValue("seed")
                .jsonPrimitive.content
                .hexToBytes()
        val message =
            fixture
                .getValue("message")
                .jsonPrimitive.content
                .hexToBytes()
        val signature =
            fixture
                .getValue("signature")
                .jsonPrimitive.content
                .hexToBytes()
        try {
            val keyPair = requireNotNull(pqCrypto.generateMlDsaKeyPair(seed))
            val publicKey = keyPair.public as MLDSAPublicKey
            assertEquals(fixture.getValue("public_key_sha256").jsonPrimitive.content, sha256Hex(publicKey.publicData))
            assertTrue(
                pqCrypto.verify(publicKey, message, signature),
                "BC must accept the pinned independent signature",
            )
            assertTrue(
                !pqCrypto.verify(publicKey, message + byteArrayOf(0), signature),
                "The independent signature must reject a changed message",
            )
        } finally {
            seed.fill(0)
            message.fill(0)
            signature.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `production rejects seeds that are not exactly 32 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            pqCrypto.generateMlDsaKeyPair(ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            pqCrypto.generateMlDsaKeyPair(ByteArray(33))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `unavailable provider is reported without returning key or signature`() {
        val seed = FIXTURE_SEED.hexToBytes()
        try {
            val validPair = requireNotNull(pqCrypto.generateMlDsaKeyPair(seed))
            val unavailableProvider = PostQuantumCrypto("missing-test-provider")

            assertTrue(!unavailableProvider.isMlDsaSupported())
            assertNull(unavailableProvider.generateMlDsaKeyPair(seed))
            assertNull(unavailableProvider.sign(validPair.private, "synthetic".toByteArray()))
            assertContentEquals(FIXTURE_SEED.hexToBytes(), seed, "The caller-owned input must remain unchanged")
        } finally {
            seed.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `signing provider failure erases the returned private-key encoding`() {
        val encodedPrivateKey = ByteArray(32) { DUMMY_BYTE_42 }
        val privateKey =
            object : PrivateKey {
                override fun getAlgorithm(): String = "ML-DSA-65"

                override fun getFormat(): String = "PKCS#8"

                override fun getEncoded(): ByteArray = encodedPrivateKey
            }

        assertNull(PostQuantumCrypto("missing-test-provider").sign(privateKey, byteArrayOf(1, 2, 3)))
        assertContentEquals(ByteArray(encodedPrivateKey.size), encodedPrivateKey)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
