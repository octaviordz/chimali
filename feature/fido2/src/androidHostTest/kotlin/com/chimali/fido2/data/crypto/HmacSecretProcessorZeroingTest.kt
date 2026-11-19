package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

    private class SyntheticSensitiveExtension

    @Test
    fun `unsupported extension diagnostics omit runtime class description`() =
        runTest {
            val diagnostics = CapturedDiagnostics()
            try {
                assertNull(processor.process("credential", SyntheticSensitiveExtension()))
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(SyntheticSensitiveExtension::class.simpleName!!)
            } finally {
                diagnostics.close()
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
    fun `sensitive CBOR salt owner remains readable through HMAC processing then request cleanup erases it`() =
        runTest {
            val codec = CborCodec()
            val originalSalt = ByteArray(32) { (it + 1).toByte() }
            val encodedRequest =
                codec.encodeToFido2Format(
                    mapOf(
                        "extensions" to
                            mapOf(
                                HmacSecretProcessor.EXTENSION_KEY to
                                    mapOf("saltEnc" to originalSalt),
                            ),
                    ),
                )
            val decodedRequest = codec.decodeSensitiveRequest(encodedRequest)
            var output: ByteArray? = null
            val rootCopy = ByteArray(32) { 0x41.toByte() }
            try {
                stubSessionOwner()
                coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy

                val extensions = decodedRequest["extensions"] as Map<*, *>
                val extension = extensions[HmacSecretProcessor.EXTENSION_KEY] as Map<*, *>
                val decodedSalt = extension["saltEnc"] as ByteArray

                assertContentEquals(originalSalt, decodedSalt, "Sensitive decoder must retain the active input")
                output = processor.process("synthetic-credential", extension)
                assertTrue(output != null, "HMAC processing must consume the decoded salt")
                assertTrue(decodedSalt.any { it != 0.toByte() }, "The request owner remains alive during use")
                assertTrue(rootCopy.all { it == 0.toByte() }, "The processor must erase its owned root copy")

                codec.eraseDecodedRequestValues(decodedRequest)
                assertTrue(decodedSalt.all { it == 0.toByte() }, "Request terminal cleanup must erase the salt owner")
            } finally {
                output?.fill(0)
                codec.eraseDecodedRequestValues(decodedRequest)
                encodedRequest.fill(0)
                originalSalt.fill(0)
            }
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
        val domainSeparator = "hmac-secret".toByteArray()
        val credentialBytes = credentialId.toByteArray()
        val credentialInput = domainSeparator + credentialBytes
        domainSeparator.fill(0)
        credentialBytes.fill(0)
        var secret: ByteArray? = null
        return try {
            secret = hmac(root, credentialInput)
            ByteArray(salts.size).also { output ->
                outputFirstHalf(requireNotNull(secret), salts, output, 0)
                if (salts.size > 32) outputFirstHalf(requireNotNull(secret), salts, output, 32)
            }
        } finally {
            credentialInput.fill(0)
            root.fill(0)
            secret?.fill(0)
        }
    }

    private fun outputFirstHalf(
        secret: ByteArray,
        salts: ByteArray,
        output: ByteArray,
        offset: Int,
    ) {
        val salt = salts.copyOfRange(offset, offset + 32)
        var hmacOutput: ByteArray? = null
        try {
            hmacOutput = hmac(secret, salt)
            requireNotNull(hmacOutput).copyInto(output, destinationOffset = offset)
        } finally {
            Arrays.fill(salt, 0)
            hmacOutput?.fill(0)
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
