package com.chimali.core.security.hdkeys

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests for HashToScalar — RFC 9380 expand_message_xmd and hash_to_field.
 *
 * Test vectors are from RFC 9380 §K.1 (expand_message_xmd with SHA-256).
 */
class HashToScalarTest {
    private companion object {
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_SIZE = 2
        private const val EXPAND_XMD_LEN_32 = 32
        private const val EXPAND_XMD_LEN_128 = 128
        private const val I2OSP_LEN_1 = 1
        private const val I2OSP_LEN_2 = 2
        private const val I2OSP_LEN_4 = 4
        private const val VAL_32 = 32
        private const val VAL_42 = 42
    }

    @Test
    fun `expandMessageXmd - empty msg, len 32`() {
        // RFC 9380 test vector: DST = "QUUX-V01-CS02-with-expander-SHA256-128"
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = ByteArray(0)
        val result = HashToScalar.expandMessageXmd(msg, dst, EXPAND_XMD_LEN_32)

        val expected = hexToBytes("68a985b87eb6b46952128911f2a4412bbc302a9d759667f87f7a21d803f07235")
        assertContentEquals(expected, result)
    }

    @Test
    fun `expandMessageXmd - msg abc, len 32`() {
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = "abc".toByteArray(Charsets.US_ASCII)
        val result = HashToScalar.expandMessageXmd(msg, dst, EXPAND_XMD_LEN_32)

        val expected = hexToBytes("d8ccab23b5985ccea865c6c97b6e5b8350e794e603b4b97902f53a8a0d605615")
        assertContentEquals(expected, result)
    }

    @Test
    fun `expandMessageXmd - empty msg, len 128`() {
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = ByteArray(0)
        val result = HashToScalar.expandMessageXmd(msg, dst, EXPAND_XMD_LEN_128)

        val expected = hexToBytes(
            "af84c27ccfd45d41914fdff5df25293e221afc53d8ad2ac06d5e3e29485dadbe" +
            "e0d121587713a3e0dd4d5e69e93eb7cd4f5df4cd103e188cf60cb02edc3edf18" +
            "eda8576c412b18ffb658e3dd6ec849469b979d444cf7b26911a08e63cf31f9dc" +
            "c541708d3491184472c2c29bb749d4286b004ceb5ee6b9a7fa5b646c993f0ced"
        )
        assertContentEquals(expected, result)
    }

    @Test
    fun `hashToScalar produces a scalar in valid range`() {
        val dst = "test-dst".toByteArray(Charsets.US_ASCII)
        val msg = "test message".toByteArray(Charsets.US_ASCII)
        val scalar = HashToScalar.hashToScalar(msg, dst)

        assertTrue(scalar.signum() > 0, "Scalar must be positive")
        assertTrue(scalar < P256Group.ORDER, "Scalar must be less than order")
    }

    @Test
    fun `hashToScalar is deterministic`() {
        val dst = "test-dst".toByteArray(Charsets.US_ASCII)
        val msg = "test".toByteArray(Charsets.US_ASCII)
        val s1 = HashToScalar.hashToScalar(msg, dst)
        val s2 = HashToScalar.hashToScalar(msg, dst)
        assertEquals(s1, s2)
    }

    @Test
    fun `i2osp produces correct encoding`() {
        assertContentEquals(byteArrayOf(0), HashToScalar.i2osp(0, I2OSP_LEN_1))
        assertContentEquals(byteArrayOf(1), HashToScalar.i2osp(1, I2OSP_LEN_1))
        assertContentEquals(byteArrayOf(0, 0x20.toByte()), HashToScalar.i2osp(VAL_32, I2OSP_LEN_2))
        assertContentEquals(byteArrayOf(0, 0, 0, 0x2A.toByte()), HashToScalar.i2osp(VAL_42, I2OSP_LEN_4))
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(HEX_BYTE_SIZE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
    }
}
