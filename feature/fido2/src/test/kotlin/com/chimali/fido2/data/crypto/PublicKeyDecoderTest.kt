package com.chimali.fido2.data.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Base64

class PublicKeyDecoderTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }

        private const val COSE_ALG_ML_DSA_65 = -49
        private const val COSE_ALG_ES256 = -7
        private const val COSE_ALG_EDDSA = -8
        private const val COSE_ALG_UNKNOWN = -999
        private const val DUMMY_BYTE_01 = 1.toByte()
        private const val DUMMY_BYTE_02 = 2.toByte()
        private const val DUMMY_BYTE_03 = 3.toByte()
    }

    private val decoder = PublicKeyDecoder()

    @Test
    fun `decode ML-DSA-65 public key successfully`() {
        val kpg = KeyPairGenerator.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        val keyPair = kpg.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        val base64Key = Base64.getEncoder().encodeToString(publicKeyBytes)

        val result = decoder.decodePublicKey(base64Key, COSE_ALG_ML_DSA_65)

        assertTrue(result.isSuccess)
        assertEquals("ML-DSA-65", result.getOrNull()?.algorithm)
        assertTrue(result.getOrNull()?.encoded.contentEquals(publicKeyBytes))
    }

    @Test
    fun `decode EC256 public key successfully`() {
        val kpg = KeyPairGenerator.getInstance("EC")
        val keyPair = kpg.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        val base64Key = Base64.getEncoder().encodeToString(publicKeyBytes)

        val result = decoder.decodePublicKey(base64Key, COSE_ALG_ES256)

        assertTrue(result.isSuccess)
        assertEquals("EC", result.getOrNull()?.algorithm)
        assertTrue(result.getOrNull()?.encoded.contentEquals(publicKeyBytes))
    }

    @Test
    fun `decode EdDSA public key successfully`() {
        // Setup BouncyCastle to generate Ed25519 key
        val kpg = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        val keyPair = kpg.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        val base64Key = Base64.getEncoder().encodeToString(publicKeyBytes)

        val result = decoder.decodePublicKey(base64Key, COSE_ALG_EDDSA)

        assertTrue(result.isSuccess)
        // Ed25519 or EdDSA depending on the exact algorithm name returned by the factory
        assertTrue(result.getOrNull()?.algorithm?.startsWith("Ed") == true)
        assertTrue(result.getOrNull()?.encoded.contentEquals(publicKeyBytes))
    }

    @Test
    fun `decode fails for invalid base64`() {
        val result = decoder.decodePublicKey("not-base64-!!!", COSE_ALG_ES256)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode fails for unsupported algorithm`() {
        val result =
            decoder.decodePublicKey(
                Base64.getEncoder().encodeToString(byteArrayOf(DUMMY_BYTE_01, DUMMY_BYTE_02, DUMMY_BYTE_03)),
                COSE_ALG_UNKNOWN,
            )
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode fails for corrupted key bytes`() {
        // Valid base64 but invalid DER encoding
        val result =
            decoder.decodePublicKey(
                Base64.getEncoder().encodeToString(byteArrayOf(DUMMY_BYTE_01, DUMMY_BYTE_02, DUMMY_BYTE_03)),
                COSE_ALG_ES256,
            )
        assertTrue(result.isFailure)
    }
}
