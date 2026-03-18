package com.chimali.fido2.data.crypto

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostQuantumCrypto @Inject constructor() {
    
    init {
        // Register Bouncy Castle PQC provider
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastlePQCProvider())
        }
    }
    
    fun isPqcSupported(): Boolean {
        return try {
            Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) != null
        } catch (e: Exception) {
            false
        }
    }
    
    fun generateKyberKeyPair(): KeyPair? {
        return try {
            if (!isPqcSupported()) return null
            
            // Try NIST name first, then BC name
            val algorithm = try {
                KeyPairGenerator.getInstance("ML-KEM-512", BouncyCastlePQCProvider.PROVIDER_NAME)
                "ML-KEM-512"
            } catch (e: Exception) {
                "Kyber"
            }
            
            val keyPairGenerator = KeyPairGenerator.getInstance(algorithm, BouncyCastlePQCProvider.PROVIDER_NAME)
            try {
                keyPairGenerator.initialize(KyberParameterSpec.kyber512)
            } catch (e: Exception) {
                keyPairGenerator.initialize(512)
            }
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            null
        }
    }
    
    fun kyberEncapsulate(publicKey: PublicKey?): Pair<ByteArray, ByteArray>? {
        return try {
            if (!isPqcSupported() || publicKey == null) return null
            
            // Probe for NIST name first, fall back to BC legacy name
            val cipherAlgorithm = try {
                javax.crypto.Cipher.getInstance("ML-KEM-512", BouncyCastlePQCProvider.PROVIDER_NAME)
                "ML-KEM-512"
            } catch (e: Exception) {
                "Kyber"
            }
            val cipher = javax.crypto.Cipher.getInstance(cipherAlgorithm, BouncyCastlePQCProvider.PROVIDER_NAME)
            cipher.init(javax.crypto.Cipher.WRAP_MODE, publicKey)
            val encapsulated = cipher.wrap(publicKey)
            val sharedSecret = ByteArray(32) // Kyber shared secret size
            // In a real implementation, this would extract the actual shared secret
            Pair(encapsulated, sharedSecret)
        } catch (e: Exception) {
            null
        }
    }
    
    fun kyberDecapsulate(privateKey: PrivateKey?, encapsulated: ByteArray?): ByteArray? {
        return try {
            if (!isPqcSupported() || privateKey == null || encapsulated == null) return null
            
            val cipher = javax.crypto.Cipher.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
            cipher.init(javax.crypto.Cipher.UNWRAP_MODE, privateKey)
            val sharedSecret = cipher.unwrap(encapsulated, "AES", javax.crypto.Cipher.SECRET_KEY)
            sharedSecret.encoded
        } catch (e: Exception) {
            null
        }
    }
    
}
