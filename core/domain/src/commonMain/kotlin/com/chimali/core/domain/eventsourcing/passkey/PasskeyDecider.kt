package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.domain.eventsourcing.Decider
import kotlinx.datetime.Clock

/**
 * Decider implementation for the PasskeyCredential aggregate.
 */
class PasskeyDecider : Decider<PasskeyCommand, PasskeyState, PasskeyEvent> {
    override val initialState: PasskeyState = PasskeyState()

    override fun decide(
        state: PasskeyState,
        command: PasskeyCommand,
    ): List<PasskeyEvent> {
        val nextSequenceNumber = state.sequenceNumber + 1
        val timestamp = Clock.System.now()

        return when (command) {
            is PasskeyCommand.Register ->
                listOf(
                    PasskeyEvent.Registered(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                        rpId = command.rpId,
                        userId = command.userId,
                        userName = command.userName,
                        userDisplayName = command.userDisplayName,
                        credentialId = command.credentialId,
                        publicKey = command.publicKey,
                        aaguid = command.aaguid,
                        signCount = command.signCount,
                    ),
                )
            is PasskeyCommand.Authenticate ->
                listOf(
                    PasskeyEvent.Authenticated(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                        newSignCount = command.newSignCount,
                    ),
                )
            is PasskeyCommand.Delete ->
                listOf(
                    PasskeyEvent.Deleted(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                    ),
                )
        }
    }

    override fun evolve(
        state: PasskeyState,
        event: PasskeyEvent,
    ): PasskeyState =
        when (event) {
            is PasskeyEvent.Registered ->
                state.copy(
                    id = event.aggregateId,
                    rpId = event.rpId,
                    userId = event.userId,
                    userName = event.userName,
                    userDisplayName = event.userDisplayName,
                    credentialId = event.credentialId,
                    publicKey = event.publicKey,
                    aaguid = event.aaguid,
                    signCount = event.signCount,
                    sequenceNumber = event.sequenceNumber,
                )
            is PasskeyEvent.Authenticated ->
                state.copy(
                    signCount = event.newSignCount,
                    sequenceNumber = event.sequenceNumber,
                )
            is PasskeyEvent.Deleted ->
                state.copy(
                    isDeleted = true,
                    sequenceNumber = event.sequenceNumber,
                )
        }
}
