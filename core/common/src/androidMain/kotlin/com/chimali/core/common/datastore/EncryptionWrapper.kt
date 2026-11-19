package com.chimali.core.common.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionWrapper {
    companion object {
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 128

        /** Includes cipher and Keystore setup so a setup failure still consumes the plaintext owner. */
        internal fun encryptUsing(
            data: ByteArray,
            createCipher: () -> Cipher,
            getKey: () -> SecretKey,
            observeTemporary: (ByteArray) -> Unit = {},
        ): ByteArray =
            try {
                encryptWithCipher(data, createCipher(), getKey(), observeTemporary)
            } finally {
                data.fill(0)
            }

        /** Internal primitive seam for host ownership tests; production obtains the key from Android Keystore. */
        internal fun encryptWithCipher(
            data: ByteArray,
            cipher: Cipher,
            secretKey: SecretKey,
            observeTemporary: (ByteArray) -> Unit = {},
        ): ByteArray {
            var iv: ByteArray? = null
            var encrypted: ByteArray? = null
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                iv = cipher.iv
                observeTemporary(iv)
                encrypted = cipher.doFinal(data)
                observeTemporary(encrypted)
                return requireNotNull(iv) + requireNotNull(encrypted)
            } finally {
                data.fill(0)
                iv?.fill(0)
                encrypted?.fill(0)
            }
        }

        /** Internal primitive seam for host ownership tests; envelope input remains borrowed. */
        internal fun decryptWithCipher(
            data: ByteArray,
            cipher: Cipher,
            secretKey: SecretKey,
            observeTemporary: (ByteArray) -> Unit = {},
        ): ByteArray {
            if (data.size < IV_SIZE) return ByteArray(0)

            val iv = data.copyOfRange(0, IV_SIZE)
            var encrypted: ByteArray? = null
            try {
                observeTemporary(iv)
                encrypted = data.copyOfRange(IV_SIZE, data.size)
                observeTemporary(encrypted)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                return cipher.doFinal(encrypted)
            } finally {
                iv.fill(0)
                encrypted?.fill(0)
            }
        }
    }

    private val keyAlias = "chimali_datastore_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec =
                KeyGenParameterSpec
                    .Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey = keyStore.getKey(keyAlias, null) as SecretKey

    /** Encrypts and consumes [data], clearing it and all application-owned temporaries on every exit. */
    fun encrypt(data: ByteArray): ByteArray =
        encryptUsing(data, { Cipher.getInstance("AES/GCM/NoPadding") }, ::getSecretKey)

    /** Decrypts borrowed [data] and clears its copied IV/ciphertext slices on every exit. */
    fun decrypt(data: ByteArray): ByteArray =
        decryptWithCipher(data, Cipher.getInstance("AES/GCM/NoPadding"), getSecretKey())
}
