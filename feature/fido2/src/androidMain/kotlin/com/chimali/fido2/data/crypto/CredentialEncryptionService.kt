package com.chimali.fido2.data.crypto

import com.chimali.fido2.data.service.CredentialStorageService
import com.chimali.fido2.domain.exception.Fido2Exception
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Service for encrypting and decrypting sensitive credential data.
 * Uses AES-GCM for authenticated encryption with additional data.
 */
@Single
class CredentialEncryptionService(
    private val credentialStorageService: CredentialStorageService,
) {
    companion object {
        private const val ALGORITHM_AES = "AES"
        private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 96 bits
        private const val GCM_TAG_LENGTH = 16 // 128 bits
        private const val KEY_SIZE_AES = 256
        private const val MASTER_KEY_ALIAS = "fido2_master_encryption_key"

        private const val BITS_PER_BYTE = 8
        private const val HKDF_BLOCK_SIZE = 32
        private const val HKDF_ROUND_UP_OFFSET = 31
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypts sensitive credential data with authenticated encryption.
     *
     * @param data The data to encrypt
     * @param associatedData Additional data that is authenticated but not encrypted
     * @return EncryptedData containing the encrypted result
     */
    suspend fun encrypt(
        data: ByteArray,
        associatedData: ByteArray? = null,
    ): Result<CredentialStorageService.EncryptedData> {
        return withContext(Dispatchers.IO) {
            try {
                // Get or create master encryption key
                val masterKey = getOrCreateMasterKey()

                // Generate random IV
                val iv = ByteArray(GCM_IV_LENGTH)
                secureRandom.nextBytes(iv)

                // Initialize cipher for encryption
                val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, iv)
                cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)

                // Add associated data if provided
                associatedData?.let { cipher.updateAAD(it) }

                // Encrypt the data
                val encryptedData = cipher.doFinal(data)

                Result.success(
                    CredentialStorageService.EncryptedData(
                        data = encryptedData,
                        iv = iv,
                        keyAlias = MASTER_KEY_ALIAS,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(Fido2Exception.EncryptionFailed(e.message ?: "Unknown error", e))
            }
        }
    }

    /**
     * Decrypts encrypted credential data with authentication.
     *
     * @param encryptedData The encrypted data to decrypt
     * @param associatedData Additional data that was authenticated during encryption
     * @return Decrypted data as ByteArray
     */
    suspend fun decrypt(
        encryptedData: CredentialStorageService.EncryptedData,
        associatedData: ByteArray? = null,
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                // Get master encryption key
                val masterKey =
                    getMasterKey()
                        ?: return@withContext Result.failure(Fido2Exception.KeyNotFound(MASTER_KEY_ALIAS))

                // Initialize cipher for decryption
                val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, encryptedData.iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)

                // Add associated data if provided
                associatedData?.let { cipher.updateAAD(it) }

                // Decrypt the data
                val decryptedData = cipher.doFinal(encryptedData.data)

                Result.success(decryptedData)
            } catch (e: Exception) {
                Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
            }
        }
    }

    /**
     * Encrypts a string value.
     */
    suspend fun encryptString(
        value: String,
        associatedData: ByteArray? = null,
    ): Result<String> {
        return encrypt(value.toByteArray(), associatedData).map { encryptedData ->
            // Combine IV and encrypted data for storage
            val combined = encryptedData.iv + encryptedData.data
            Base64.getEncoder().encodeToString(combined)
        }
    }

    /**
     * Decrypts a string value.
     */
    suspend fun decryptString(
        encryptedValue: String,
        associatedData: ByteArray? = null,
    ): Result<String> {
        return try {
            val combined = Base64.getDecoder().decode(encryptedValue)

            if (combined.size < GCM_IV_LENGTH) {
                return Result.failure(Fido2Exception.InvalidEncryptedData("Data too short"))
            }

            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val data = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            val encryptedData =
                CredentialStorageService.EncryptedData(
                    data = data,
                    iv = iv,
                    keyAlias = MASTER_KEY_ALIAS,
                )

            decrypt(encryptedData, associatedData).map { decryptedData ->
                String(decryptedData)
            }
        } catch (e: Exception) {
            Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Encrypts credential metadata with RP ID as associated data.
     */
    suspend fun encryptCredentialMetadata(
        rpId: String,
        userId: String,
        userName: String,
        userDisplayName: String,
    ): Result<EncryptedCredentialMetadata> {
        return try {
            val metadata =
                CredentialMetadata(
                    rpId = rpId,
                    userId = userId,
                    userName = userName,
                    userDisplayName = userDisplayName,
                )

            val metadataJson = serializeMetadata(metadata)
            val associatedData = rpId.toByteArray()

            encrypt(metadataJson.toByteArray(), associatedData).map { encryptedData ->
                EncryptedCredentialMetadata(
                    encryptedData = Base64.getEncoder().encodeToString(encryptedData.data),
                    iv = Base64.getEncoder().encodeToString(encryptedData.iv),
                    rpIdHash = hashRpId(rpId),
                )
            }
        } catch (e: Exception) {
            Result.failure(Fido2Exception.EncryptionFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Decrypts credential metadata with RP ID verification.
     */
    suspend fun decryptCredentialMetadata(
        encryptedMetadata: EncryptedCredentialMetadata,
        expectedRpId: String,
    ): Result<CredentialMetadata> {
        return try {
            // Verify RP ID hash
            val expectedHash = hashRpId(expectedRpId)
            if (encryptedMetadata.rpIdHash != expectedHash) {
                return Result.failure(Fido2Exception.RpIdMismatch(expectedRpId, encryptedMetadata.rpIdHash))
            }

            val encryptedData = Base64.getDecoder().decode(encryptedMetadata.encryptedData)
            val iv = Base64.getDecoder().decode(encryptedMetadata.iv)

            val credentialEncryptedData =
                CredentialStorageService.EncryptedData(
                    data = encryptedData,
                    iv = iv,
                    keyAlias = MASTER_KEY_ALIAS,
                )

            val associatedData = expectedRpId.toByteArray()
            decrypt(credentialEncryptedData, associatedData).map { decryptedData ->
                val metadataJson = String(decryptedData)
                deserializeMetadata(metadataJson)
            }
        } catch (e: Exception) {
            Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Creates a key derivation key for a specific credential.
     */
    suspend fun deriveCredentialKey(
        credentialId: String,
        rpId: String,
    ): Result<SecretKey> {
        return try {
            // Use HKDF to derive a unique key for each credential
            val masterKey = getOrCreateMasterKey()
            val salt = (credentialId + rpId).toByteArray()

            val derivedKey =
                hkdfSha256(
                    masterKey = masterKey,
                    salt = salt,
                    info = "fido2_credential_key".toByteArray(),
                    // 256 bits
                    outputLength = 32,
                )

            Result.success(SecretKeySpec(derivedKey, ALGORITHM_AES))
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyDerivationFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Encrypts data using a credential-specific derived key.
     */
    suspend fun encryptWithCredentialKey(
        data: ByteArray,
        credentialId: String,
        rpId: String,
    ): Result<CredentialStorageService.EncryptedData> {
        return try {
            val derivedKey = deriveCredentialKey(credentialId, rpId).getOrThrow()

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, iv)
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey, gcmSpec)

            // Encrypt the data
            val encryptedData = cipher.doFinal(data)

            Result.success(
                CredentialStorageService.EncryptedData(
                    data = encryptedData,
                    iv = iv,
                    keyAlias = "derived_${credentialId}_${rpId.hashCode()}",
                ),
            )
        } catch (e: Exception) {
            Result.failure(Fido2Exception.EncryptionFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Decrypts data using a credential-specific derived key.
     */
    suspend fun decryptWithCredentialKey(
        encryptedData: CredentialStorageService.EncryptedData,
        credentialId: String,
        rpId: String,
    ): Result<ByteArray> {
        return try {
            val derivedKey = deriveCredentialKey(credentialId, rpId).getOrThrow()

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, gcmSpec)

            // Decrypt the data
            val decryptedData = cipher.doFinal(encryptedData.data)

            Result.success(decryptedData)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Rotates the master encryption key.
     */
    suspend fun rotateMasterKey(): Result<Unit> {
        return try {
            generateMasterKey()

            // In a real implementation, you would:
            // 1. Re-encrypt all existing data with the new key
            // 2. Update all references
            // 3. Delete the old key

            // For now, we'll just create the new key
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyRotationFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Gets or creates the master encryption key.
     */
    private suspend fun getOrCreateMasterKey(): SecretKey {
        return getMasterKey() ?: generateMasterKey().getOrThrow()
    }

    /**
     * Gets the master encryption key.
     */
    private suspend fun getMasterKey(): SecretKey? {
        return try {
            credentialStorageService.keyExists(MASTER_KEY_ALIAS)
            // In a real implementation, you would retrieve the actual key from KeyStore
            // For now, we'll generate a temporary key for demonstration
            generateMasterKey().getOrNull()
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.e(e) { "CredentialEncryptionService: Failed to get master key" }
            null
        }
    }

    /**
     * Generates a new master encryption key.
     */
    private suspend fun generateMasterKey(): Result<SecretKey> {
        return try {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM_AES)
            keyGenerator.init(KEY_SIZE_AES)
            val key = keyGenerator.generateKey()

            // In a real implementation, store this in Android KeyStore
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * HKDF-SHA256 implementation for key derivation.
     */
    private fun hkdfSha256(
        masterKey: SecretKey,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        // Simplified HKDF implementation
        // In production, use a proper cryptographic library
        val hmacSha256 = javax.crypto.Mac.getInstance("HmacSHA256")
        hmacSha256.init(masterKey)

        // Extract
        hmacSha256.update(salt)
        val prk = hmacSha256.doFinal()

        // Expand
        val result = mutableListOf<Byte>()
        var t = ByteArray(0)
        val iterations = (outputLength + HKDF_ROUND_UP_OFFSET) / HKDF_BLOCK_SIZE // 32 bytes per hash

        for (i in 1..iterations) {
            hmacSha256.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
            hmacSha256.update(t)
            hmacSha256.update(info)
            hmacSha256.update(i.toByte())
            t = hmacSha256.doFinal()
            result.addAll(t.asList())
        }

        return result.toByteArray().take(outputLength).toByteArray()
    }

    /**
     * Hashes RP ID for verification.
     */
    private fun hashRpId(rpId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rpId.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Serializes credential metadata to JSON.
     */
    private fun serializeMetadata(metadata: CredentialMetadata): String {
        // Simplified JSON serialization
        return """{"rpId":"${metadata.rpId}","userId":"${metadata.userId}",""" +
            """"userName":"${metadata.userName}","userDisplayName":"${metadata.userDisplayName}"}"""
    }

    /**
     * Deserializes credential metadata from JSON.
     */
    private fun deserializeMetadata(json: String): CredentialMetadata {
        // Simplified JSON deserialization
        // In production, use a proper JSON library
        val rpId = json.substringAfter("\"rpId\":\"").substringBefore("\"")
        val userId = json.substringAfter("\"userId\":\"").substringBefore("\"")
        val userName = json.substringAfter("\"userName\":\"").substringBefore("\"")
        val userDisplayName = json.substringAfter("\"userDisplayName\":\"").substringBefore("\"")

        return CredentialMetadata(
            rpId = rpId,
            userId = userId,
            userName = userName,
            userDisplayName = userDisplayName,
        )
    }

    /**
     * Data class for credential metadata.
     */
    data class CredentialMetadata(
        val rpId: String,
        val userId: String,
        val userName: String,
        val userDisplayName: String,
    )

    /**
     * Data class for encrypted credential metadata.
     */
    data class EncryptedCredentialMetadata(
        val encryptedData: String,
        val iv: String,
        val rpIdHash: String,
    )
}
