package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PrfExtensionOutput
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PrfKeyDerivationZeroingTest {
    private val hmacSecretProcessor = mockk<HmacSecretProcessor>()
    private val prfKeyDerivation = PrfKeyDerivation(hmacSecretProcessor)

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun `PrfExtensionOutput clear() zeros all output buffers`() =
        runTest {
            // Arrange
            val output1 = ByteArray(32) { 1 }
            val output2 = ByteArray(32) { 2 }

            val salt1 = ByteArray(32) { 1 }
            val salt2 = ByteArray(32) { 2 }
            val input = PrfExtensionInput(listOf(salt1, salt2))
            val credentialId = "test-cred"

            var result: PrfExtensionOutput? = null
            try {
                coEvery { hmacSecretProcessor.process(credentialId, salt1) } returns output1.copyOf()
                coEvery { hmacSecretProcessor.process(credentialId, salt2) } returns output2.copyOf()

                // Act
                result = prfKeyDerivation.deriveAll(input, credentialId)
                val actualResult = requireNotNull(result)

                // Assert before clearing
                assertContentEquals(output1, actualResult.output1)
                assertContentEquals(output2, actualResult.output2)

                // Clear
                actualResult.clear()

                // Assert after clearing
                assertTrue(actualResult.output1.all { it == 0.toByte() })
                assertTrue(actualResult.output2?.all { it == 0.toByte() } == true)
            } finally {
                result?.clear()
                salt1.fill(0)
                salt2.fill(0)
                output1.fill(0)
                output2.fill(0)
            }

            assertTrue(salt1.all { it == 0.toByte() })
            assertTrue(salt2.all { it == 0.toByte() })
            assertTrue(output1.all { it == 0.toByte() })
            assertTrue(output2.all { it == 0.toByte() })
        }
}
