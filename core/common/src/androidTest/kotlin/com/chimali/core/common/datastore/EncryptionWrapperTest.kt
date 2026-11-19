package com.chimali.core.common.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionWrapperTest {

    @Test
    fun testEncryptionAndDecryption() {
        val wrapper = EncryptionWrapper()
        val originalData = "test_sensitive_data".toByteArray()
        val originalCopy = originalData.copyOf()
        var encrypted: ByteArray? = null
        var decrypted: ByteArray? = null
        try {
            val envelope = wrapper.encrypt(originalData)
            encrypted = envelope

            assertFalse(originalCopy.contentEquals(envelope))
            assertTrue(originalData.all { it == 0.toByte() })

            val cleartext = wrapper.decrypt(envelope)
            decrypted = cleartext
            assertArrayEquals(originalCopy, cleartext)
        } finally {
            originalData.fill(0)
            originalCopy.fill(0)
            encrypted?.fill(0)
            decrypted?.fill(0)
        }
    }
}
