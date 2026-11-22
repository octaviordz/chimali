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
        } catch (_: Exception) {
            Result.failure(PasskeyAggregateRetrievalException())
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
            snapshotResult.exceptionOrNull().throwIfCancellation()
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
                            onFailure = { failure ->
                                failure.throwIfCancellation()
                                Result.failure(PasskeyAggregateRetrievalException())
                            },
                        )
                    } finally {
                        if (!stateTransferred) initialState.eraseSensitiveMetadata()
                    }
                }
            }
        }

        return eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, asOf).fold(
            onSuccess = { events -> Result.success(foldOwnedEvents(PasskeyState(), events, aggregateId)) },
            onFailure = { failure ->
                failure.throwIfCancellation()
                Result.failure(PasskeyAggregateRetrievalException())
            },
        )
    }

    private fun Throwable?.throwIfCancellation() {
        if (this is CancellationException) throw this
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
            validateCredentialKeyDescriptor(state.credentialId, state.coseAlgorithm, state.derivationProfile)
        } catch (_: IllegalArgumentException) {
            invalidPasskeyState()
        }
    }

    private fun validateCredentialKeyDescriptor(
        credentialId: ByteArray,
        coseAlgorithm: Int,
        derivationProfile: String,
    ) {
        require(credentialId.size in CredentialId.MIN_CREDENTIAL_ID_BYTES..CredentialId.MAX_CREDENTIAL_ID_BYTES) {
            "Invalid credential identifier"
        }
        require(CredentialKeyDescriptor.profileFor(coseAlgorithm) == derivationProfile) {
            "Unsupported credential algorithm/profile pair"
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
        } catch (_: Exception) {
            Result.failure(PasskeyAggregateCommandExecutionException())
        } finally {
            (command as? PasskeyCommand.Register)?.eraseSensitiveMetadata()
        }

    private suspend fun executeOwned(
        aggregateId: String,
        command: PasskeyCommand,
    ): Result<PasskeyState> {
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
                            snapshotResult.exceptionOrNull()?.let { failure ->
                                if (failure is CancellationException) throw failure
                            }
                            return Result.failure(PasskeyAggregateSnapshotException())
                        }
                    }

                    preserveCurrentState = true
                    return Result.success(state)
                }
                appendResult.exceptionOrNull()?.let { failure ->
                    if (failure is CancellationException) throw failure
                }
            } finally {
                if (!preserveCurrentState) {
                    currentState.eraseSensitiveMetadata()
                    stateToReturn?.eraseSensitiveMetadata()
                }
                events.filterIsInstance<PasskeyEvent.Registered>().forEach { it.eraseSensitiveMetadata() }
            }
        }

        val finalResult: Result<PasskeyState> = Result.failure(PasskeyAggregateRetryException())
        return finalResult
    }

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
                validateCredentialKeyDescriptor(command.credentialId, command.coseAlgorithm, command.derivationProfile)
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

    private class PasskeyAggregateRetrievalException :
        IllegalStateException("Passkey aggregate retrieval failed")

    private class PasskeyAggregateSnapshotException :
        IllegalStateException("Passkey aggregate snapshot save failed")

    private class PasskeyAggregateRetryException :
        IllegalStateException("Passkey aggregate command execution failed after retries")

    private class PasskeyAggregateCommandExecutionException :
        IllegalStateException("Passkey aggregate command execution failed")
}

/** Copies snapshot-owned mutable metadata and erases every staged copy if construction fails. */
internal fun PasskeyState.copyWithOwnedMetadata(
    copyCharacters: (CharArray) -> CharArray = { it.copyOf() },
    copyBytes: (ByteArray) -> ByteArray = { it.copyOf() },
): PasskeyState {
    var rpIdCopy: CharArray? = null
    var userIdCopy: CharArray? = null
    var userNameCopy: CharArray? = null
    var userDisplayNameCopy: CharArray? = null
    var credentialIdCopy: ByteArray? = null
    var transferred = false
    try {
        rpIdCopy = copyCharacters(rpId)
        userIdCopy = copyCharacters(userId)
        userNameCopy = copyCharacters(userName)
        userDisplayNameCopy = copyCharacters(userDisplayName)
        credentialIdCopy = copyBytes(credentialId)
        return copy(
            rpId = requireNotNull(rpIdCopy),
            userId = requireNotNull(userIdCopy),
            userName = requireNotNull(userNameCopy),
            userDisplayName = requireNotNull(userDisplayNameCopy),
            credentialId = requireNotNull(credentialIdCopy),
        ).also { transferred = true }
    } finally {
        if (!transferred) {
            rpIdCopy?.fill('\u0000')
            userIdCopy?.fill('\u0000')
            userNameCopy?.fill('\u0000')
            userDisplayNameCopy?.fill('\u0000')
            credentialIdCopy?.fill(0)
        }
    }
}
