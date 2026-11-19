package com.chimali.core.common.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionWrapperTest {
    @Test
    fun testEncryptionAndDecryption() {
        val wrapper = EncryptionWrapper()
        val plainText = "my_super_secret_seed_phrase".toByteArray()
        val plainTextCopy = plainText.clone()
        var encrypted: ByteArray? = null
        var decrypted: ByteArray? = null
        try {
            val envelope = wrapper.encrypt(plainText)
            encrypted = envelope
            assertTrue(plainText.all { it == 0.toByte() })

            val cleartext = wrapper.decrypt(envelope)
            decrypted = cleartext
            assertContentEquals(plainTextCopy, cleartext)
        } finally {
            plainText.fill(0)
            plainTextCopy.fill(0)
            encrypted?.fill(0)
            decrypted?.fill(0)
        }
    }
}
