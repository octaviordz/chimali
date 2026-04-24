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
    }

    private val decoder = PublicKeyDecoder()

    @Test
    fun `decode ML-DSA-65 public key successfully`() {
        val kpg = KeyPairGenerator.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        val keyPair = kpg.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        val base64Key = Base64.getEncoder().encodeToString(publicKeyBytes)

        val result = decoder.decodePublicKey(base64Key, -49)

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

        val result = decoder.decodePublicKey(base64Key, -7)

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

        val result = decoder.decodePublicKey(base64Key, -8)

        assertTrue(result.isSuccess)
        // Ed25519 or EdDSA depending on the exact algorithm name returned by the factory
        assertTrue(result.getOrNull()?.algorithm?.startsWith("Ed") == true)
        assertTrue(result.getOrNull()?.encoded.contentEquals(publicKeyBytes))
    }

    @Test
    fun `decode fails for invalid base64`() {
        val result = decoder.decodePublicKey("not-base64-!!!", -7)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode fails for unsupported algorithm`() {
        val result = decoder.decodePublicKey(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), -999)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode fails for corrupted key bytes`() {
        // Valid base64 but invalid DER encoding
        val result = decoder.decodePublicKey(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), -7)
        assertTrue(result.isFailure)
    }
}
