package com.chimali.core.domain.eventsourcing

import com.chimali.core.common.annotation.Req

/**
 * Thrown when an attempt is made to append an event with a sequence number that already exists
 * for a given aggregate root, indicating a concurrent update conflict.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class OptimisticConcurrencyException(
    val aggregateId: String,
    val sequenceNumber: Long,
) : RuntimeException("Optimistic concurrency conflict")
