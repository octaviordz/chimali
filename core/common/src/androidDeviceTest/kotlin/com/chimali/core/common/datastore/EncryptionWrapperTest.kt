package com.chimali.core.common.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionWrapperTest {
    @Test
    fun testEncryptionAndDecryption() {
        val wrapper = EncryptionWrapper()
        val plainText = "my_super_secret_seed_phrase".toByteArray()
        val plainTextCopy = plainText.clone()

        val encrypted = wrapper.encrypt(plainText)

        // Assert memory zeroing of input array (T011a)
        assertTrue(plainText.all { it == 0.toByte() })

        val decrypted = wrapper.decrypt(encrypted)

        assertEquals(String(plainTextCopy), String(decrypted))
    }
}
