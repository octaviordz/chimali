package com.chimali.core.security.impl

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@Req("NFR-SEC-040")
class EventStoreKeyProviderImplTest {
    private val masterSeedProvider = mockk<MasterSeedProvider>()
    private val keyProvider = EventStoreKeyProviderImpl(masterSeedProvider)

    private fun stubSessionOwner() {
        coEvery {
            masterSeedProvider.withSessionOwner(any(), any(), any<suspend (Long) -> ByteArray>())
        } coAnswers {
            thirdArg<suspend (Long) -> ByteArray>().invoke(1L)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `getEventStoreKey produces 32-byte key from HMAC-SHA512`() =
        runTest {
            val masterSeed = ByteArray(32) { 0x01.toByte() }
            val expected = referenceKey(masterSeed.copyOf(), "test_label")
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } returns masterSeed

            val key = keyProvider.getEventStoreKey("test_label")

            assertEquals(32, key.size)
            assertContentEquals(expected, key)
            assertTrue(masterSeed.all { it == 0.toByte() }, "The owned root copy must be erased")
            expected.fill(0)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `getEventStoreKey throws IllegalStateException when master seed is null`() =
        runTest {
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } returns null

            assertThrows(IllegalStateException::class.java) {
                runTest {
                    keyProvider.getEventStoreKey("test_label")
                }
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `getEventStoreKey produces distinct keys for distinct labels`() =
        runTest {
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } answers { ByteArray(32) { 0x01.toByte() } }

            val key1 = keyProvider.getEventStoreKey("label_1")
            val key2 = keyProvider.getEventStoreKey("label_2")

            assertNotEquals(key1.toList(), key2.toList())
        }

    private fun referenceKey(
        seed: ByteArray,
        label: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(seed, "HmacSHA512"))
        val output = mac.doFinal(label.toByteArray(Charsets.UTF_8)).copyOf(32)
        Arrays.fill(seed, 0)
        return output
    }
}
