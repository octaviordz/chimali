package com.chimali.core.common.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionWrapperTest {

    @Test
    fun testEncryptionAndDecryption() {
        val wrapper = EncryptionWrapper()
        val originalData = "test_sensitive_data".toByteArray()
        val originalCopy = originalData.copyOf() // Keep a copy because encrypt zeroizes the input

        val encrypted = wrapper.encrypt(originalData)
        
        // Ensure ciphertext is different from plaintext
        assertNotEquals(originalCopy.toList(), encrypted.toList())

        // Ensure original array was zeroed
        val zeroArray = ByteArray(originalCopy.size) { 0 }
        assertArrayEquals(zeroArray, originalData)

        val decrypted = wrapper.decrypt(encrypted)
        assertArrayEquals(originalCopy, decrypted)
    }
}
