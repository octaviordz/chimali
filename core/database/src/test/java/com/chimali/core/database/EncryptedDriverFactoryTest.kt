package com.chimali.core.database

import android.content.Context
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EncryptedDriverFactoryTest {
    private val context: Context = mockk(relaxed = true)
    private val masterSeedProvider: MasterSeedProvider = mockk()
    private lateinit var factory: EncryptedDriverFactory

    @BeforeEach
    fun setUp() {
        factory = EncryptedDriverFactory(context, masterSeedProvider)
    }

    @Test
    fun `deriveDatabaseKey derives deterministic 256-bit key from seed`() {
        val seed1 = ByteArray(32) { it.toByte() }
        val seed2 = ByteArray(32) { it.toByte() }

        val key1 = factory.deriveDatabaseKey(seed1)
        val key2 = factory.deriveDatabaseKey(seed2)

        assertNotNull(key1)
        assertEquals(32, key1.size) // 256 bits = 32 bytes
        assertTrue(key1.contentEquals(key2), "Key derivation must be deterministic")
        assertFalse(key1.contentEquals(seed1), "Derived key must not be identical to the raw seed")
    }

    @Test
    fun `deriveDatabaseKey throws on empty seed`() {
        assertThrows<IllegalArgumentException> {
            factory.deriveDatabaseKey(ByteArray(0))
        }
    }

    @Test
    fun `createDriver throws IllegalStateException when master seed is null`() {
        coEvery { masterSeedProvider.getMasterSeed() } returns null

        val exception =
            assertThrows<IllegalStateException> {
                factory.createDriver(mockk(relaxed = true), "test.db")
            }
        assertTrue(exception.message!!.contains("Master seed not initialized"))
    }

    @Test
    fun `verifyIntegrity returns false when database file does not exist`() {
        every { context.getDatabasePath("non_existent.db") } returns
            mockk(relaxed = true) {
                every { exists() } returns false
            }

        val result = factory.verifyIntegrity("non_existent.db")
        assertEquals(false, result)
    }

    private fun assertFalse(
        condition: Boolean,
        message: String,
    ) {
        assertTrue(!condition, message)
    }
}
