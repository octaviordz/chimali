package com.chimali.core.security.impl

import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventStoreKeyProviderImplTest {
    private val masterSeedProvider = mockk<MasterSeedProvider>()
    private val keyProvider = EventStoreKeyProviderImpl(masterSeedProvider)

    @Test
    fun `getEventStoreKey produces 32-byte key from HMAC-SHA512`() =
        runTest {
            val masterSeed = ByteArray(32) { 0x01.toByte() }
            coEvery { masterSeedProvider.getMasterSeed() } returns masterSeed

            val key = keyProvider.getEventStoreKey("test_label")

            assertEquals(32, key.size)
        }

    @Test
    fun `getEventStoreKey throws IllegalStateException when master seed is null`() =
        runTest {
            coEvery { masterSeedProvider.getMasterSeed() } returns null

            assertThrows(IllegalStateException::class.java) {
                runTest {
                    keyProvider.getEventStoreKey("test_label")
                }
            }
        }

    @Test
    fun `getEventStoreKey produces distinct keys for distinct labels`() =
        runTest {
            val masterSeed = ByteArray(32) { 0x01.toByte() }
            coEvery { masterSeedProvider.getMasterSeed() } returns masterSeed

            val key1 = keyProvider.getEventStoreKey("label_1")
            val key2 = keyProvider.getEventStoreKey("label_2")

            assertNotEquals(key1.toList(), key2.toList())
        }
}
