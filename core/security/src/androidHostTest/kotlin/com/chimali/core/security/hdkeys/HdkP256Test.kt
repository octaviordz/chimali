package com.chimali.core.security.hdkeys

import com.chimali.core.common.annotation.Req
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

@Req("NFR-SEC-041", "TR-AUTH-031")
class HdkP256Test {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `frozen independent child vectors match full preimage digest factor and key outputs`() {
        val localSecret = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToBytes()
        val parentScalar = ByteArray(32).also { it[31] = 1 }
        val parentPublic = P256Group.serializeElement(P256Group.G)
        val vectors =
            listOf(
                Vector(
                    index = 0u,
                    preimage =
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                            "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f500000000",
                    digest = "359cff1983f9506ac5c5ff33bf41b9a815ef52fcd2b44f4fc9445e6a4031c4c7",
                    factor = "359cff1983f9506ac5c5ff33bf41b9a815ef52fcd2b44f4fc9445e6a4031c4c8",
                    childPrivate = "359cff1983f9506ac5c5ff33bf41b9a815ef52fcd2b44f4fc9445e6a4031c4c8",
                    childPublic =
                        "048ad037ee17ebb558f330e5d92d6a17d46a1421aabd7be108281c9f8bc0a0759" +
                            "8e4d09918e309f376bb8c2486fdfa1f93e28f1c898c081e5ba937b926a9e9b3c0",
                ),
                Vector(
                    index = 1u,
                    preimage =
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                            "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f500000001",
                    digest = "df6acd780f47e088556c3fdac5863753534ad039c2ab3addeff878935aeecd25",
                    factor = "df6acd780f47e088556c3fdac5863753534ad039c2ab3addeff878935aeecd26",
                    childPrivate = "df6acd780f47e088556c3fdac5863753534ad039c2ab3addeff878935aeecd26",
                    childPublic =
                        "0432cd353f734bc6ffa515032f7aef08cec4575b8f77cb9bc54cba2736b95874" +
                            "0593d33565fbf526d55c33b05ff0b85a02d958e77c94004cb82bbf951575a27107",
                ),
                Vector(
                    index = 0x80000000u,
                    preimage =
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                            "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f580000000",
                    digest = "00dbecc9aa6dd4f438205095448437df8f62ab4927847639827b8ed3a292cd29",
                    factor = "00dbecc9aa6dd4f438205095448437df8f62ab4927847639827b8ed3a292cd2a",
                    childPrivate = "00dbecc9aa6dd4f438205095448437df8f62ab4927847639827b8ed3a292cd2a",
                    childPublic =
                        "0432bb801a140f255ae8702d80d81ae5b03d35cb6a243b8e81b5a1713eb83151" +
                            "86ea10626c0fa5cb285ae1e09d97f8226e87a479080ae4ea013ed157ec6938882d",
                ),
                Vector(
                    index = UInt.MAX_VALUE,
                    preimage =
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                            "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
                            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5ffffffff",
                    digest = "62f8113d5efc75083d4b7d3139d6613164619ad9188417f8e2eef3e65e87d224",
                    factor = "62f8113d5efc75083d4b7d3139d6613164619ad9188417f8e2eef3e65e87d225",
                    childPrivate = "62f8113d5efc75083d4b7d3139d6613164619ad9188417f8e2eef3e65e87d225",
                    childPublic =
                        "04a9cd2fcb8434f231fedd089ee1a5778a79932579f3c7f4a9427ec899115d6" +
                            "ccb9d5dab670fb62a550dd68c3ae7516f90d8cbbb49f8c652d6a2c4e21bfd268c44",
                ),
            )

        try {
            vectors.forEach { vector ->
                val beforeSecret = localSecret.copyOf()
                val beforeParentScalar = parentScalar.copyOf()
                val beforeParentPublic = parentPublic.copyOf()
                val expectedPreimage = vector.preimage.hexToBytes()
                val actualPreimage = localSecret + parentPublic + vector.index.toBigEndianBytes()
                val expectedDigest = vector.digest.hexToBytes()
                val expectedFactor = vector.factor.hexToBytes()
                val result =
                    HdkP256.deriveChild(
                        localSecret = localSecret,
                        parentPrivateScalar = parentScalar,
                        parentPublicKey = parentPublic,
                        index = vector.index,
                    )
                try {
                    assertContentEquals(expectedPreimage, actualPreimage)
                    assertContentEquals(expectedDigest, MessageDigest.getInstance("SHA-256").digest(actualPreimage))
                    assertContentEquals(expectedFactor, expectedFactorFrom(expectedDigest))
                    assertContentEquals(expectedFactor, result.factor)
                    assertContentEquals(vector.childPrivate.hexToBytes(), result.childPrivateScalar)
                    assertContentEquals(vector.childPublic.hexToBytes(), result.childPublicKey)
                    assertContentEquals(beforeSecret, localSecret)
                    assertContentEquals(beforeParentScalar, parentScalar)
                    assertContentEquals(beforeParentPublic, parentPublic)
                    assertNotSame(localSecret, result.factor)
                    assertNotSame(parentScalar, result.factor)
                    assertNotSame(parentPublic, result.childPublicKey)
                } finally {
                    result.factor.fill(0)
                    result.childPrivateScalar.fill(0)
                }
            }
        } finally {
            localSecret.fill(0)
            parentScalar.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `child result preserves parent private public correspondence`() {
        val localSecret = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToBytes()
        val parentScalar = ByteArray(32).also { it[31] = 1 }
        val parentPublic = P256Group.serializeElement(P256Group.G)
        val result = HdkP256.deriveChild(localSecret, parentScalar, parentPublic, 0u)
        try {
            val scalar = BigInteger(1, result.childPrivateScalar)
            val expectedPublic = P256Group.serializeElement(P256Group.scalarBaseMult(scalar))
            assertContentEquals(expectedPublic, result.childPublicKey)
        } finally {
            result.factor.fill(0)
            result.childPrivateScalar.fill(0)
            localSecret.fill(0)
            parentScalar.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `derivation rejects malformed secrets scalars points and mismatched parent pair`() {
        val secret = ByteArray(32) { 0x71 }
        val scalarOne = ByteArray(32).also { it[31] = 1 }
        val generator = P256Group.serializeElement(P256Group.G)
        try {
            assertFailsWith<IllegalArgumentException> { HdkP256.deriveChild(ByteArray(31), scalarOne, generator, 0u) }
            assertFailsWith<IllegalArgumentException> { HdkP256.deriveChild(ByteArray(33), scalarOne, generator, 0u) }
            assertFailsWith<IllegalArgumentException> { HdkP256.deriveChild(secret, ByteArray(31), generator, 0u) }
            assertFailsWith<IllegalArgumentException> { HdkP256.deriveChild(secret, ByteArray(32), generator, 0u) }
            assertFailsWith<IllegalArgumentException> {
                HdkP256.deriveChild(secret, scalarOne, generator.copyOf(64), 0u)
            }
            assertFailsWith<IllegalArgumentException> {
                HdkP256.deriveChild(secret, scalarOne, ByteArray(65).also { it[0] = 0x04 }, 0u)
            }
            assertFailsWith<IllegalArgumentException> {
                HdkP256.deriveChild(
                    secret,
                    scalarOne,
                    P256Group.serializeElement(P256Group.scalarBaseMult(BigInteger.TWO)),
                    0u,
                )
            }
        } finally {
            secret.fill(0)
            scalarOne.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `repeated child composition preserves the scalar and point relation`() {
        val secretOne = "101112131415161718191a1b1c1d1e1f000102030405060708090a0b0c0d0e0f".hexToBytes()
        val secretTwo = "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f".hexToBytes()
        val parentScalar = ByteArray(32).also { it[31] = 1 }
        val parentPublic = P256Group.serializeElement(P256Group.G)
        val first = HdkP256.deriveChild(secretOne, parentScalar, parentPublic, 7u)
        val second = HdkP256.deriveChild(secretTwo, first.childPrivateScalar, first.childPublicKey, 9u)
        try {
            val composedFactor =
                BigInteger(1, first.factor)
                    .multiply(BigInteger(1, second.factor))
                    .mod(P256Group.ORDER)
            val expectedPrivate = P256Group.serializeScalar(composedFactor)
            val expectedPublic = P256Group.serializeElement(P256Group.scalarBaseMult(composedFactor))
            assertContentEquals(expectedPrivate, second.childPrivateScalar)
            assertContentEquals(expectedPublic, second.childPublicKey)
        } finally {
            first.factor.fill(0)
            first.childPrivateScalar.fill(0)
            second.factor.fill(0)
            second.childPrivateScalar.fill(0)
            parentScalar.fill(0)
            secretOne.fill(0)
            secretTwo.fill(0)
        }
    }

    private data class Vector(
        val index: UInt,
        val preimage: String,
        val digest: String,
        val factor: String,
        val childPrivate: String,
        val childPublic: String,
    )

    private fun expectedFactorFrom(digest: ByteArray): ByteArray =
        BigInteger(1, digest)
            .mod(P256Group.ORDER - BigInteger.ONE)
            .add(BigInteger.ONE)
            .let(P256Group::serializeScalar)

    private fun UInt.toBigEndianBytes(): ByteArray =
        byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            toByte(),
        )

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
