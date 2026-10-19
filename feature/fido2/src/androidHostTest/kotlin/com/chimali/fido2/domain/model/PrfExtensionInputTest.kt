package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T039 — Unit tests for [PrfExtensionInput] validation.
 *
 * Per WebAuthn L3 §10.1.4 / CTAP2 §12.4:
 *   - Empty list → throws (at least 1 salt required)
 *   - Single 32-byte salt → passes
 *   - Two 32-byte salts → passes
 *   - Three salts → throws (max 2)
 *   - Wrong-size salt → throws (must be 32 bytes)
 */
class PrfExtensionInputTest {
    private val salt32 = ByteArray(32) { it.toByte() }
    private val salt32b = ByteArray(32) { (it + 1).toByte() }

    // ── Valid constructions ───────────────────────────────────────────────────

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun singleSalt_passes() {
        val input = PrfExtensionInput.single(salt32)
        assertNotNull(input)
        assertEquals(1, input.salts.size)
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun twoSalts_passes() {
        val input = PrfExtensionInput.dual(salt32, salt32b)
        assertNotNull(input)
        assertEquals(2, input.salts.size)
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun salt1_accessor_returnsFirstSalt() {
        val input = PrfExtensionInput.single(salt32)
        assertEquals(salt32.toList(), input.salt1.toList())
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun salt2_accessor_returnsSecondSalt_whenPresent() {
        val input = PrfExtensionInput.dual(salt32, salt32b)
        assertEquals(salt32b.toList(), input.salt2?.toList())
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun salt2_accessor_returnsNull_forSingleSaltInput() {
        val input = PrfExtensionInput.single(salt32)
        assertNull(input.salt2)
    }

    // ── Invalid constructions ─────────────────────────────────────────────────

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun emptyList_throws() {
        assertFailsWith<IllegalArgumentException>(
            "Empty salt list must throw",
        ) {
            PrfExtensionInput(emptyList())
        }
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun threeSalts_throws() {
        val thirdSalt = ByteArray(32) { 0xFF.toByte() }
        assertFailsWith<IllegalArgumentException>(
            "More than ${PrfExtensionInput.MAX_SALTS} salts must throw",
        ) {
            PrfExtensionInput(listOf(salt32, salt32b, thirdSalt))
        }
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun wrongSizeSalt_31bytes_throws() {
        val shortSalt = ByteArray(31)
        assertFailsWith<IllegalArgumentException>("31-byte salt must throw") {
            PrfExtensionInput(listOf(shortSalt))
        }
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun wrongSizeSalt_33bytes_throws() {
        val longSalt = ByteArray(33)
        assertFailsWith<IllegalArgumentException>("33-byte salt must throw") {
            PrfExtensionInput(listOf(longSalt))
        }
    }

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun wrongSizeSalt_empty_throws() {
        assertFailsWith<IllegalArgumentException>("Empty salt must throw") {
            PrfExtensionInput(listOf(byteArrayOf()))
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    /**
     * @see NFR-SEC-010
     * @see FR-VAL-020
     */
    @Test
    @Req("NFR-SEC-010", "FR-VAL-020")
    fun constants_haveCorrectValues() {
        assertEquals(1, PrfExtensionInput.MIN_SALTS)
        assertEquals(2, PrfExtensionInput.MAX_SALTS)
        assertEquals(32, PrfExtensionInput.SALT_SIZE_BYTES)
        assertEquals(32, PrfExtensionInput.MAX_OUTPUT_BYTES)
    }
}
