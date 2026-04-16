package com.chimali.fido2.data.service

import com.chimali.fido2.domain.exception.Fido2Exception
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides symmetric AES-CBC encryption and decryption for local credential metadata.
 *
 * This service is intentionally narrow in scope after the T145b cleanup:
 * - Asymmetric (AndroidKeyStore EC/RSA) logic has been removed; FIDO2 private keys are now
 *   derived in-memory by [Fido2CryptoService] via HDK.
 * - Only the AES-GCM symmetric helpers remain so that [CredentialEncryptionService] can
 *   protect metadata at rest without any change to its public API.
 */
@Singleton
class CredentialStorageService
    @Inject
    constructor() {
        companion object {
            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val KEY_ALGORITHM_AES = "AES"
            private const val BLOCK_MODE_CBC = "CBC"
            private const val PADDING_PKCS7 = "PKCS7Padding"
            private const val TRANSFORMATION_AES = "$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$PADDING_PKCS7"
            private const val KEY_SIZE_AES = 256
        }

        private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // ── Symmetric (AES) operations — used by CredentialEncryptionService ─────

        /**
         * Generates a symmetric AES key for encrypting sensitive data.
         */
        suspend fun generateEncryptionKey(alias: String): Result<SecretKey> {
            return try {
                val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES)
                keyGenerator.init(KEY_SIZE_AES)
                val secretKey = keyGenerator.generateKey()
                Result.success(secretKey)
            } catch (e: Exception) {
                Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
            }
        }

        /**
         * Checks if a key alias exists in Android KeyStore.
         */
        suspend fun keyExists(alias: String): Boolean {
            return try {
                keyStore.containsAlias(alias)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Encrypts data using a symmetric AES key from Android KeyStore.
         */
        suspend fun encryptData(
            data: ByteArray,
            keyAlias: String,
        ): Result<EncryptedData> {
            return try {
                val key =
                    keyStore.getKey(keyAlias, null) as? SecretKey
                        ?: return Result.failure(Fido2Exception.KeyNotFound(keyAlias))

                val cipher = Cipher.getInstance(TRANSFORMATION_AES)
                cipher.init(Cipher.ENCRYPT_MODE, key)

                val iv = cipher.iv
                val encryptedData = cipher.doFinal(data)

                Result.success(EncryptedData(data = encryptedData, iv = iv, keyAlias = keyAlias))
            } catch (e: Exception) {
                Result.failure(Fido2Exception.EncryptionFailed(e.message ?: "Unknown error", e))
            }
        }

        /**
         * Decrypts data using a symmetric AES key from Android KeyStore.
         */
        suspend fun decryptData(encryptedData: EncryptedData): Result<ByteArray> {
            return try {
                val key =
                    keyStore.getKey(encryptedData.keyAlias, null) as? SecretKey
                        ?: return Result.failure(Fido2Exception.KeyNotFound(encryptedData.keyAlias))

                val cipher = Cipher.getInstance(TRANSFORMATION_AES)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(encryptedData.iv))

                val decryptedData = cipher.doFinal(encryptedData.data)
                Result.success(decryptedData)
            } catch (e: Exception) {
                Result.failure(Fido2Exception.DecryptionFailed(e.message ?: "Unknown error", e))
            }
        }

        // ── Data classes ─────────────────────────────────────────────────────────

        data class EncryptedData(
            val data: ByteArray,
            val iv: ByteArray,
            val keyAlias: String,
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
    }
