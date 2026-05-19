package com.chimali.fido2.data.crypto

import com.chimali.fido2.domain.model.PrfExtensionInput
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PrfKeyDerivationZeroingTest {
    private val hmacSecretProcessor = mockk<HmacSecretProcessor>()
    private val prfKeyDerivation = PrfKeyDerivation(hmacSecretProcessor)

    @Test
    fun `PrfExtensionOutput clear() zeros all output buffers`() =
        runTest {
            // Arrange
            val output1 = ByteArray(32) { 1 }
            val output2 = ByteArray(32) { 2 }

            val salt1 = ByteArray(32) { 1 }
            val salt2 = ByteArray(32) { 2 }
            val input = PrfExtensionInput(listOf(salt1, salt2))
            val credentialId = "test-cred"

            coEvery { hmacSecretProcessor.process(credentialId, salt1) } returns output1.copyOf()
            coEvery { hmacSecretProcessor.process(credentialId, salt2) } returns output2.copyOf()

            // Act
            val result = prfKeyDerivation.deriveAll(input, credentialId)

            // Assert before clearing
            assertContentEquals(output1, result?.output1)
            assertContentEquals(output2, result?.output2)

            // Clear
            result?.clear()

            // Assert after clearing
            assertTrue(result?.output1?.all { it == 0.toByte() } == true)
            assertTrue(result?.output2?.all { it == 0.toByte() } == true)
        }
}
