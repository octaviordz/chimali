package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * T040a — Test for memory zeroing in [HmacSecretProcessor].
 * Fulfills Constitution §I.
 */
class HmacSecretProcessorZeroingTest {
    private val masterSeedProvider: MasterSeedProvider = mockk()
    private val processor = HmacSecretProcessor(masterSeedProvider)

    @Test
    fun `process zeros input salt when passed directly`() =
        runTest {
            coEvery { masterSeedProvider.getMasterSeed() } returns ByteArray(32) { 0xAA.toByte() }

            val salt = ByteArray(64) { 0x01.toByte() }
            val credentialId = "test-id"

            // Act
            processor.process(credentialId, salt)

            // Assert
            // The second half (index 32-63) should be zeroed per the implementation
            for (i in 32 until 64) {
                assertEquals(0.toByte(), salt[i], "Byte at index $i must be zeroed")
            }
        }
}
