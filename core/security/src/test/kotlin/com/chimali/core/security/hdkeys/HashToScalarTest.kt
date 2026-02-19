package com.chimali.core.security.hdkeys

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HashToScalar — RFC 9380 expand_message_xmd and hash_to_field.
 *
 * Test vectors are from RFC 9380 §K.1 (expand_message_xmd with SHA-256).
 */
class HashToScalarTest {

    @Test
    fun `expandMessageXmd - empty msg, len 32`() {
        // RFC 9380 test vector: DST = "QUUX-V01-CS02-with-expander-SHA256-128"
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = ByteArray(0)
        val result = HashToScalar.expandMessageXmd(msg, dst, 0x20)

        val expected = hexToBytes("68a985b87eb6b46952128911f2a4412bbc302a9d759667f87f7a21d803f07235")
        assertArrayEquals(expected, result)
    }

    @Test
    fun `expandMessageXmd - msg abc, len 32`() {
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = "abc".toByteArray(Charsets.US_ASCII)
        val result = HashToScalar.expandMessageXmd(msg, dst, 0x20)

        val expected = hexToBytes("d8ccab23b5985ccea865c6c97b6e5b8350e794e603b4b97902f53a8a0d605615")
        assertArrayEquals(expected, result)
    }

    @Test
    fun `expandMessageXmd - empty msg, len 128`() {
        val dst = "QUUX-V01-CS02-with-expander-SHA256-128".toByteArray(Charsets.US_ASCII)
        val msg = ByteArray(0)
        val result = HashToScalar.expandMessageXmd(msg, dst, 0x80)

        val expected = hexToBytes(
            "af84c27ccfd45d41914fdff5df25293e221afc53d8ad2ac06d5e3e29485dadbe" +
            "e0d121587713a3e0dd4d5e69e93eb7cd4f5df4cd103e188cf60cb02edc3edf18" +
            "eda8576c412b18ffb658e3dd6ec849469b979d444cf7b26911a08e63cf31f9dc" +
            "c541708d3491184472c2c29bb749d4286b004ceb5ee6b9a7fa5b646c993f0ced"
        )
        assertArrayEquals(expected, result)
    }

    @Test
    fun `hashToScalar produces a scalar in valid range`() {
        val dst = "test-dst".toByteArray(Charsets.US_ASCII)
        val msg = "test message".toByteArray(Charsets.US_ASCII)
        val scalar = HashToScalar.hashToScalar(msg, dst)

        assertTrue("Scalar must be positive", scalar.signum() > 0)
        assertTrue("Scalar must be less than order", scalar < P256Group.ORDER)
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
        assertArrayEquals(byteArrayOf(0x00), HashToScalar.i2osp(0, 1))
        assertArrayEquals(byteArrayOf(0x01), HashToScalar.i2osp(1, 1))
        assertArrayEquals(byteArrayOf(0x00, 0x20), HashToScalar.i2osp(32, 2))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x2A), HashToScalar.i2osp(42, 4))
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
