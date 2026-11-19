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
                withOwnedPasskeyMetadata(
                    command.rpId,
                    command.userId,
                    command.userName,
                    command.userDisplayName,
                    command.credentialId,
                ) { rpId, userId, userName, userDisplayName, credentialId ->
                    listOf(
                        PasskeyEvent.Registered(
                            aggregateId = command.id,
                            sequenceNumber = nextSequenceNumber,
                            timestamp = timestamp,
                            rpId = rpId,
                            userId = userId,
                            userName = userName,
                            userDisplayName = userDisplayName,
                            credentialId = credentialId,
                            publicKey = command.publicKey,
                            aaguid = command.aaguid,
                            signCount = command.signCount,
                            coseAlgorithm = command.coseAlgorithm,
                            derivationProfile = command.derivationProfile,
                        ),
                    )
                }
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
                withOwnedPasskeyMetadata(
                    event.rpId,
                    event.userId,
                    event.userName,
                    event.userDisplayName,
                    event.credentialId,
                ) { rpId, userId, userName, userDisplayName, credentialId ->
                    state.copy(
                        id = event.aggregateId,
                        rpId = rpId,
                        userId = userId,
                        userName = userName,
                        userDisplayName = userDisplayName,
                        credentialId = credentialId,
                        publicKey = event.publicKey,
                        aaguid = event.aaguid,
                        signCount = event.signCount,
                        sequenceNumber = event.sequenceNumber,
                        coseAlgorithm = event.coseAlgorithm,
                        derivationProfile = event.derivationProfile,
                    )
                }
            }
            is PasskeyEvent.Authenticated ->
                withOwnedPasskeyMetadata(
                    state.rpId,
                    state.userId,
                    state.userName,
                    state.userDisplayName,
                    state.credentialId,
                ) { rpId, userId, userName, userDisplayName, credentialId ->
                    state.copy(
                        rpId = rpId,
                        userId = userId,
                        userName = userName,
                        userDisplayName = userDisplayName,
                        credentialId = credentialId,
                        signCount = event.newSignCount,
                        sequenceNumber = event.sequenceNumber,
                    )
                }
            is PasskeyEvent.Deleted ->
                withOwnedPasskeyMetadata(
                    state.rpId,
                    state.userId,
                    state.userName,
                    state.userDisplayName,
                    state.credentialId,
                ) { rpId, userId, userName, userDisplayName, credentialId ->
                    state.copy(
                        rpId = rpId,
                        userId = userId,
                        userName = userName,
                        userDisplayName = userDisplayName,
                        credentialId = credentialId,
                        isDeleted = true,
                        sequenceNumber = event.sequenceNumber,
                    )
                }
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

/** Stages passkey metadata copies until event or state construction transfers ownership. */
internal fun <T> withOwnedPasskeyMetadata(
    rpId: CharArray,
    userId: CharArray,
    userName: CharArray,
    userDisplayName: CharArray,
    credentialId: ByteArray,
    copyCharacters: (CharArray) -> CharArray = { it.copyOf() },
    copyBytes: (ByteArray) -> ByteArray = { it.copyOf() },
    build: (CharArray, CharArray, CharArray, CharArray, ByteArray) -> T,
): T {
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
        return build(
            requireNotNull(rpIdCopy),
            requireNotNull(userIdCopy),
            requireNotNull(userNameCopy),
            requireNotNull(userDisplayNameCopy),
            requireNotNull(credentialIdCopy),
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
