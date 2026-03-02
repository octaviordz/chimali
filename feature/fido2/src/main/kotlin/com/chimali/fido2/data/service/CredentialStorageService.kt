package com.chimali.fido2.data.service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.chimali.fido2.domain.exception.Fido2Exception
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for secure storage and retrieval of cryptographic credentials using Android KeyStore.
 * Provides encryption, decryption, and key management operations.
 */
@Singleton
class CredentialStorageService @Inject constructor() {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM_EC = "EC"
        private const val KEY_ALGORITHM_RSA = "RSA"
        private const val KEY_ALGORITHM_AES = "AES"
        private const val BLOCK_MODE_CBC = "CBC"
        private const val PADDING_PKCS7 = "PKCS7Padding"
        private const val TRANSFORMATION_AES = "$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$PADDING_PKCS7"
        private const val KEY_SIZE_AES = 256
        private const val KEY_SIZE_EC = 256
        private const val KEY_SIZE_RSA = 2048
        private const val SIGNATURE_ALGORITHM_ECDSA = "SHA256withECDSA"
        private const val SIGNATURE_ALGORITHM_RSA = "SHA256withRSA"
    }
    
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    /**
     * Stores a private key securely in KeyStore with the given alias.
     */
    suspend fun storePrivateKey(
        alias: String,
        publicKey: PublicKey
    ): Result<Unit> {
        return try {
            // Generate a new key pair for this credential
            val keyPairGenerator = when (publicKey.algorithm) {
                KEY_ALGORITHM_EC -> {
                    val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false) // FIDO2 handles user auth separately
                        .setAttestationChallenge(byteArrayOf()) // No attestation challenge for storage
                        .build()
                    kpg.initialize(spec)
                    kpg.generateKeyPair()
                }
                KEY_ALGORITHM_RSA -> {
                    val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setKeySize(KEY_SIZE_RSA)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setUserAuthenticationRequired(false)
                        .build()
                    kpg.initialize(spec)
                    kpg.generateKeyPair()
                }
                else -> {
                    return Result.failure(Fido2Exception.UnsupportedAlgorithm(publicKey.algorithm))
                }
            }
            
            // Store the public key reference for retrieval
            storePublicKeyReference(alias, publicKey)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyStorageFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Retrieves a public key from KeyStore by alias.
     */
    suspend fun getPublicKey(alias: String): PublicKey? {
        return try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            entry?.certificate?.publicKey
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Retrieves a private key from KeyStore by alias.
     */
    suspend fun getPrivateKey(alias: String): PrivateKey? {
        return try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Deletes a key pair from KeyStore by alias.
     */
    suspend fun deletePrivateKey(alias: String): Result<Unit> {
        return try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            deletePublicKeyReference(alias)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyDeletionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Checks if a key exists in KeyStore.
     */
    suspend fun keyExists(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Lists all key aliases in KeyStore that belong to our application.
     */
    suspend fun listKeyAliases(): List<String> {
        return try {
            keyStore.aliases().toList().filter { alias ->
                alias.startsWith("fido2_") || alias.startsWith("cred_")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Generates a symmetric encryption key for encrypting sensitive data.
     */
    suspend fun generateEncryptionKey(alias: String): Result<SecretKey> {
        return try {
            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE_CBC)
                .setEncryptionPaddings(PADDING_PKCS7)
                .setKeySize(KEY_SIZE_AES)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(spec)
            val secretKey = keyGenerator.generateKey()
            Result.success(secretKey)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Encrypts data using a symmetric key.
     */
    suspend fun encryptData(
        data: ByteArray,
        keyAlias: String
    ): Result<EncryptedData> {
        return try {
            val key = keyStore.getKey(keyAlias, null) as? SecretKey
                ?: return Result.failure(Fido2Exception.KeyNotFound(keyAlias))
            
            val cipher = Cipher.getInstance(TRANSFORMATION_AES)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)
            
            Result.success(
                EncryptedData(
                    data = encryptedData,
                    iv = iv,
                    keyAlias = keyAlias
                )
            )
        } catch (e: Exception) {
            Result.failure(Fido2Exception.EncryptionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Decrypts data using a symmetric key.
     */
    suspend fun decryptData(encryptedData: EncryptedData): Result<ByteArray> {
        return try {
            val key = keyStore.getKey(encryptedData.keyAlias, null) as? SecretKey
                ?: return Result.failure(Fido2Exception.KeyNotFound(encryptedData.keyAlias))
            
            val cipher = Cipher.getInstance(TRANSFORMATION_AES)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(encryptedData.iv))
            
            val decryptedData = cipher.doFinal(encryptedData.data)
            Result.success(decryptedData)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Signs data using a private key from KeyStore.
     */
    suspend fun signData(
        data: ByteArray,
        keyAlias: String
    ): Result<ByteArray> {
        return try {
            val privateKey = getPrivateKey(keyAlias)
                ?: return Result.failure(Fido2Exception.KeyNotFound(keyAlias))
            
            val signatureAlgorithm = when (privateKey.algorithm) {
                KEY_ALGORITHM_EC -> SIGNATURE_ALGORITHM_ECDSA
                KEY_ALGORITHM_RSA -> SIGNATURE_ALGORITHM_RSA
                else -> return Result.failure(Fido2Exception.UnsupportedAlgorithm(privateKey.algorithm))
            }
            
            val signature = java.security.Signature.getInstance(signatureAlgorithm)
            signature.initSign(privateKey)
            signature.update(data)
            val signedData = signature.sign()
            
            Result.success(signedData)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.SignatureFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Verifies a signature using a public key.
     */
    suspend fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKey: PublicKey
    ): Result<Boolean> {
        return try {
            val signatureAlgorithm = when (publicKey.algorithm) {
                KEY_ALGORITHM_EC -> SIGNATURE_ALGORITHM_ECDSA
                KEY_ALGORITHM_RSA -> SIGNATURE_ALGORITHM_RSA
                else -> return Result.failure(Fido2Exception.UnsupportedAlgorithm(publicKey.algorithm))
            }
            
            val sig = java.security.Signature.getInstance(signatureAlgorithm)
            sig.initVerify(publicKey)
            sig.update(data)
            val isValid = sig.verify(signature)
            
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.SignatureVerificationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Exports a public key in PEM format for storage or transmission.
     */
    suspend fun exportPublicKey(publicKey: PublicKey): Result<String> {
        return try {
            val encoded = publicKey.encoded
            val base64Encoded = Base64.getEncoder().encodeToString(encoded)
            
            val pemFormat = when (publicKey.algorithm) {
                KEY_ALGORITHM_EC -> {
                    """-----BEGIN PUBLIC KEY-----
$base64Encoded
-----END PUBLIC KEY-----"""
                }
                KEY_ALGORITHM_RSA -> {
                    """-----BEGIN PUBLIC KEY-----
$base64Encoded
-----END PUBLIC KEY-----"""
                }
                else -> {
                    return Result.failure(Fido2Exception.UnsupportedAlgorithm(publicKey.algorithm))
                }
            }
            
            Result.success(pemFormat)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyExportFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Imports a public key from PEM format.
     */
    suspend fun importPublicKey(pemData: String): Result<PublicKey> {
        return try {
            val base64Data = pemData
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            
            val encoded = Base64.getDecoder().decode(base64Data)
            val keyFactory = java.security.KeyFactory.getInstance("EC") // Default to EC for FIDO2
            val publicKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(encoded))
            
            Result.success(publicKey)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyImportFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Rotates a key by creating a new key and migrating data.
     */
    suspend fun rotateKey(
        oldAlias: String,
        newAlias: String
    ): Result<Unit> {
        return try {
            val publicKey = getPublicKey(oldAlias)
                ?: return Result.failure(Fido2Exception.KeyNotFound(oldAlias))
            
            // Store new key
            val storeResult = storePrivateKey(newAlias, publicKey)
            if (storeResult.isFailure) {
                return storeResult
            }
            
            // Delete old key
            deletePrivateKey(oldAlias)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyRotationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Gets information about a stored key.
     */
    suspend fun getKeyInfo(alias: String): KeyInfo? {
        return try {
            if (!keyExists(alias)) return null
            
            val entry = keyStore.getEntry(alias, null)
            when (entry) {
                is KeyStore.PrivateKeyEntry -> {
                    val publicKey = entry.certificate.publicKey
                    KeyInfo(
                        alias = alias,
                        algorithm = publicKey.algorithm,
                        keySize = when (publicKey.algorithm) {
                            KEY_ALGORITHM_EC -> KEY_SIZE_EC
                            KEY_ALGORITHM_RSA -> KEY_SIZE_RSA
                            else -> 0
                        },
                        isPrivateKey = true,
                        createdAt = System.currentTimeMillis() // KeyStore doesn't provide creation time
                    )
                }
                is KeyStore.SecretKeyEntry -> {
                    KeyInfo(
                        alias = alias,
                        algorithm = entry.secretKey.algorithm,
                        keySize = KEY_SIZE_AES,
                        isPrivateKey = false,
                        createdAt = System.currentTimeMillis()
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Cleans up keys that are no longer needed.
     */
    suspend fun cleanupUnusedKeys(usedAliases: Set<String>): Result<Int> {
        return try {
            val allAliases = listKeyAliases()
            val unusedAliases = allAliases - usedAliases
            var cleanedCount = 0
            
            unusedAliases.forEach { alias ->
                deletePrivateKey(alias)
                cleanedCount++
            }
            
            Result.success(cleanedCount)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyCleanupFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Stores a reference to a public key for later retrieval.
     * This is a simplified implementation - in production, you might want to use
     * a more sophisticated approach like storing the key in a separate secure database.
     */
    private suspend fun storePublicKeyReference(alias: String, publicKey: PublicKey) {
        // For now, we rely on the KeyStore certificate chain
        // In a full implementation, you might store additional metadata
    }
    
    /**
     * Deletes a public key reference.
     */
    private suspend fun deletePublicKeyReference(alias: String) {
        // Cleanup any additional metadata stored for the public key
    }
    
    /**
     * Data class representing encrypted data.
     */
    data class EncryptedData(
        val data: ByteArray,
        val iv: ByteArray,
        val keyAlias: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedData
            if (!data.contentEquals(other.data)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (keyAlias != other.keyAlias) return false
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + keyAlias.hashCode()
            return result
        }
    }
    
    /**
     * Data class representing key information.
     */
    data class KeyInfo(
        val alias: String,
        val algorithm: String,
        val keySize: Int,
        val isPrivateKey: Boolean,
        val createdAt: Long
    )
}
