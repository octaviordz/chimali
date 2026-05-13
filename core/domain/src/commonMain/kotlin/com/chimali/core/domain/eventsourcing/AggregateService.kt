package com.chimali.core.domain.eventsourcing

import kotlinx.datetime.Instant

/**
 * High-level service interface for managing an aggregate root using event sourcing.
 * Orchestrates the hydration, command execution, and event persistence.
 *
 * @param Command The type of commands supported by the aggregate.
 * @param State The type of the aggregate root state.
 */
interface AggregateService<Command, State> {
    /**
     * Reconstructs the current state of an aggregate by replaying its event stream.
     *
     * @param aggregateId The unique identifier of the aggregate root.
     * @param asOf Optional timestamp to reconstruct state at a specific point in history.
     * @return The reconstructed state or failure if the aggregate does not exist.
     */
    suspend fun getState(
        aggregateId: String,
        asOf: Instant? = null,
    ): Result<State>

    /**
     * Executes a command against an aggregate.
     * This method handles:
     * 1. Hydrating the current state.
     * 2. Deciding which events to emit.
     * 3. Appending events to the store.
     * 4. Retrying automatically on concurrency conflicts.
     *
     * @param aggregateId The unique identifier of the aggregate root.
     * @param command The command to execute.
     * @return The new state of the aggregate after command execution.
     */
    suspend fun execute(
        aggregateId: String,
        command: Command,
    ): Result<State>
}
