package com.chimali.core.domain.eventsourcing.vault

import com.chimali.core.domain.eventsourcing.Decider
import kotlinx.datetime.Clock

/**
 * Decider implementation for the VaultEntry aggregate.
 */
class VaultDecider : Decider<VaultCommand, VaultState, VaultEvent> {
    override val initialState: VaultState = VaultState()

    override fun decide(
        state: VaultState,
        command: VaultCommand,
    ): List<VaultEvent> {
        val nextSequenceNumber = state.sequenceNumber + 1
        val timestamp = Clock.System.now()

        return when (command) {
            is VaultCommand.Create ->
                listOf(
                    VaultEvent.Created(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                        type = command.type,
                        title = command.title,
                        payload = command.payload,
                        identityId = command.identityId,
                    ),
                )
            is VaultCommand.Update ->
                listOf(
                    VaultEvent.Updated(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                        title = command.title,
                        payload = command.payload,
                    ),
                )
            is VaultCommand.Delete ->
                listOf(
                    VaultEvent.Deleted(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                    ),
                )
        }
    }

    override fun evolve(
        state: VaultState,
        event: VaultEvent,
    ): VaultState =
        when (event) {
            is VaultEvent.Created ->
                state.copy(
                    id = event.aggregateId,
                    type = event.type,
                    title = event.title,
                    payload = event.payload,
                    identityId = event.identityId,
                    sequenceNumber = event.sequenceNumber,
                )
            is VaultEvent.Updated ->
                state.copy(
                    title = event.title ?: state.title,
                    payload = event.payload ?: state.payload,
                    sequenceNumber = event.sequenceNumber,
                )
            is VaultEvent.Deleted ->
                state.copy(
                    isDeleted = true,
                    sequenceNumber = event.sequenceNumber,
                )
        }
}
