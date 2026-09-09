package com.chimali.core.data.eventsourcing

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
                val events = decider.decide(currentState, command)

                if (events.isEmpty()) return Result.success(currentState)

                try {
                    val appendResult: Result<Unit> = eventStoreRepository.append(EventKind.VAULT_ENTRY, events)
                    if (appendResult.isSuccess) {
                        var state = currentState
                        events.forEach { event ->
                            val next = decider.evolve(state, event)
                            if (state !== next) state.clearSensitiveMemory()
                            state = next
                        }

                        // Check if we should take a snapshot (threshold: 20 events)
                        if (state.sequenceNumber % SNAPSHOT_THRESHOLD == 0L) {
                            snapshotRepository.save(
                                EventKind.VAULT_ENTRY,
                                Snapshot(
                                    aggregateId = aggregateId,
                                    sequenceNumber = state.sequenceNumber,
                                    state = state,
                                    timestamp =
                                        kotlinx.datetime.Clock.System
                                            .now(),
                                ),
                            )
                        }

                        return Result.success(state)
                    }
                    currentState.clearSensitiveMemory()
                    lastResult = appendResult
                } finally {
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
