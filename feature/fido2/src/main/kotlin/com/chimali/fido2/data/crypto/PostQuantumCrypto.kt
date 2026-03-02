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
            
            val keyPairGenerator = KeyPairGenerator.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
            keyPairGenerator.initialize(KyberParameterSpec.kyber512())
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            null
        }
    }
    
    fun kyberEncapsulate(publicKey: PublicKey): Pair<ByteArray, ByteArray>? {
        return try {
            if (!isPqcSupported()) return null
            
            val cipher = javax.crypto.Cipher.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
            cipher.init(javax.crypto.Cipher.WRAP_MODE, publicKey)
            val encapsulated = cipher.wrap(publicKey)
            val sharedSecret = ByteArray(32) // Kyber shared secret size
            // In a real implementation, this would extract the actual shared secret
            Pair(encapsulated, sharedSecret)
        } catch (e: Exception) {
            null
        }
    }
    
    fun kyberDecapsulate(privateKey: PrivateKey, encapsulated: ByteArray): ByteArray? {
        return try {
            if (!isPqcSupported()) return null
            
            val cipher = javax.crypto.Cipher.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
            cipher.init(javax.crypto.Cipher.UNWRAP_MODE, privateKey)
            val sharedSecret = cipher.unwrap(encapsulated, "AES", javax.crypto.Cipher.SECRET_KEY)
            sharedSecret.encoded
        } catch (e: Exception) {
            null
        }
    }
    
    fun getRecommendedAlgorithm(): String {
        return if (isPqcSupported()) {
            "Kyber512"
        } else {
            "ECDH-P256"
        }
    }
}
