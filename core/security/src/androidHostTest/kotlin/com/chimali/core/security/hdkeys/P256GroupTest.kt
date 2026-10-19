package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Req("NFR-SEC-041", "TR-AUTH-031")
class P256GroupTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `generator uses canonical uncompressed SEC1 encoding`() {
        val expected =
            "04" +
                "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"

        assertEquals(65, P256Group.serializeElement(P256Group.G).size)
        assertEquals(expected, P256Group.serializeElement(P256Group.G).toHex())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `SEC1 point round trip preserves the borrowed encoding`() {
        val borrowed = P256Group.serializeElement(P256Group.G)
        val original = borrowed.copyOf()

        val decoded = P256Group.deserializeElement(borrowed)

        assertEquals(P256Group.G, decoded)
        assertContentEquals(original, borrowed)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `scalar serialization rejects zero and the group order`() {
        assertFailsWith<IllegalArgumentException> { P256Group.serializeScalar(BigInteger.ZERO) }
        assertFailsWith<IllegalArgumentException> { P256Group.serializeScalar(P256Group.ORDER) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `scalar deserialization requires exactly 32 bytes`() {
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeScalar(byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeScalar(ByteArray(33)) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `scalar deserialization rejects zero and the group order`() {
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeScalar(ByteArray(32)) }
        assertFailsWith<IllegalArgumentException> {
            P256Group.deserializeScalar(P256Group.ORDER.toFixedWidthBytes())
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `scalar boundaries serialize canonically without truncation`() {
        val one = P256Group.serializeScalar(BigInteger.ONE)
        val largestValid = P256Group.serializeScalar(P256Group.ORDER - BigInteger.ONE)

        assertEquals(P256Group.SCALAR_LENGTH, one.size)
        assertEquals("00".repeat(31) + "01", one.toHex())
        assertEquals(P256Group.SCALAR_LENGTH, largestValid.size)
        assertEquals(BigInteger.ONE, P256Group.deserializeScalar(one))
        assertEquals(P256Group.ORDER - BigInteger.ONE, P256Group.deserializeScalar(largestValid))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `point deserialization rejects infinity and invalid coordinates`() {
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeElement(byteArrayOf(0x00)) }
        val invalidPoint = ByteArray(P256Group.ELEMENT_UNCOMPRESSED_LENGTH).also { it[0] = 0x04 }
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeElement(invalidPoint) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `scalar multiplication rejects zero and out-of-range scalar`() {
        assertFailsWith<IllegalArgumentException> { P256Group.scalarMult(P256Group.G, BigInteger.ZERO) }
        assertFailsWith<IllegalArgumentException> { P256Group.scalarMult(P256Group.G, P256Group.ORDER) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `point deserialization rejects compressed and malformed encodings`() {
        val generator = P256Group.serializeElement(P256Group.G)
        val compressed = byteArrayOf(0x03) + generator.copyOfRange(1, 33)
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeElement(compressed) }
        val malformed = ByteArray(P256Group.ELEMENT_UNCOMPRESSED_LENGTH).also { it[0] = 0x04 }
        assertFailsWith<IllegalArgumentException> { P256Group.deserializeElement(malformed) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun BigInteger.toFixedWidthBytes(): ByteArray {
        val encoded = toByteArray().let { if (it.size == 33 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return ByteArray(P256Group.SCALAR_LENGTH - encoded.size) + encoded
    }
}
