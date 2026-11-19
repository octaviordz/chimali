package com.chimali.core.security.impl

import com.chimali.core.security.api.EncryptionManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

@Single
class AesEncryptionManager : EncryptionManager {
    private val random = SecureRandom()

    /** Host-test observer for retaining operation-local arrays and verifying terminal cleanup. */
    internal var temporaryBufferObserver: ((ByteArray) -> Unit)? = null

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        var encryptedBytes: ByteArray? = null
        try {
            temporaryBufferObserver?.invoke(iv)
            random.nextBytes(iv)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            val keySpec = SecretKeySpec(key, "AES")

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)
            val encrypted = cipher.doFinal(plaintext)
            encryptedBytes = encrypted
            temporaryBufferObserver?.invoke(encrypted)

            return ByteArray(iv.size + encrypted.size).also { output ->
                iv.copyInto(output)
                encrypted.copyInto(output, destinationOffset = iv.size)
            }
        } finally {
            iv.fill(0)
            encryptedBytes?.fill(0)
        }
    }

    override fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        var iv: ByteArray? = null
        var encryptedData: ByteArray? = null
        try {
            val ownedIv = ciphertext.sliceArray(0 until IV_LENGTH)
            iv = ownedIv
            temporaryBufferObserver?.invoke(ownedIv)
            val ownedEncryptedData = ciphertext.sliceArray(IV_LENGTH until ciphertext.size)
            encryptedData = ownedEncryptedData
            temporaryBufferObserver?.invoke(ownedEncryptedData)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH, ownedIv)
            val keySpec = SecretKeySpec(key, "AES")

            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            return cipher.doFinal(ownedEncryptedData)
        } finally {
            iv?.fill(0)
            encryptedData?.fill(0)
        }
    }
}
