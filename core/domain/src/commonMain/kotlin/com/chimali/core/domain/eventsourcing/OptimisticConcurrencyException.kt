package com.chimali.core.domain.eventsourcing

/**
 * Thrown when an attempt is made to append an event with a sequence number that already exists
 * for a given aggregate root, indicating a concurrent update conflict.
 */
class OptimisticConcurrencyException(
    val aggregateId: String,
    val sequenceNumber: Long,
    message: String = "Concurrency conflict for aggregate $aggregateId at sequence $sequenceNumber",
) : RuntimeException(message)
