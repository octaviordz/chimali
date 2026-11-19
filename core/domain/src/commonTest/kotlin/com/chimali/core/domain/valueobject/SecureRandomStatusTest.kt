package com.chimali.core.domain.valueobject

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SecureRandomStatusTest {
    @Test
    fun failedRandomRequestClearsPartialBytesAndThrowsFixedFailure() {
        val bytes = byteArrayOf(9, 8, 7, 6)

        val failure =
            assertFailsWith<IllegalStateException> {
                fillSecureRandomBytes(bytes) { output ->
                    output[0] = 42
                    -1
                }
            }

        assertEquals("Secure random generation failed", failure.message)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), bytes)
    }

    @Test
    fun successfulRandomRequestReturnsTheOriginalBuffer() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertSame(bytes, fillSecureRandomBytes(bytes) { 0 })
        assertContentEquals(byteArrayOf(1, 2, 3, 4), bytes)
    }

    @Test
    fun providerExceptionClearsPartialBytesAndDropsProviderDetails() {
        val bytes = byteArrayOf(9, 8, 7, 6)

        val failure =
            assertFailsWith<IllegalStateException> {
                fillSecureRandomBytes(bytes) { output ->
                    output[0] = 42
                    throw IllegalArgumentException("SYNTHETIC_PROVIDER_DETAIL")
                }
            }

        assertEquals("Secure random generation failed", failure.message)
        assertEquals(null, failure.cause)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), bytes)
    }
}
