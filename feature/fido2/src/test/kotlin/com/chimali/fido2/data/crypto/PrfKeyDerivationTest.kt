package com.chimali.fido2.data.crypto

import com.chimali.fido2.domain.model.PrfExtensionInput
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * T040 — Unit tests for [PrfKeyDerivation].
 *
 * Verifies:
 * - [PrfKeyDerivation.derive] output is exactly 32 bytes
 * - [PrfKeyDerivation.derive] is deterministic (same inputs → same output)
 * - [PrfKeyDerivation.derive] returns null when the master seed is unavailable
 * - [PrfKeyDerivation.deriveAll] returns [PrfExtensionOutput] with output1 and output2
 * - [PrfKeyDerivation.deriveAll] returns null (T049) if seed unavailable
 */
class PrfKeyDerivationTest {
    private val mockHmacProcessor: HmacSecretProcessor = mockk()
    private val prfKeyDerivation = PrfKeyDerivation(mockHmacProcessor)

    private val salt1 = ByteArray(32) { 0x01 }
    private val salt2 = ByteArray(32) { 0x02 }
    private val credentialId = "test-credential-id"
    private val fakeOutput32 = ByteArray(32) { 0xAB.toByte() }
    private val fakeOutput32b = ByteArray(32) { 0xCD.toByte() }

    // ── derive() ─────────────────────────────────────────────────────────────

    @Test
    fun derive_outputIs32Bytes() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns fakeOutput32

            val result = prfKeyDerivation.derive(salt1, credentialId)

            assertNotNull(result)
            assertEquals(32, result.size, "derive() output must be exactly 32 bytes")
        }

    @Test
    fun derive_isDeterministic() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns fakeOutput32

            val result1 = prfKeyDerivation.derive(salt1, credentialId)
            val result2 = prfKeyDerivation.derive(salt1, credentialId)

            assertNotNull(result1)
            assertNotNull(result2)
            assertEquals(
                result1.toList(),
                result2.toList(),
                "Same salt + credentialId must produce the same output",
            )
        }

    @Test
    fun derive_returnsNull_whenSeedUnavailable() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns null

            val result = prfKeyDerivation.derive(salt1, credentialId)

            assertNull(result, "derive() must return null when master seed is unavailable (T049)")
        }

    // ── deriveAll() ─────────────────────────────────────────────────────────

    @Test
    fun deriveAll_singleSalt_returnsOutput1Only() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns fakeOutput32

            val input = PrfExtensionInput.single(salt1)
            val output = prfKeyDerivation.deriveAll(input, credentialId)

            assertNotNull(output)
            assertEquals(fakeOutput32.toList(), output.output1.toList())
            assertNull(output.output2, "Single-salt input must not produce output2")
        }

    @Test
    fun deriveAll_dualSalt_returnsBothOutputs() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns fakeOutput32
            coEvery { mockHmacProcessor.process(credentialId, salt2) } returns fakeOutput32b

            val input = PrfExtensionInput.dual(salt1, salt2)
            val output = prfKeyDerivation.deriveAll(input, credentialId)

            assertNotNull(output)
            assertEquals(fakeOutput32.toList(), output.output1.toList())
            assertNotNull(output.output2)
            assertEquals(fakeOutput32b.toList(), output.output2.toList())
        }

    @Test
    fun deriveAll_returnsNull_whenSeedUnavailable_T049() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns null

            val input = PrfExtensionInput.single(salt1)
            val output = prfKeyDerivation.deriveAll(input, credentialId)

            assertNull(output, "T049: deriveAll() must return null without failing ceremony when seed unavailable")
        }

    @Test
    fun deriveAll_toCborMap_hasIntegerKeys() =
        runTest {
            coEvery { mockHmacProcessor.process(credentialId, salt1) } returns fakeOutput32
            coEvery { mockHmacProcessor.process(credentialId, salt2) } returns fakeOutput32b

            val input = PrfExtensionInput.dual(salt1, salt2)
            val output = prfKeyDerivation.deriveAll(input, credentialId)

            assertNotNull(output)
            val cborMap = output.toCborMap()
            assertEquals(2, cborMap.size)
            assertNotNull(cborMap[1], "key 1 must be present for output1")
            assertNotNull(cborMap[2], "key 2 must be present for output2")
        }
}
