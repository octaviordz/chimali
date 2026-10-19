package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * T020 — Ownership and borrowed-buffer checks for the existing hmac-secret consumer.
 */
@Req("NFR-SEC-040")
class HmacSecretProcessorZeroingTest {
    private val masterSeedProvider: MasterSeedProvider = mockk()
    private val processor = HmacSecretProcessor(masterSeedProvider)

    private fun stubSessionOwner() {
        coEvery {
            masterSeedProvider.withSessionOwner(any(), any(), any<suspend (Long) -> ByteArray?>())
        } coAnswers {
            thirdArg<suspend (Long) -> ByteArray?>().invoke(1L)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `process preserves borrowed salt`() =
        runTest {
            val rootCopy = ByteArray(32) { 0xAA.toByte() }
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy

            val salt = ByteArray(64) { it.toByte() }
            val borrowedSalt = salt.copyOf()
            val credentialId = "test-credential"
            val expected = referenceOutput(ByteArray(32) { 0xAA.toByte() }, credentialId, borrowedSalt)

            val actual = processor.process(credentialId, salt)

            assertContentEquals(expected, actual)
            assertContentEquals(borrowedSalt, salt, "Borrowed caller salt must remain unchanged")
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `process erases its owned root copy`() =
        runTest {
            val rootCopy = ByteArray(32) { 0xAA.toByte() }
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy

            processor.process("test-credential", ByteArray(32) { 0x01.toByte() })

            assertTrue(rootCopy.all { it == 0.toByte() }, "The owned root copy must be erased")
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `process erases result when session owner cancels during result handoff`() =
        runTest {
            val rootCopy = ByteArray(32) { 0x5A.toByte() }
            val salt = ByteArray(32) { 0x26.toByte() }
            var discardedOutput: ByteArray? = null
            coEvery {
                masterSeedProvider.withSessionOwner(any(), any(), any<suspend (Long) -> ByteArray?>())
            } coAnswers {
                discardedOutput = thirdArg<suspend (Long) -> ByteArray?>().invoke(1L)
                throw CancellationException("synthetic cancellation at owner handoff")
            }
            coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy

            assertFailsWith<CancellationException> { processor.process("synthetic-credential", salt) }

            assertTrue(discardedOutput?.all { it == 0.toByte() } == true, "Discarded session result must be erased")
            assertTrue(rootCopy.all { it == 0.toByte() }, "Owned root copy must also be erased")
            assertTrue(salt.any { it != 0.toByte() }, "Borrowed salt remains unchanged")
        }

    private fun referenceOutput(
        root: ByteArray,
        credentialId: String,
        salts: ByteArray,
    ): ByteArray {
        val secret = hmac(root, "hmac-secret".toByteArray() + credentialId.toByteArray())
        return try {
            ByteArray(salts.size).also { output ->
                outputFirstHalf(secret, salts, output, 0)
                outputFirstHalf(secret, salts, output, 32)
            }
        } finally {
            Arrays.fill(root, 0)
            Arrays.fill(secret, 0)
        }
    }

    private fun outputFirstHalf(
        secret: ByteArray,
        salts: ByteArray,
        output: ByteArray,
        offset: Int,
    ) {
        val salt = salts.copyOfRange(offset, offset + 32)
        try {
            hmac(secret, salt).copyInto(output, offset)
        } finally {
            Arrays.fill(salt, 0)
        }
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
