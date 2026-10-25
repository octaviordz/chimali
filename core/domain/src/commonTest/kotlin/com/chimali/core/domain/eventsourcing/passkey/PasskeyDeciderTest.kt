package com.chimali.core.domain.eventsourcing.passkey

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Unit tests for the PasskeyDecider pure logic.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyDeciderTest {
    private val decider = PasskeyDecider()
    private val credentialIdBytes = ByteArray(32) { it.toByte() }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `passkey state diagnostics redact account and key metadata`() {
        val state =
            PasskeyState(
                id = "synthetic-credential-id",
                rpId = "synthetic-rp.example".toCharArray(),
                userId = "synthetic-user-id".toCharArray(),
                userName = "synthetic-account-name".toCharArray(),
                userDisplayName = "Synthetic Account Display".toCharArray(),
                credentialId = "synthetic-credential-bytes".encodeToByteArray(),
                publicKey = "synthetic-public-key",
                aaguid = "synthetic-aaguid",
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        try {
            val diagnostic = state.toString()
            listOf(
                "synthetic-rp.example",
                "synthetic-user-id",
                "synthetic-account-name",
                "Synthetic Account Display",
                "synthetic-credential-bytes",
                "synthetic-public-key",
                "synthetic-aaguid",
            ).forEach { marker -> assertFalse(diagnostic.contains(marker)) }
        } finally {
            state.eraseSensitiveMetadata()
        }
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Register command emits Registered event`() {
        val command =
            PasskeyCommand.Register(
                id = "test-id",
                rpId = "example.com",
                userId = "user-123",
                userName = "user@example.com",
                userDisplayName = "User Name",
                credentialId = credentialIdBytes,
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )
        val events = decider.decide(decider.initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as PasskeyEvent.Registered
        assertEquals("test-id", event.aggregateId)
        assertEquals(1L, event.sequenceNumber)
        assertEquals("example.com", event.rpId.concatToString())
        assertTrue(event.credentialId.contentEquals(credentialIdBytes))
        assertEquals(CredentialKeyDescriptor.COSE_ES256, event.coseAlgorithm)
        assertEquals(CredentialKeyDescriptor.P256_PROFILE, event.derivationProfile)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Registered event evolves initial state`() {
        val event =
            PasskeyEvent.Registered(
                aggregateId = "test-id",
                sequenceNumber = 1L,
                timestamp = Clock.System.now(),
                rpId = "example.com",
                userId = "user-123",
                userName = "user@example.com",
                userDisplayName = "User Name",
                credentialId = credentialIdBytes,
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_EDDSA,
                derivationProfile = CredentialKeyDescriptor.ED25519_PROFILE,
            )
        val state = decider.evolve(decider.initialState, event)

        assertEquals("test-id", state.id)
        assertEquals("example.com", state.rpId.concatToString())
        assertEquals("user-123", state.userId.concatToString())
        assertTrue(state.credentialId.contentEquals(credentialIdBytes))
        assertEquals(CredentialKeyDescriptor.COSE_EDDSA, state.coseAlgorithm)
        assertEquals(CredentialKeyDescriptor.ED25519_PROFILE, state.derivationProfile)
        assertEquals(1L, state.sequenceNumber)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `registration rejects invalid algorithm sentinel`() {
        val command = registrationCommand(coseAlgorithm = 0, derivationProfile = "")

        assertFailsWith<IllegalArgumentException> { decider.decide(decider.initialState, command) }
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `registration rejects mismatched algorithm and profile`() {
        val command =
            registrationCommand(
                coseAlgorithm = CredentialKeyDescriptor.COSE_EDDSA,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        assertFailsWith<IllegalArgumentException> { decider.decide(decider.initialState, command) }
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `registered event rejects invalid serialized sentinel pair`() {
        val invalidEvent =
            PasskeyEvent.Registered(
                aggregateId = "test-id",
                sequenceNumber = 1L,
                timestamp = Clock.System.now(),
                rpId = "example.com",
                userId = "user-123",
                userName = "user@example.com",
                userDisplayName = "User Name",
                credentialId = credentialIdBytes,
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
                coseAlgorithm = 0,
                derivationProfile = "",
            )

        assertFailsWith<IllegalArgumentException> { decider.evolve(decider.initialState, invalidEvent) }
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `registered event serializes algorithm and profile and round trips`() {
        val event: PasskeyEvent =
            PasskeyEvent.Registered(
                aggregateId = "test-id",
                sequenceNumber = 1L,
                timestamp = Clock.System.now(),
                rpId = "example.com",
                userId = "user-123",
                userName = "user@example.com",
                userDisplayName = "User Name",
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ML_DSA_65,
                derivationProfile = CredentialKeyDescriptor.MLDSA65_PROFILE,
            )
        val json = Json.encodeToString(PasskeyEvent.serializer(), event)
        val decoded = Json.decodeFromString(PasskeyEvent.serializer(), json)

        assertTrue(json.contains("\"coseAlgorithm\":-49"))
        assertTrue(json.contains("\"derivationProfile\":\"${CredentialKeyDescriptor.MLDSA65_PROFILE}\""))
        assertEquals(event, decoded)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Authenticate command emits Authenticated event`() {
        val initialState =
            PasskeyState(
                id = "test-id",
                signCount = 10L,
                sequenceNumber = 1L,
            )
        val command =
            PasskeyCommand.Authenticate(
                id = "test-id",
                newSignCount = 11L,
            )
        val events = decider.decide(initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as PasskeyEvent.Authenticated
        assertEquals("test-id", event.aggregateId)
        assertEquals(2L, event.sequenceNumber)
        assertEquals(11L, event.newSignCount)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Authenticated event evolves state`() {
        val initialState =
            PasskeyState(
                id = "test-id",
                signCount = 10L,
                sequenceNumber = 1L,
            )
        val event =
            PasskeyEvent.Authenticated(
                aggregateId = "test-id",
                sequenceNumber = 2L,
                timestamp = Clock.System.now(),
                newSignCount = 11L,
            )
        val state = decider.evolve(initialState, event)

        assertEquals(11L, state.signCount)
        assertEquals(2L, state.sequenceNumber)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Delete command emits Deleted event`() {
        val initialState =
            PasskeyState(
                id = "test-id",
                sequenceNumber = 1L,
            )
        val command = PasskeyCommand.Delete(id = "test-id")
        val events = decider.decide(initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as PasskeyEvent.Deleted
        assertEquals("test-id", event.aggregateId)
        assertEquals(2L, event.sequenceNumber)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `Deleted event evolves state`() {
        val initialState =
            PasskeyState(
                id = "test-id",
                sequenceNumber = 1L,
            )
        val event =
            PasskeyEvent.Deleted(
                aggregateId = "test-id",
                sequenceNumber = 2L,
                timestamp = Clock.System.now(),
            )
        val state = decider.evolve(initialState, event)

        assertTrue(state.isDeleted)
        assertEquals(2L, state.sequenceNumber)
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `PasskeyState equals and hashCode cover all properties and edge cases`() {
        val baseState =
            PasskeyState(
                id = "passkey-1",
                rpId = "example.com".toCharArray(),
                userId = "user-1".toCharArray(),
                userName = "alice".toCharArray(),
                userDisplayName = "Alice A".toCharArray(),
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "pub-key",
                aaguid = "aaguid-1",
                signCount = 10L,
                isDeleted = false,
                sequenceNumber = 1L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        val sameState =
            PasskeyState(
                id = "passkey-1",
                rpId = "example.com".toCharArray(),
                userId = "user-1".toCharArray(),
                userName = "alice".toCharArray(),
                userDisplayName = "Alice A".toCharArray(),
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "pub-key",
                aaguid = "aaguid-1",
                signCount = 10L,
                isDeleted = false,
                sequenceNumber = 1L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        assertEquals(baseState, baseState)
        assertEquals(baseState, sameState)
        assertEquals(baseState.hashCode(), sameState.hashCode())
        val nullState: Any? = null
        assertFalse(baseState == nullState)
        assertFalse(baseState.equals("not a PasskeyState"))

        assertFalse(baseState == baseState.copy(id = "other-id"))
        assertFalse(baseState == baseState.copy(rpId = "other.com".toCharArray()))
        assertFalse(baseState == baseState.copy(userId = "user-2".toCharArray()))
        assertFalse(baseState == baseState.copy(userName = "bob".toCharArray()))
        assertFalse(baseState == baseState.copy(userDisplayName = "Bob B".toCharArray()))
        assertFalse(baseState == baseState.copy(credentialId = byteArrayOf(1, 2, 9)))
        assertFalse(baseState == baseState.copy(publicKey = "other-pub"))
        assertFalse(baseState == baseState.copy(aaguid = "other-aaguid"))
        assertFalse(baseState == baseState.copy(signCount = 99L))
        assertFalse(baseState == baseState.copy(isDeleted = true))
        assertFalse(baseState == baseState.copy(sequenceNumber = 2L))
        assertFalse(baseState == baseState.copy(coseAlgorithm = CredentialKeyDescriptor.COSE_ML_DSA_65))
        assertFalse(baseState == baseState.copy(derivationProfile = CredentialKeyDescriptor.MLDSA65_PROFILE))
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `PasskeyCommand Register equals and hashCode cover all properties and edge cases`() {
        val baseCmd =
            PasskeyCommand.Register(
                id = "passkey-1",
                rpId = "example.com".toCharArray(),
                userId = "user-1".toCharArray(),
                userName = "alice".toCharArray(),
                userDisplayName = "Alice A".toCharArray(),
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "pub-key",
                aaguid = "aaguid-1",
                signCount = 10L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        val sameCmd =
            PasskeyCommand.Register(
                id = "passkey-1",
                rpId = "example.com".toCharArray(),
                userId = "user-1".toCharArray(),
                userName = "alice".toCharArray(),
                userDisplayName = "Alice A".toCharArray(),
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "pub-key",
                aaguid = "aaguid-1",
                signCount = 10L,
                coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        assertEquals(baseCmd, baseCmd)
        assertEquals(baseCmd, sameCmd)
        assertEquals(baseCmd.hashCode(), sameCmd.hashCode())
        val nullCmd: Any? = null
        assertFalse(baseCmd == nullCmd)
        assertFalse(baseCmd.equals("not a Register command"))

        assertFalse(baseCmd == baseCmd.copy(id = "other-id"))
        assertFalse(baseCmd == baseCmd.copy(rpId = "other.com".toCharArray()))
        assertFalse(baseCmd == baseCmd.copy(userId = "user-2".toCharArray()))
        assertFalse(baseCmd == baseCmd.copy(userName = "bob".toCharArray()))
        assertFalse(baseCmd == baseCmd.copy(userDisplayName = "Bob B".toCharArray()))
        assertFalse(baseCmd == baseCmd.copy(credentialId = byteArrayOf(1, 2, 9)))
        assertFalse(baseCmd == baseCmd.copy(publicKey = "other-pub"))
        assertFalse(baseCmd == baseCmd.copy(aaguid = "other-aaguid"))
        assertFalse(baseCmd == baseCmd.copy(signCount = 99L))
        assertFalse(baseCmd == baseCmd.copy(coseAlgorithm = CredentialKeyDescriptor.COSE_ML_DSA_65))
        assertFalse(baseCmd == baseCmd.copy(derivationProfile = CredentialKeyDescriptor.MLDSA65_PROFILE))
    }

    private fun registrationCommand(
        coseAlgorithm: Int,
        derivationProfile: String,
    ) = PasskeyCommand.Register(
        id = "test-id",
        rpId = "example.com",
        userId = "user-123",
        userName = "user@example.com",
        userDisplayName = "User Name",
        credentialId = credentialIdBytes,
        publicKey = "public-key",
        aaguid = "aaguid",
        signCount = 0L,
        coseAlgorithm = coseAlgorithm,
        derivationProfile = derivationProfile,
    )
}
