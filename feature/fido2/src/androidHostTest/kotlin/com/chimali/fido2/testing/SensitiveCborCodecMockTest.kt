package com.chimali.fido2.testing

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.SensitiveCredentialText
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SensitiveCborCodecMockTest {
    @Test
    fun `compatibility mock erases nested synthetic decoded byte and text owners`() {
        val codec = mockk<CborCodec>()
        codec.stubSensitiveRequestCompatibility()
        val bytes = byteArrayOf(4, 8, 15, 16)
        val textBytes = "synthetic account".encodeToByteArray()
        val text = SensitiveCredentialText.fromUtf8Bytes(textBytes)
        textBytes.fill(0)

        codec.eraseDecodedRequestValues(
            mapOf(
                "payload" to bytes,
                "nested" to listOf(mapOf("account" to text)),
            ),
        )

        assertContentEquals(ByteArray(bytes.size), bytes)
        assertTrue((0 until text.length).all { text[it] == '\u0000' })
    }
}
