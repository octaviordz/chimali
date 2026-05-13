package com.chimali.core.domain.eventsourcing

import kotlinx.datetime.Instant

/**
 * Base interface for all domain events in the event sourcing architecture.
 */
interface DomainEvent {
    /**
     * The unique identifier for the aggregate root this event belongs to.
     */
    val aggregateId: String

    /**
     * The position of this event within the aggregate's event stream.
     */
    val sequenceNumber: Long

    /**
     * The point in time when this event occurred.
     */
    val timestamp: Instant
}
