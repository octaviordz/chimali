package com.chimali.fido2.data.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HdkKeyPairCopyOwnershipTest {
    @Test
    fun `constructor failure erases both staged copies and preserves sources`() {
        val privateSource = ByteArray(32) { (it + 1).toByte() }
        val publicSource = ByteArray(65) { (it + 2).toByte() }
        var privateCopy: ByteArray? = null
        var publicCopy: ByteArray? = null

        assertFailsWith<IllegalStateException> {
            copyOwnedHdkKeyPair(
                privateSource,
                publicSource,
                copyPrivate = { privateSource.copyOf().also { privateCopy = it } },
                copyPublic = { publicSource.copyOf().also { publicCopy = it } },
                create = { _, _ -> error("injected pair construction failure") },
            )
        }

        assertTrue(requireNotNull(privateCopy).all { it == 0.toByte() })
        assertTrue(requireNotNull(publicCopy).all { it == 0.toByte() })
        assertContentEquals(ByteArray(32) { (it + 1).toByte() }, privateSource)
        assertContentEquals(ByteArray(65) { (it + 2).toByte() }, publicSource)
    }

    @Test
    fun `public copy failure erases an already staged private copy`() {
        val privateSource = ByteArray(32) { (it + 1).toByte() }
        val publicSource = ByteArray(65) { (it + 2).toByte() }
        var privateCopy: ByteArray? = null

        assertFailsWith<IllegalStateException> {
            copyOwnedHdkKeyPair(
                privateSource,
                publicSource,
                copyPrivate = { privateSource.copyOf().also { privateCopy = it } },
                copyPublic = { error("injected public copy failure") },
            )
        }

        assertTrue(requireNotNull(privateCopy).all { it == 0.toByte() })
        assertContentEquals(ByteArray(32) { (it + 1).toByte() }, privateSource)
        assertContentEquals(ByteArray(65) { (it + 2).toByte() }, publicSource)
    }
}
