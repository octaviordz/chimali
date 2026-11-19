package com.chimali.core.domain.repository

import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import kotlinx.datetime.Instant

/**
 * Interface for persisting and retrieving domain events.
 * Implementations are responsible for encryption and serialization.
 */
interface EventStoreRepository {
    /**
     * Appends a list of events to the store for a specific aggregate root.
     *
     * @param kind The type of aggregate root.
     * @param events The events to append.
     * @return Success if all events were appended, or failure (e.g., OptimisticConcurrencyException).
     *
     * Ownership: the repository borrows each event and its mutable fields only until this call
     * completes. It must not mutate or erase caller-owned event buffers; the caller remains
     * responsible for their terminal cleanup after the call returns or throws.
     */
    suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
    ): Result<Unit>

    /**
     * Retrieves the complete event history for an aggregate root.
     *
     * @param kind The type of aggregate root.
     * @param aggregateId The unique identifier of the aggregate.
     * @param upTo Optional timestamp to limit the retrieved events (for temporal queries).
     * @return An ordered list of events.
     *
     * Ownership: successful results transfer fresh event owners to the caller. A failed or
     * cancelled read transfers no partial list; implementations erase any decoded prefix before
     * returning or rethrowing.
     */
    suspend fun getEvents(
        kind: EventKind,
        aggregateId: String,
        upTo: Instant? = null,
    ): Result<List<DomainEvent>>

    /**
     * Retrieves events for an aggregate root starting from a specific sequence number.
     * Used for hydration starting from a snapshot.
     *
     * @param kind The type of aggregate root.
     * @param aggregateId The unique identifier of the aggregate.
     * @param fromSequence The sequence number to start from (exclusive).
     * @return An ordered list of subsequent events.
     *
     * Ownership: successful results transfer fresh event owners to the caller. A failed or
     * cancelled read transfers no partial list; implementations erase any decoded prefix before
     * returning or rethrowing.
     */
    suspend fun getEventsFrom(
        kind: EventKind,
        aggregateId: String,
        fromSequence: Long,
    ): Result<List<DomainEvent>>
}
