package com.chimali.core.security.impl

import com.chimali.core.security.api.EncryptedMetadataService
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

@Single
class AesGcmEncryptedMetadataService : EncryptedMetadataService {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = EncryptedMetadataService.TAG_LENGTH_BYTES * 8
    }

    private var encryptionKey: SecretKey? = null
    private val secureRandom = SecureRandom()

    fun provisionKey(key: ByteArray) {
        encryptionKey = SecretKeySpec(key, "AES")
    }

    fun clearKey() {
        encryptionKey = null
    }

    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray {
        val key = checkNotNull(encryptionKey) { "Encryption key not provisioned" }
        val cipher = Cipher.getInstance(ALGORITHM)

        val nonce = ByteArray(EncryptedMetadataService.NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, nonce)

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext)
        // Format: VERSION (1 byte) || NONCE (12 bytes) || CIPHERTEXT
        return byteArrayOf(EncryptedMetadataService.VERSION_1.toByte()) + nonce + ciphertext
    }

    override fun decrypt(
        ciphertext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray {
        val key = checkNotNull(encryptionKey) { "Encryption key not provisioned" }
        require(ciphertext.size > 1 + EncryptedMetadataService.NONCE_LENGTH_BYTES) { "Invalid ciphertext length" }
        require(ciphertext[0] == EncryptedMetadataService.VERSION_1.toByte()) { "Unsupported metadata version" }

        val nonce = ciphertext.copyOfRange(1, 1 + EncryptedMetadataService.NONCE_LENGTH_BYTES)
        val encryptedData = ciphertext.copyOfRange(1 + EncryptedMetadataService.NONCE_LENGTH_BYTES, ciphertext.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, nonce)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        return try {
            cipher.doFinal(encryptedData)
        } catch (e: java.security.GeneralSecurityException) {
            throw SecurityException("Authentication failed: data may be tampered", e)
        }
    }
}
