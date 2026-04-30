package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.map
import com.chimali.fido2.data.service.CredentialStorageService
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
        private const val UNKNOWN_ERROR = "Unknown error"
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
    ): Outcome<CredentialStorageService.EncryptedData, DomainError.CryptoError> {
        return withContext(Dispatchers.IO) {
            try {
                // Get or create master encryption key
                val masterKey =
                    getOrCreateMasterKey()
                        ?: return@withContext Outcome.Error(
                            DomainError.CryptoError("Failed to obtain master key"),
                        )

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

                Outcome.Success(
                    CredentialStorageService.EncryptedData(
                        data = encryptedData,
                        iv = iv,
                        keyAlias = MASTER_KEY_ALIAS,
                    ),
                )
            } catch (e: java.security.GeneralSecurityException) {
                Logger.e(e) { "CredentialEncryptionService: Encryption failed" }
                Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
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
    ): Outcome<ByteArray, DomainError.CryptoError> {
        return withContext(Dispatchers.IO) {
            try {
                // Get master encryption key
                val masterKey =
                    getMasterKey()
                        ?: return@withContext Outcome.Error(
                            DomainError.CryptoError("Key not found: $MASTER_KEY_ALIAS"),
                        )

                // Initialize cipher for decryption
                val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, encryptedData.iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)

                // Add associated data if provided
                associatedData?.let { cipher.updateAAD(it) }

                // Decrypt the data
                val decryptedData = cipher.doFinal(encryptedData.data)

                Outcome.Success(decryptedData)
            } catch (e: java.security.GeneralSecurityException) {
                Logger.e(e) { "CredentialEncryptionService: Decryption failed" }
                Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
            }
        }
    }

    /**
     * Encrypts a string value.
     */
    suspend fun encryptString(
        value: String,
        associatedData: ByteArray? = null,
    ): Outcome<String, DomainError.CryptoError> {
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
    ): Outcome<String, DomainError.CryptoError> {
        return try {
            val combined = Base64.getDecoder().decode(encryptedValue)

            if (combined.size < GCM_IV_LENGTH) {
                return Outcome.Error(DomainError.CryptoError("Encrypted data too short"))
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
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "CredentialEncryptionService: Invalid Base64 for decryption" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
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
    ): Outcome<EncryptedCredentialMetadata, DomainError.CryptoError> {
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
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Failed to encrypt credential metadata for rpId=$rpId" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Decrypts credential metadata with RP ID verification.
     */
    suspend fun decryptCredentialMetadata(
        encryptedMetadata: EncryptedCredentialMetadata,
        expectedRpId: String,
    ): Outcome<CredentialMetadata, DomainError.CryptoError> {
        return try {
            // Verify RP ID hash
            val expectedHash = hashRpId(expectedRpId)
            if (encryptedMetadata.rpIdHash != expectedHash) {
                return Outcome.Error(
                    DomainError.CryptoError("RP ID mismatch: expected $expectedRpId"),
                )
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
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Failed to decrypt credential metadata" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Creates a key derivation key for a specific credential.
     */
    suspend fun deriveCredentialKey(
        credentialId: String,
        rpId: String,
    ): Outcome<SecretKey, DomainError.CryptoError> {
        return try {
            // Use HKDF to derive a unique key for each credential
            val masterKey =
                getOrCreateMasterKey()
                    ?: return Outcome.Error(DomainError.CryptoError("Failed to obtain master key"))
            val salt = (credentialId + rpId).toByteArray()

            val derivedKey =
                hkdfSha256(
                    masterKey = masterKey,
                    salt = salt,
                    info = "fido2_credential_key".toByteArray(),
                    // 256 bits
                    outputLength = 32,
                )

            Outcome.Success(SecretKeySpec(derivedKey, ALGORITHM_AES))
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Key derivation failed for credentialId=$credentialId" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Encrypts data using a credential-specific derived key.
     */
    suspend fun encryptWithCredentialKey(
        data: ByteArray,
        credentialId: String,
        rpId: String,
    ): Outcome<CredentialStorageService.EncryptedData, DomainError.CryptoError> {
        return try {
            val derivedKeyOutcome = deriveCredentialKey(credentialId, rpId)
            if (derivedKeyOutcome is Outcome.Error) return derivedKeyOutcome
            val derivedKey = (derivedKeyOutcome as Outcome.Success).data

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, iv)
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey, gcmSpec)

            // Encrypt the data
            val encryptedData = cipher.doFinal(data)

            Outcome.Success(
                CredentialStorageService.EncryptedData(
                    data = encryptedData,
                    iv = iv,
                    keyAlias = "derived_${credentialId}_${rpId.hashCode()}",
                ),
            )
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(
                e,
            ) { "CredentialEncryptionService: encryptWithCredentialKey failed for credentialId=$credentialId" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Decrypts data using a credential-specific derived key.
     */
    suspend fun decryptWithCredentialKey(
        encryptedData: CredentialStorageService.EncryptedData,
        credentialId: String,
        rpId: String,
    ): Outcome<ByteArray, DomainError.CryptoError> {
        return try {
            val derivedKeyOutcome = deriveCredentialKey(credentialId, rpId)
            if (derivedKeyOutcome is Outcome.Error) return derivedKeyOutcome
            val derivedKey = (derivedKeyOutcome as Outcome.Success).data

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * BITS_PER_BYTE, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, gcmSpec)

            // Decrypt the data
            val decryptedData = cipher.doFinal(encryptedData.data)

            Outcome.Success(decryptedData)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(
                e,
            ) { "CredentialEncryptionService: decryptWithCredentialKey failed for credentialId=$credentialId" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Rotates the master encryption key.
     */
    suspend fun rotateMasterKey(): Outcome<Unit, DomainError.CryptoError> {
        return try {
            generateMasterKey()

            // In a real implementation, you would:
            // 1. Re-encrypt all existing data with the new key
            // 2. Update all references
            // 3. Delete the old key

            // For now, we'll just create the new key
            Outcome.Success(Unit)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Master key rotation failed" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Gets or creates the master encryption key.
     */
    private suspend fun getOrCreateMasterKey(): SecretKey? {
        return getMasterKey() ?: generateMasterKey()
    }

    /**
     * Gets the master encryption key.
     */
    private suspend fun getMasterKey(): SecretKey? {
        return try {
            credentialStorageService.keyExists(MASTER_KEY_ALIAS)
            // In a real implementation, you would retrieve the actual key from KeyStore
            // For now, we'll generate a temporary key for demonstration
            generateMasterKey()
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Failed to get master key" }
            null
        }
    }

    /**
     * Generates a new master encryption key.
     */
    private fun generateMasterKey(): SecretKey? {
        return try {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM_AES)
            keyGenerator.init(KEY_SIZE_AES)

            // In a real implementation, store this in Android KeyStore
            keyGenerator.generateKey()
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialEncryptionService: Master key generation failed" }
            null
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
