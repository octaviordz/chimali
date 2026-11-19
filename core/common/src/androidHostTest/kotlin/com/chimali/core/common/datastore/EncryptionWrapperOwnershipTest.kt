package com.chimali.core.common.datastore

import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EncryptionWrapperOwnershipTest {
    @Test
    fun `encryption clears consumed plaintext and temporary IV and ciphertext while returning an owned envelope`() {
        val key = key()
        val plaintext = "synthetic secret payload".encodeToByteArray()
        val expectedPlaintext = plaintext.copyOf()
        val temporaryBuffers = mutableListOf<ByteArray>()
        var envelope: ByteArray? = null
        var decrypted: ByteArray? = null
        try {
            val encryptedEnvelope =
                EncryptionWrapper.encryptWithCipher(
                    plaintext,
                    Cipher.getInstance("AES/GCM/NoPadding"),
                    key,
                    temporaryBuffers::add,
                )
            envelope = encryptedEnvelope

            assertTrue(plaintext.all { it == 0.toByte() })
            assertTrue(temporaryBuffers.size == 2)
            assertTrue(temporaryBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
            assertTrue(encryptedEnvelope.any { it != 0.toByte() }, "The envelope is a separate caller-owned result")

            val decryptedPlaintext =
                EncryptionWrapper.decryptWithCipher(
                    encryptedEnvelope,
                    Cipher.getInstance("AES/GCM/NoPadding"),
                    key,
                )
            decrypted = decryptedPlaintext
            assertContentEquals(expectedPlaintext, decryptedPlaintext)
        } finally {
            plaintext.fill(0)
            expectedPlaintext.fill(0)
            envelope?.fill(0)
            decrypted?.fill(0)
        }
    }

    @Test
    fun `encryption failure still clears consumed plaintext`() {
        val invalidKeyBytes = ByteArray(8) { 0x41 }
        val invalidKey = SecretKeySpec(invalidKeyBytes, "AES")
        val plaintext = "synthetic failure payload".encodeToByteArray()
        try {
            assertFailsWith<InvalidKeyException> {
                EncryptionWrapper.encryptWithCipher(
                    plaintext,
                    Cipher.getInstance("AES/GCM/NoPadding"),
                    invalidKey,
                )
            }
            assertTrue(plaintext.all { it == 0.toByte() })
        } finally {
            invalidKeyBytes.fill(0)
            plaintext.fill(0)
        }
    }

    @Test
    fun `cipher creation and keystore lookup failures still clear consumed plaintext`() {
        val cipherSetupPlaintext = "synthetic cipher setup failure".encodeToByteArray()
        val keyLookupPlaintext = "synthetic keystore lookup failure".encodeToByteArray()
        try {
            assertFailsWith<IllegalStateException> {
                EncryptionWrapper.encryptUsing(
                    cipherSetupPlaintext,
                    { error("synthetic cipher creation failure") },
                    ::key,
                )
            }
            assertTrue(cipherSetupPlaintext.all { it == 0.toByte() })

            assertFailsWith<IllegalStateException> {
                EncryptionWrapper.encryptUsing(
                    keyLookupPlaintext,
                    { Cipher.getInstance("AES/GCM/NoPadding") },
                    { error("synthetic Keystore key lookup failure") },
                )
            }
            assertTrue(keyLookupPlaintext.all { it == 0.toByte() })
        } finally {
            cipherSetupPlaintext.fill(0)
            keyLookupPlaintext.fill(0)
        }
    }

    @Test
    fun `decryption clears copied slices on success and authentication failure without mutating envelope`() {
        val key = key()
        val plaintext = "synthetic decrypt payload".encodeToByteArray()
        val expectedPlaintext = plaintext.copyOf()
        val envelope =
            EncryptionWrapper.encryptWithCipher(
                plaintext,
                Cipher.getInstance("AES/GCM/NoPadding"),
                key,
            )
        val originalEnvelope = envelope.copyOf()
        val temporaryBuffers = mutableListOf<ByteArray>()
        var decrypted: ByteArray? = null
        try {
            val decryptedPlaintext =
                EncryptionWrapper.decryptWithCipher(
                    envelope,
                    Cipher.getInstance("AES/GCM/NoPadding"),
                    key,
                    temporaryBuffers::add,
                )
            decrypted = decryptedPlaintext
            assertContentEquals(expectedPlaintext, decryptedPlaintext)
            assertTrue(temporaryBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
            assertContentEquals(originalEnvelope, envelope, "Decryption borrows but does not erase the envelope")
            decryptedPlaintext.fill(0)
            decrypted = null

            envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
            temporaryBuffers.clear()
            assertFailsWith<AEADBadTagException> {
                EncryptionWrapper.decryptWithCipher(
                    envelope,
                    Cipher.getInstance("AES/GCM/NoPadding"),
                    key,
                    temporaryBuffers::add,
                )
            }
            assertTrue(temporaryBuffers.size == 2)
            assertTrue(temporaryBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
        } finally {
            plaintext.fill(0)
            expectedPlaintext.fill(0)
            envelope.fill(0)
            originalEnvelope.fill(0)
            decrypted?.fill(0)
            temporaryBuffers.forEach { it.fill(0) }
        }
    }

    private fun key(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return generator.generateKey()
    }
}
