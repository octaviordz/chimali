package com.chimali.core.domain.eventsourcing

import kotlin.test.Test
import kotlin.test.assertEquals

class OptimisticConcurrencyExceptionPrivacyTest {
    @Test
    fun `message does not expose aggregate identifier or sequence`() {
        val exception = OptimisticConcurrencyException("synthetic-aggregate-marker", 41)

        assertEquals("Optimistic concurrency conflict", exception.message)
        assertEquals("synthetic-aggregate-marker", exception.aggregateId)
        assertEquals(41L, exception.sequenceNumber)
    }
}
