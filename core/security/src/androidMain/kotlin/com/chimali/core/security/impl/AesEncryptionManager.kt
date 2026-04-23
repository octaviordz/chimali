package com.chimali.core.security.impl

import com.chimali.core.security.api.EncryptionManager
import org.koin.core.annotation.Single
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Single
class AesEncryptionManager : EncryptionManager {
    private val random = SecureRandom()
    private val ALGORITHM = "AES/GCM/NoPadding"
    private val TAG_LENGTH = 128
    private val IV_LENGTH = 12

    override fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        val keySpec = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)
        val ciphertext = cipher.doFinal(plaintext)
        
        return iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        val iv = ciphertext.sliceArray(0 until IV_LENGTH)
        val encryptedData = ciphertext.sliceArray(IV_LENGTH until ciphertext.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        val keySpec = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
        return cipher.doFinal(encryptedData)
    }
}
