package com.chimali.fido2.data.service

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import org.koin.core.annotation.Single

/**
 * Provides symmetric AES-CBC encryption and decryption for local credential metadata.
 *
 * This legacy helper exposes AndroidKeyStore AES-CBC operations for callers that explicitly
 * manage an alias. It is separate from the current searchable-metadata path, which uses
 * [CredentialMetadataProtectionService], and it does not store FIDO2 signing private keys.
 */
@Single
class CredentialStorageService {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM_AES = "AES"
        private const val BLOCK_MODE_CBC = "CBC"
        private const val PADDING_PKCS7 = "PKCS7Padding"
        private const val TRANSFORMATION_AES = "$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$PADDING_PKCS7"
        private const val KEY_SIZE_AES = 256
        private const val UNKNOWN_ERROR = "Unknown error"
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ── Symmetric AES-CBC operations ─────────────────────────────────────────

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun generateEncryptionKey(): Outcome<SecretKey, DomainError.CryptoError> =
        try {
            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES)
            keyGenerator.init(KEY_SIZE_AES)
            val secretKey = keyGenerator.generateKey()
            Outcome.Success(secretKey)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialStorageService: Failed to generate encryption key" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }

    /**
     * Checks if a key alias exists in Android KeyStore.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun keyExists(alias: String): Boolean =
        try {
            keyStore.containsAlias(alias)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialStorageService: Error checking if key exists: $alias" }
            false
        }

    /**
     * Encrypts data using a symmetric AES key from Android KeyStore.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun encryptData(
        data: ByteArray,
        keyAlias: String,
    ): Outcome<EncryptedData, DomainError.CryptoError> {
        return try {
            val key =
                keyStore.getKey(keyAlias, null) as? SecretKey
                    ?: return Outcome.Error(DomainError.CryptoError("Key not found: $keyAlias"))

            val cipher = Cipher.getInstance(TRANSFORMATION_AES)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)

            Outcome.Success(EncryptedData(data = encryptedData, iv = iv, keyAlias = keyAlias))
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialStorageService: Encryption failed for keyAlias=$keyAlias" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    /**
     * Decrypts data using a symmetric AES key from Android KeyStore.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun decryptData(encryptedData: EncryptedData): Outcome<ByteArray, DomainError.CryptoError> {
        return try {
            val key =
                keyStore.getKey(encryptedData.keyAlias, null) as? SecretKey
                    ?: return Outcome.Error(DomainError.CryptoError("Key not found: ${encryptedData.keyAlias}"))

            val cipher = Cipher.getInstance(TRANSFORMATION_AES)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(encryptedData.iv))

            val decryptedData = cipher.doFinal(encryptedData.data)
            Outcome.Success(decryptedData)
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "CredentialStorageService: Decryption failed for keyAlias=${encryptedData.keyAlias}" }
            Outcome.Error(DomainError.CryptoError(e.message ?: UNKNOWN_ERROR, e))
        }
    }

    // ── Data classes ─────────────────────────────────────────────────────────

    data class EncryptedData(
        val data: ByteArray,
        val iv: ByteArray,
        val keyAlias: String,
    ) {
        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedData
            if (!data.contentEquals(other.data)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (keyAlias != other.keyAlias) return false
            return true
        }

        @Req("NFR-SEC-041", "TR-AUTH-031")
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + keyAlias.hashCode()
            return result
        }
    }
}
