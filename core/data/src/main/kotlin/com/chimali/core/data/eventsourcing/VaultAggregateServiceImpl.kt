package com.chimali.core.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultDecider
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.eventsourcing.vault.clearSensitiveMemory
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import kotlinx.datetime.Instant
import org.koin.core.annotation.Single

/**
 * Implementation of AggregateService for the VaultEntry aggregate.
 * Orchestrates hydration and command execution with retry logic.
 */
@Single
class VaultAggregateServiceImpl(
    private val eventStoreRepository: EventStoreRepository,
    private val snapshotRepository: SnapshotRepository,
    private val decider: VaultDecider = VaultDecider(),
) : AggregateService<VaultCommand, VaultState> {
    companion object {
        private const val MAX_RETRIES = 3
        private const val SNAPSHOT_THRESHOLD = 20
    }

    /**
     * Reconstructs aggregate state up to the specified point in time or latest.
     * Uses snapshot hydration when available with fallback to full event fold.
     *
     * Traceability: NFR-ARCH-050 (Deterministic Decider fold reconstruction, snapshot acceleration, full fold fallback)
     */
    @Req("NFR-ARCH-050")
    override suspend fun getState(
        aggregateId: String,
        asOf: Instant?,
    ): Result<VaultState> {
        // Try to load from snapshot if we're looking for the latest state
        if (asOf == null) {
            val snapshotResult = snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId)
            val snapshot = snapshotResult.getOrNull()

            if (snapshot != null) {
                return eventStoreRepository
                    .getEventsFrom(
                        EventKind.VAULT_ENTRY,
                        aggregateId,
                        snapshot.sequenceNumber,
                    ).map { events -> evolveOwned(snapshot.state, events) }
            }
        }

        // Fallback to full history
        return eventStoreRepository
            .getEvents(EventKind.VAULT_ENTRY, aggregateId, asOf)
            .map { events -> evolveOwned(decider.initialState, events) }
    }

    /**
     * Executes a command against the aggregate with optimistic concurrency retry logic.
     * Appends generated events, takes snapshots at configured intervals, and clears volatile memory.
     *
     * Traceability: NFR-ARCH-050 (Append-only commit, bounded optimistic concurrency retry, snapshot generation)
     * Traceability: NFR-SEC-041 (Sensitive memory zeroization on state transition)
     */
    @Req("NFR-ARCH-050", "NFR-SEC-041", "TR-AUTH-031")
    override suspend fun execute(
        aggregateId: String,
        command: VaultCommand,
    ): Result<VaultState> =
        try {
            var lastResult: Result<Unit>? = null

            repeat(MAX_RETRIES) {
                val currentStateResult = getState(aggregateId)
                if (currentStateResult.isFailure) return currentStateResult

                val currentState = currentStateResult.getOrThrow()
                var ownedState = currentState
                var events: List<VaultEvent> = emptyList()
                var stateTransferred = false
                try {
                    events = decider.decide(currentState, command)
                    if (events.isEmpty()) {
                        stateTransferred = true
                        return Result.success(currentState)
                    }

                    val appendResult: Result<Unit> = eventStoreRepository.append(EventKind.VAULT_ENTRY, events)
                    if (appendResult.isFailure) {
                        lastResult = appendResult
                        return@repeat
                    }

                    events.forEach { event ->
                        val previousState = ownedState
                        ownedState = decider.evolve(previousState, event)
                        if (previousState !== ownedState) previousState.clearSensitiveMemory()
                    }

                    // Check if we should take a snapshot (threshold: 20 events)
                    if (ownedState.sequenceNumber % SNAPSHOT_THRESHOLD == 0L) {
                        snapshotRepository.save(
                            EventKind.VAULT_ENTRY,
                            Snapshot(
                                aggregateId = aggregateId,
                                sequenceNumber = ownedState.sequenceNumber,
                                state = ownedState,
                                timestamp =
                                    kotlinx.datetime.Clock.System
                                        .now(),
                            ),
                        )
                    }

                    stateTransferred = true
                    return Result.success(ownedState)
                } finally {
                    if (!stateTransferred) ownedState.clearSensitiveMemory()
                    events.forEach { it.clearSensitiveMemory() }
                }
            }

            val exception =
                lastResult?.exceptionOrNull() ?: RuntimeException(
                    "Aggregate command execution failed after retries",
                )
            val finalResult: Result<VaultState> = Result.failure(exception)
            return finalResult
        } finally {
            command.clearSensitiveMemory()
        }

    /** Event-store reads transfer each title into the returned state, then release the event owner. */
    private fun evolveOwned(
        initial: VaultState,
        events: List<DomainEvent>,
    ): VaultState {
        var state = initial
        events.forEach { domainEvent ->
            val event = domainEvent as VaultEvent
            try {
                val next = decider.evolve(state, event)
                if (state !== next) state.clearSensitiveMemory()
                state = next
            } finally {
                event.clearSensitiveMemory()
            }
        }
        return state
    }
}
