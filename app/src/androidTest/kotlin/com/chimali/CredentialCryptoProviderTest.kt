package com.chimali

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises provider discovery in the target APK, including its release shrinking rules. */
@RunWith(AndroidJUnit4::class)
class CredentialCryptoProviderTest {
    /** Verifies that ES256 credential key conversion and signing survive shrinking. */
    @Test
    fun es256PublicKeyRoundTripAndSignature() {
        verifyCredentialAlgorithm("EC", "SHA256withECDSA", "secp256r1")
    }

    /** Verifies that Ed25519 credential key conversion and signing survive shrinking. */
    @Test
    fun ed25519PublicKeyRoundTripAndSignature() {
        verifyCredentialAlgorithm("Ed25519", "Ed25519")
    }

    /** Verifies that ML-DSA-65 credential key conversion and signing survive shrinking. */
    @Test
    fun mlDsa65PublicKeyRoundTripAndSignature() {
        verifyCredentialAlgorithm("ML-DSA-65", "ML-DSA-65")
    }

    private fun verifyCredentialAlgorithm(
        keyAlgorithm: String,
        signatureAlgorithm: String,
        curve: String? = null,
    ) {
        // Reference the provider facade only, never its mapping or service implementation classes.
        // Verify that it came from the target APK rather than an unshrunk test dependency.
        val targetLoader = InstrumentationRegistry.getInstrumentation().targetContext.classLoader
        val provider = BouncyCastleProvider()
        assertTrue("Provider must come from the target APK", provider.javaClass.classLoader === targetLoader)
        assertNotNull("Missing target KeyFactory.$keyAlgorithm", provider.getService("KeyFactory", keyAlgorithm))
        val generator = KeyPairGenerator.getInstance(keyAlgorithm, provider)
        curve?.let { generator.initialize(ECGenParameterSpec(it)) }
        val keyPair = generator.generateKeyPair()
        val decoded =
            KeyFactory.getInstance(keyAlgorithm, provider).generatePublic(X509EncodedKeySpec(keyPair.public.encoded))
        assertArrayEquals(keyPair.public.encoded, decoded.encoded)
        val message = "credential-provider-regression".encodeToByteArray()
        val signer = Signature.getInstance(signatureAlgorithm, provider)
        signer.initSign(keyPair.private)
        signer.update(message)
        val signature = signer.sign()
        val verifier = Signature.getInstance(signatureAlgorithm, provider)
        verifier.initVerify(decoded)
        verifier.update(message)
        assertTrue("Credential signature must verify", verifier.verify(signature))
        verifier.initVerify(decoded)
        verifier.update("different-message".encodeToByteArray())
        assertFalse("Credential signature must reject changed data", verifier.verify(signature))
    }
}
