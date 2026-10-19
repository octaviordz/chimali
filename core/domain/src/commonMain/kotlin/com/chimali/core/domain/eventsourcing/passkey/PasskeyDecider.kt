package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.Decider
import com.chimali.core.domain.model.CredentialKeyDescriptor
import kotlinx.datetime.Clock

/**
 * Decider implementation for the PasskeyCredential aggregate.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyDecider : Decider<PasskeyCommand, PasskeyState, PasskeyEvent> {
    /** Empty initial aggregate state before a registration event is applied. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override val initialState: PasskeyState = PasskeyState()

    /** Validates a command against current state and emits its next event or rejects it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun decide(
        state: PasskeyState,
        command: PasskeyCommand,
    ): List<PasskeyEvent> {
        val nextSequenceNumber = state.sequenceNumber + 1
        val timestamp = Clock.System.now()

        return when (command) {
            is PasskeyCommand.Register -> {
                requireCurrentProfile(command.coseAlgorithm, command.derivationProfile)
                listOf(
                    PasskeyEvent.Registered(
                        aggregateId = command.id,
                        sequenceNumber = nextSequenceNumber,
                        timestamp = timestamp,
                        rpId = command.rpId.copyOf(),
                        userId = command.userId.copyOf(),
                        userName = command.userName.copyOf(),
                        userDisplayName = command.userDisplayName.copyOf(),
                        credentialId = command.credentialId,
                        publicKey = command.publicKey,
                        aaguid = command.aaguid,
                        signCount = command.signCount,
                        coseAlgorithm = command.coseAlgorithm,
                        derivationProfile = command.derivationProfile,
                    ),
                )
            }
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

    /** Applies a validated event to produce the next passkey state. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun evolve(
        state: PasskeyState,
        event: PasskeyEvent,
    ): PasskeyState =
        when (event) {
            is PasskeyEvent.Registered -> {
                requireCurrentProfile(event.coseAlgorithm, event.derivationProfile)
                state.copy(
                    id = event.aggregateId,
                    rpId = event.rpId.copyOf(),
                    userId = event.userId.copyOf(),
                    userName = event.userName.copyOf(),
                    userDisplayName = event.userDisplayName.copyOf(),
                    credentialId = event.credentialId,
                    publicKey = event.publicKey,
                    aaguid = event.aaguid,
                    signCount = event.signCount,
                    sequenceNumber = event.sequenceNumber,
                    coseAlgorithm = event.coseAlgorithm,
                    derivationProfile = event.derivationProfile,
                )
            }
            is PasskeyEvent.Authenticated ->
                state.copy(
                    rpId = state.rpId.copyOf(),
                    userId = state.userId.copyOf(),
                    userName = state.userName.copyOf(),
                    userDisplayName = state.userDisplayName.copyOf(),
                    signCount = event.newSignCount,
                    sequenceNumber = event.sequenceNumber,
                )
            is PasskeyEvent.Deleted ->
                state.copy(
                    rpId = state.rpId.copyOf(),
                    userId = state.userId.copyOf(),
                    userName = state.userName.copyOf(),
                    userDisplayName = state.userDisplayName.copyOf(),
                    isDeleted = true,
                    sequenceNumber = event.sequenceNumber,
                )
        }

    private fun requireCurrentProfile(
        coseAlgorithm: Int,
        derivationProfile: String,
    ) {
        require(CredentialKeyDescriptor.profileFor(coseAlgorithm) == derivationProfile) {
            "Unsupported credential algorithm/profile pair"
        }
    }
}
