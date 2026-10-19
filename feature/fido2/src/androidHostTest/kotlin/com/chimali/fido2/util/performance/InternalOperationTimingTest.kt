package com.chimali.fido2.util.performance

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @see NFR-PERF-030 */
class InternalOperationTimingTest {
    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `subtracts only the measured user verification interval`() {
        assertEquals(175_000_000L, adjustedInternalProcessingNanos(8_175_000_000L, 8_000_000_000L))
    }

    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `rejects negative and overlong user intervals`() {
        assertNull(adjustedInternalProcessingNanos(-1L, 0L))
        assertNull(adjustedInternalProcessingNanos(100L, -1L))
        assertNull(adjustedInternalProcessingNanos(100L, 101L))
    }

    /**
     * @see NFR-PERF-030
     * @see FR-VAL-030
     */
    @Test
    @Req("NFR-PERF-030", "FR-VAL-030")
    fun `internal processing budget is strict at 200 milliseconds`() {
        assertTrue(isBelowInternalProcessingBudget(199_999_999L))
        assertFalse(isBelowInternalProcessingBudget(200_000_000L))
    }
}
