package com.chimali.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface KeyStoreManager {
    suspend fun generateKeyPair(keyAlias: String, algorithm: String = "EC"): Pair<PublicKey, PrivateKey>
    suspend fun generateSecretKey(keyAlias: String, algorithm: String = "AES"): SecretKey
    suspend fun getPrivateKey(keyAlias: String): PrivateKey?
    suspend fun getPublicKey(keyAlias: String): PublicKey?
    suspend fun getSecretKey(keyAlias: String): SecretKey?
    suspend fun deleteKey(keyAlias: String)
    suspend fun keyExists(keyAlias: String): Boolean
    suspend fun signData(keyAlias: String, data: ByteArray): ByteArray
    suspend fun verifySignature(keyAlias: String, data: ByteArray, signature: ByteArray): Boolean
    suspend fun encryptData(keyAlias: String, data: ByteArray): Pair<ByteArray, ByteArray>
    suspend fun decryptData(keyAlias: String, encryptedData: ByteArray, iv: ByteArray): ByteArray
}

class AndroidKeyStoreManager : KeyStoreManager {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    override suspend fun generateKeyPair(keyAlias: String, algorithm: String): Pair<PublicKey, PrivateKey> {
        return try {
            val spec = when (algorithm) {
                "EC" -> KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
                else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
            }
            
            val keyPairGenerator = KeyPairGenerator.getInstance(algorithm, "AndroidKeyStore")
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            Pair(keyPair.public, keyPair.private)
        } catch (e: Exception) {
            throw KeyStoreException("Failed to generate key pair: ${e.message}", e)
        }
    }
    
    override suspend fun generateSecretKey(keyAlias: String, algorithm: String): SecretKey {
        return try {
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            
            val keyGenerator = KeyGenerator.getInstance(algorithm, "AndroidKeyStore")
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            throw KeyStoreException("Failed to generate secret key: ${e.message}", e)
        }
    }
    
    override suspend fun getPrivateKey(keyAlias: String): PrivateKey? {
        return try {
            keyStore.getKey(keyAlias, null) as? PrivateKey
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getPublicKey(keyAlias: String): PublicKey? {
        return try {
            keyStore.getCertificate(keyAlias)?.publicKey
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getSecretKey(keyAlias: String): SecretKey? {
        return try {
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun deleteKey(keyAlias: String) {
        try {
            keyStore.deleteEntry(keyAlias)
        } catch (e: Exception) {
            throw KeyStoreException("Failed to delete key: ${e.message}", e)
        }
    }
    
    override suspend fun keyExists(keyAlias: String): Boolean {
        return try {
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun signData(keyAlias: String, data: ByteArray): ByteArray {
        return try {
            val privateKey = getPrivateKey(keyAlias) 
                ?: throw KeyStoreException("Private key not found: $keyAlias")
            
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            throw KeyStoreException("Failed to sign data: ${e.message}", e)
        }
    }
    
    override suspend fun verifySignature(keyAlias: String, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val publicKey = getPublicKey(keyAlias) 
                ?: throw KeyStoreException("Public key not found: $keyAlias")
            
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            throw KeyStoreException("Failed to verify signature: ${e.message}", e)
        }
    }
    
    override suspend fun encryptData(keyAlias: String, data: ByteArray): Pair<ByteArray, ByteArray> {
        return try {
            val secretKey = getSecretKey(keyAlias) 
                ?: throw KeyStoreException("Secret key not found: $keyAlias")
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)
            
            Pair(encryptedData, iv)
        } catch (e: Exception) {
            throw KeyStoreException("Failed to encrypt data: ${e.message}", e)
        }
    }
    
    override suspend fun decryptData(keyAlias: String, encryptedData: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val secretKey = getSecretKey(keyAlias) 
                ?: throw KeyStoreException("Secret key not found: $keyAlias")
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            throw KeyStoreException("Failed to decrypt data: ${e.message}", e)
        }
    }
}

class KeyStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
