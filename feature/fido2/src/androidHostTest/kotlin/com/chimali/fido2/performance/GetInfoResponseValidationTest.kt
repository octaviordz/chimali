package com.chimali.fido2.performance

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetInfoResponseValidationTest {
    @Test
    fun `requires CTAP success and expected capability content`() {
        val valid = response()
        assertTrue(isSuccessfulGetInfo(listOf(valid)))

        assertFalse(isSuccessfulGetInfo(emptyList()))
        assertFalse(isSuccessfulGetInfo(listOf(valid.copyOf().apply { this[7] = 0x30 })))
        assertFalse(isSuccessfulGetInfo(listOf(valid.copyOf().apply { this[4] = 0x91.toByte() })))
        assertFalse(isSuccessfulGetInfo(listOf(valid.copyOf().apply { this[8] = 0x00 })))
        assertFalse(isSuccessfulGetInfo(listOf(valid.copyOf().apply { fill(0) })))
        assertFalse(isSuccessfulGetInfo(listOf(ByteArray(61))))
    }

    private fun response(): ByteArray =
        ByteArray(62).apply {
            this[4] = 0x90.toByte()
            this[7] = 0x00
            this[8] = 0xa4.toByte()
            val capabilities = "FIDO_2_0 FIDO_2_1 credProtect usb".encodeToByteArray()
            capabilities.copyInto(this, destinationOffset = 9)
        }
}
