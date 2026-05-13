package com.chimali.core.domain.eventsourcing

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a materialized state of an aggregate root at a specific sequence number.
 * Used to accelerate state hydration by avoiding full historical replays.
 */
@Serializable
data class Snapshot<T>(
    /**
     * The unique identifier for the aggregate root this snapshot represents.
     */
    val aggregateId: String,
    /**
     * The sequence number of the last event processed into this snapshot.
     */
    val sequenceNumber: Long,
    /**
     * The reconstructed state of the aggregate root.
     */
    val state: T,
    /**
     * The point in time when this snapshot was created.
     */
    val timestamp: Instant,
)
