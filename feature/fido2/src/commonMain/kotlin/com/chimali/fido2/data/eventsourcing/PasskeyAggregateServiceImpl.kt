package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyDecider
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.domain.valueobject.CredentialId
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
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
        private const val INVALID_COMMAND_MESSAGE = "Invalid passkey command"
    }

    /** Reconstructs owned aggregate state from the latest snapshot and subsequent events. */
    @Req("FR-003", "FR-013", "NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getState(
        aggregateId: String,
        asOf: Instant?,
    ): Result<PasskeyState> =
        try {
            getStateOwned(aggregateId, asOf)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IllegalArgumentException) {
            Result.failure(MalformedPasskeyAggregateException())
        } catch (_: ClassCastException) {
            Result.failure(MalformedPasskeyAggregateException())
        } catch (_: IllegalStateException) {
            Result.failure(MalformedPasskeyAggregateException())
        }

    // Ownership scopes are intentionally nested so snapshot fallback and transfer cleanup stay paired.
    @Suppress("NestedBlockDepth")
    private suspend fun getStateOwned(
        aggregateId: String,
        asOf: Instant?,
    ): Result<PasskeyState> {
        // Try to load from snapshot if we're looking for the latest state
        if (asOf == null) {
            val snapshotResult = snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId)
            val snapshot = snapshotResult.getOrNull()

            if (snapshot != null) {
                val sourceState = snapshot.state
                val initialState =
                    try {
                        validateSnapshot(snapshot, aggregateId)
                        sourceState.copyWithOwnedMetadata()
                    } catch (_: InvalidPasskeyStateException) {
                        null
                    } finally {
                        sourceState.eraseSensitiveMetadata()
                    }

                if (initialState != null) {
                    var stateTransferred = false
                    try {
                        val eventsResult =
                            eventStoreRepository.getEventsFrom(
                                EventKind.PASSKEY,
                                aggregateId,
                                snapshot.sequenceNumber,
                            )
                        return eventsResult.fold(
                            onSuccess = { events ->
                                val reconstructed = foldOwnedEvents(initialState, events, aggregateId)
                                stateTransferred = true
                                Result.success(reconstructed)
                            },
                            onFailure = { failure -> Result.failure(failure) },
                        )
                    } finally {
                        if (!stateTransferred) initialState.eraseSensitiveMetadata()
                    }
                }
            }
        }

        return eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, asOf).fold(
            onSuccess = { events -> Result.success(foldOwnedEvents(PasskeyState(), events, aggregateId)) },
            onFailure = { failure -> Result.failure(failure) },
        )
    }

    private fun foldOwnedEvents(
        initialState: PasskeyState,
        events: List<DomainEvent>,
        aggregateId: String,
        eraseEvents: Boolean = true,
    ): PasskeyState {
        var state = initialState
        var returnedState = false
        try {
            validateState(state, aggregateId, allowInitial = true)
            events.forEach { domainEvent ->
                val event = domainEvent as? PasskeyEvent ?: invalidPasskeyState()
                if (event.aggregateId != aggregateId || state.sequenceNumber == Long.MAX_VALUE) {
                    invalidPasskeyState()
                }
                if (event.sequenceNumber != state.sequenceNumber + 1L) invalidPasskeyState()
                val evolved =
                    try {
                        decider.evolve(state, event)
                    } catch (_: IllegalArgumentException) {
                        invalidPasskeyState()
                    }
                state.eraseSensitiveMetadata()
                state = evolved
                validateState(state, aggregateId, allowInitial = false)
            }
            returnedState = true
            return state
        } finally {
            if (!returnedState) state.eraseSensitiveMetadata()
            if (eraseEvents) {
                events.filterIsInstance<PasskeyEvent.Registered>().forEach { it.eraseSensitiveMetadata() }
            }
        }
    }

    private fun validateSnapshot(
        snapshot: Snapshot<PasskeyState>,
        aggregateId: String,
    ) {
        if (snapshot.aggregateId != aggregateId || snapshot.sequenceNumber != snapshot.state.sequenceNumber) {
            invalidPasskeyState()
        }
        validateState(snapshot.state, aggregateId, allowInitial = false)
    }

    private fun validateState(
        state: PasskeyState,
        aggregateId: String,
        allowInitial: Boolean,
    ) {
        if (state.isInitialState()) {
            if (allowInitial) return
            invalidPasskeyState()
        }
        if (state.id != aggregateId || state.sequenceNumber <= 0L || state.publicKey.isBlank()) {
            invalidPasskeyState()
        }
        try {
            CredentialKeyDescriptor(
                CredentialId.fromByteArray(state.credentialId),
                state.coseAlgorithm,
                state.derivationProfile,
            )
        } catch (_: IllegalArgumentException) {
            invalidPasskeyState()
        }
    }

    private fun PasskeyState.isInitialState(): Boolean =
        id.isEmpty() &&
            rpId.isEmpty() &&
            userId.isEmpty() &&
            userName.isEmpty() &&
            userDisplayName.isEmpty() &&
            credentialId.isEmpty() &&
            publicKey.isEmpty() &&
            aaguid.isEmpty() &&
            signCount == 0L &&
            !isDeleted &&
            sequenceNumber == 0L &&
            coseAlgorithm == 0 &&
            derivationProfile.isEmpty()

    /** Applies a command, persists its events, and returns caller-owned reconstructed state. */
    @Req("FR-003", "FR-013", "NFR-SEC-041", "TR-AUTH-031")
    override suspend fun execute(
        aggregateId: String,
        command: PasskeyCommand,
    ): Result<PasskeyState> =
        try {
            executeOwned(aggregateId, command)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: IllegalArgumentException) {
            // Preserve errors raised by repositories/snapshots; only validation failures before
            // append are translated to InvalidPasskeyCommandException below.
            Result.failure(failure)
        } finally {
            (command as? PasskeyCommand.Register)?.eraseSensitiveMetadata()
        }

    private suspend fun executeOwned(
        aggregateId: String,
        command: PasskeyCommand,
    ): Result<PasskeyState> {
        var lastResult: Result<Unit>? = null

        repeat(MAX_RETRIES) {
            val currentStateResult = getState(aggregateId)
            if (currentStateResult.isFailure) return currentStateResult

            val currentState = currentStateResult.getOrThrow()
            var events: List<PasskeyEvent> = emptyList()
            var preserveCurrentState = false
            var stateToReturn: PasskeyState? = null
            try {
                try {
                    validateCommand(aggregateId, currentState, command)
                    events = decider.decide(currentState, command)
                } catch (_: IllegalArgumentException) {
                    return Result.failure(InvalidPasskeyCommandException())
                }

                if (events.isEmpty()) {
                    preserveCurrentState = true
                    return Result.success(currentState)
                }

                // Validate the complete prospective state before committing authoritative events.
                // Keep event metadata owned until append has consumed it.
                val prospectiveState =
                    try {
                        foldOwnedEvents(currentState, events, aggregateId, eraseEvents = false)
                    } catch (_: IllegalArgumentException) {
                        return Result.failure(InvalidPasskeyCommandException())
                    }
                stateToReturn = prospectiveState
                val appendResult: Result<Unit> = eventStoreRepository.append(EventKind.PASSKEY, events)
                if (appendResult.isSuccess) {
                    val state = prospectiveState

                    // Check if we should take a snapshot (threshold: 20 events)
                    if (state.sequenceNumber % SNAPSHOT_THRESHOLD == 0L) {
                        val snapshotResult =
                            snapshotRepository.save(
                                EventKind.PASSKEY,
                                Snapshot(
                                    aggregateId = aggregateId,
                                    sequenceNumber = state.sequenceNumber,
                                    state = state,
                                    timestamp = Clock.System.now(),
                                ),
                            )
                        if (snapshotResult.isFailure) {
                            return Result.failure(requireNotNull(snapshotResult.exceptionOrNull()))
                        }
                    }

                    preserveCurrentState = true
                    return Result.success(state)
                }
                lastResult = appendResult
            } finally {
                if (!preserveCurrentState) {
                    currentState.eraseSensitiveMetadata()
                    stateToReturn?.eraseSensitiveMetadata()
                }
                events.filterIsInstance<PasskeyEvent.Registered>().forEach { it.eraseSensitiveMetadata() }
            }
        }

        val exception =
            lastResult?.exceptionOrNull() ?: RuntimeException(
                "Passkey aggregate command execution failed after retries",
            )
        val finalResult: Result<PasskeyState> = Result.failure(exception)
        return finalResult
    }

    private fun PasskeyState.copyWithOwnedMetadata(): PasskeyState =
        copy(
            rpId = rpId.copyOf(),
            userId = userId.copyOf(),
            userName = userName.copyOf(),
            userDisplayName = userDisplayName.copyOf(),
        )

    private fun validateCommand(
        aggregateId: String,
        state: PasskeyState,
        command: PasskeyCommand,
    ) {
        require(aggregateId.isNotBlank()) { INVALID_COMMAND_MESSAGE }
        when (command) {
            is PasskeyCommand.Register -> {
                require(command.id == aggregateId && state.isInitialState()) { INVALID_COMMAND_MESSAGE }
                require(command.publicKey.isNotBlank()) { INVALID_COMMAND_MESSAGE }
                CredentialKeyDescriptor(
                    CredentialId.fromByteArray(command.credentialId),
                    command.coseAlgorithm,
                    command.derivationProfile,
                )
            }
            is PasskeyCommand.Authenticate -> {
                require(command.id == aggregateId && !state.isInitialState() && !state.isDeleted) {
                    INVALID_COMMAND_MESSAGE
                }
            }
            is PasskeyCommand.Delete -> {
                require(command.id == aggregateId && !state.isInitialState() && !state.isDeleted) {
                    INVALID_COMMAND_MESSAGE
                }
            }
        }
        require(state.sequenceNumber < Long.MAX_VALUE) { INVALID_COMMAND_MESSAGE }
    }

    private class InvalidPasskeyStateException : IllegalArgumentException("Invalid passkey aggregate data")

    private fun invalidPasskeyState(): Nothing = throw InvalidPasskeyStateException()

    private class MalformedPasskeyAggregateException : IllegalStateException("Passkey aggregate data is invalid")

    private class InvalidPasskeyCommandException : IllegalArgumentException("Passkey command is invalid")
}
