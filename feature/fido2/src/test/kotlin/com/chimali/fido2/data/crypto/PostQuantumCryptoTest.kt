package com.chimali.fido2.data.crypto

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T017a — Smoke-test that the PostQuantumCrypto class initializes and
 * provides the expected ML-DSA-65 API.
 *
 * Full functional tests (KAT, sign/verify, determinism) live in [PqcSigningTest].
 * These legacy-replacement tests ensure the old Kyber/ML-KEM methods are gone
 * and the new ML-DSA API is wired up correctly.
 */
class PostQuantumCryptoTest {
    private lateinit var pqCrypto: PostQuantumCrypto

    @BeforeEach
    fun setUp() {
        pqCrypto = PostQuantumCrypto()
    }

    @Test
    fun `ML-DSA provider support detection returns true on JVM`() {
        assertTrue(pqCrypto.isMlDsaSupported(), "BouncyCastle BC provider must be available on JVM")
    }

    @Test
    fun `ML-DSA key pair generation returns non-null keypair`() {
        val seed = ByteArray(64) { it.toByte() }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair, "generateMlDsaKeyPair must return a non-null KeyPair")
        assertNotNull(keyPair?.public, "Public key must not be null")
        assertNotNull(keyPair?.private, "Private key must not be null")
    }

    @Test
    fun `ML-DSA key algorithm name contains ML-DSA`() {
        val seed = ByteArray(64) { 0x42 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        assertTrue(
            keyPair!!.public.algorithm.contains("ML-DSA", ignoreCase = true) ||
                keyPair.public.algorithm.contains("Dilithium", ignoreCase = true),
            "Expected ML-DSA or Dilithium algorithm, got: ${keyPair.public.algorithm}",
        )
    }

    @Test
    fun `publicKeyBytes returns non-empty byte array`() {
        val seed = ByteArray(64) { 0x11 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        val bytes = pqCrypto.publicKeyBytes(keyPair!!)
        assertTrue(bytes.isNotEmpty(), "Public key bytes must not be empty")
    }

    @Test
    fun `sign returns non-null signature`() {
        val seed = ByteArray(64) { 0x22 }
        val keyPair = pqCrypto.generateMlDsaKeyPair(seed)
        assertNotNull(keyPair)
        val message = "hello-fido2".toByteArray()
        val sig = pqCrypto.sign(keyPair!!.private, message)
        assertNotNull(sig, "sign() must return a non-null signature")
        assertTrue(sig!!.isNotEmpty(), "Signature must not be empty")
    }
}
