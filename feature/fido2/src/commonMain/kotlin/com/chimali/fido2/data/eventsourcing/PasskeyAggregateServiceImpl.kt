package com.chimali.fido2.data.eventsourcing

import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyDecider
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import kotlinx.datetime.Instant

/**
 * Implementation of AggregateService for the Passkey aggregate.
 * Orchestrates hydration and command execution with retry logic.
 */
class PasskeyAggregateServiceImpl(
    private val eventStoreRepository: EventStoreRepository,
    private val snapshotRepository: SnapshotRepository,
    private val decider: PasskeyDecider = PasskeyDecider(),
) : AggregateService<PasskeyCommand, PasskeyState> {
    companion object {
        private const val MAX_RETRIES = 3
        private const val SNAPSHOT_THRESHOLD = 20
    }

    override suspend fun getState(
        aggregateId: String,
        asOf: Instant?,
    ): Result<PasskeyState> {
        // Try to load from snapshot if we're looking for the latest state
        if (asOf == null) {
            val snapshotResult = snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId)
            val snapshot = snapshotResult.getOrNull()

            if (snapshot != null) {
                return eventStoreRepository
                    .getEventsFrom(
                        EventKind.PASSKEY,
                        aggregateId,
                        snapshot.sequenceNumber,
                    ).map { events ->
                        events.fold(snapshot.state) { state, event ->
                            decider.evolve(state, event as PasskeyEvent)
                        }
                    }
            }
        }

        // Fallback to full history
        return eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, asOf).map { events ->
            events.fold(decider.initialState) { state, event ->
                decider.evolve(state, event as PasskeyEvent)
            }
        }
    }

    override suspend fun execute(
        aggregateId: String,
        command: PasskeyCommand,
    ): Result<PasskeyState> {
        var lastResult: Result<Unit>? = null

        repeat(MAX_RETRIES) {
            val currentStateResult = getState(aggregateId)
            if (currentStateResult.isFailure) return currentStateResult

            val currentState = currentStateResult.getOrThrow()
            val events = decider.decide(currentState, command)

            if (events.isEmpty()) return Result.success(currentState)

            val appendResult: Result<Unit> = eventStoreRepository.append(EventKind.PASSKEY, events)
            if (appendResult.isSuccess) {
                var state = currentState
                events.forEach { event ->
                    state = decider.evolve(state, event)
                }

                // Check if we should take a snapshot (threshold: 20 events)
                if (state.sequenceNumber % SNAPSHOT_THRESHOLD == 0L) {
                    snapshotRepository.save(
                        EventKind.PASSKEY,
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
            lastResult = appendResult
        }

        val exception =
            lastResult?.exceptionOrNull() ?: RuntimeException(
                "Passkey aggregate command execution failed after retries",
            )
        val finalResult: Result<PasskeyState> = Result.failure(exception)
        return finalResult
    }
}
