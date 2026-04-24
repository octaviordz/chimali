package com.chimali.fido2.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * T017a — Unit tests for [PostQuantumCrypto] (ML-DSA-65 / NIST FIPS 204).
 *
 * Test strategy (Known Answer Test approach):
 * - Determinism: same seed → same public key bytes.
 * - Round-trip: sign then verify succeeds.
 * - Tamper detection: verify rejects a modified message.
 * - Branch isolation: different seeds produce different keys.
 *
 * These tests run entirely in the JVM (no Android device required) because BouncyCastle
 * provides its own BC provider without any Android KeyStore dependency.
 */
class PqcSigningTest {
    private val pqc = PostQuantumCrypto()

    // ── Provider availability ─────────────────────────────────────────────────

    @Test
    fun providerSupportReturnsTrue() {
        assertTrue(
            pqc.isMlDsaSupported(),
            "BouncyCastle BC provider must be available on JVM",
        )
    }

    // ── Determinism (Known Answer Test) ───────────────────────────────────────

    @Test
    fun sameSeedProducesSamePublicKey() {
        val seed = ByteArray(64) { it.toByte() }

        val kp1 = pqc.generateMlDsaKeyPair(seed)
        val kp2 = pqc.generateMlDsaKeyPair(seed)

        assertNotNull(kp1, "Key pair must not be null")
        assertNotNull(kp2, "Key pair must not be null")
        assertContentEquals(
            pqc.publicKeyBytes(kp1!!),
            pqc.publicKeyBytes(kp2!!),
            "Same seed must produce the same public key (KAT)",
        )
    }

    @Test
    fun differentSeedsProduceDifferentKeys() {
        val seed1 = ByteArray(64) { 0x01 }
        val seed2 = ByteArray(64) { 0x02 }

        val kp1 = pqc.generateMlDsaKeyPair(seed1)
        val kp2 = pqc.generateMlDsaKeyPair(seed2)

        assertNotNull(kp1)
        assertNotNull(kp2)
        assertFalse(
            pqc.publicKeyBytes(kp1!!).contentEquals(pqc.publicKeyBytes(kp2!!)),
            "Different seeds must produce different public keys",
        )
    }

    // ── Sign / verify round-trip ──────────────────────────────────────────────

    @Test
    fun signAndVerifyRoundTripSucceeds() {
        val seed = ByteArray(64) { 0xAB.toByte() }
        val message = "authData||clientDataHash mock content".toByteArray()

        val kp = pqc.generateMlDsaKeyPair(seed)
        assertNotNull(kp)

        val signature = pqc.sign(kp!!.private, message)
        assertNotNull(signature, "Signature must not be null")

        val valid = pqc.verify(kp.public, message, signature!!)
        assertTrue(valid, "Signature must verify against the original message")
    }

    @Test
    fun verifyTamperedMessageReturnsFalse() {
        val seed = ByteArray(64) { 0xCD.toByte() }
        val originalMessage = "original-auth-data".toByteArray()
        val tamperedMessage = "tampered-auth-data".toByteArray()

        val kp = pqc.generateMlDsaKeyPair(seed)
        val signature = pqc.sign(kp!!.private, originalMessage)
        assertNotNull(signature)

        val valid = pqc.verify(kp.public, tamperedMessage, signature!!)
        assertFalse(valid, "Verification of a tampered message must fail")
    }

    @Test
    fun verifyWrongKeyReturnsFalse() {
        val seed1 = ByteArray(64) { 0xEF.toByte() }
        val seed2 = ByteArray(64) { 0x12 }
        val message = "some-challenge-bytes".toByteArray()

        val kp1 = pqc.generateMlDsaKeyPair(seed1)
        val kp2 = pqc.generateMlDsaKeyPair(seed2)
        assertNotNull(kp1)
        assertNotNull(kp2)

        val signature = pqc.sign(kp1!!.private, message)
        assertNotNull(signature)

        val valid = pqc.verify(kp2!!.public, message, signature!!)
        assertFalse(valid, "Verification with a mismatched public key must fail")
    }

    // ── Public key encoding ───────────────────────────────────────────────────

    @Test
    fun publicKeyBytesHasExpectedMinimumLength() {
        // ML-DSA-65 public keys are 1952 bytes in SubjectPublicKeyInfo DER format
        val seed = ByteArray(64) { 0x55 }
        val kp = pqc.generateMlDsaKeyPair(seed)
        assertNotNull(kp)
        val keyBytes = pqc.publicKeyBytes(kp!!)
        assertTrue(
            keyBytes.size >= 1952,
            "ML-DSA-65 public key DER must be at least 1952 bytes, was ${keyBytes.size}",
        )
    }

    // ── COSE algorithm constant ───────────────────────────────────────────────

    @Test
    fun coseAlgorithmConstantHasExpectedValue() {
        // Regression: must remain -49 to match IANA COSE assignment for ML-DSA-65
        assertEquals(-49, COSE_ML_DSA_65)
    }
}
