package com.chimali.core.domain.repository

import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot

/**
 * Interface for persisting and retrieving aggregate snapshots.
 */
interface SnapshotRepository {
    /**
     * Persists a new snapshot for an aggregate root.
     * Implementations MUST enforce the snapshot retention policy (e.g., retain last two).
     *
     * @param kind The type of aggregate root.
     * @param snapshot The snapshot to save.
     */
    suspend fun <T> save(
        kind: EventKind,
        snapshot: Snapshot<T>,
    ): Result<Unit>

    /**
     * Retrieves the most recent snapshot for an aggregate root.
     *
     * @param kind The type of aggregate root.
     * @param aggregateId The unique identifier of the aggregate.
     * @return The latest snapshot or null if none exist.
     */
    suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?>
}
