package com.chimali.core.domain.eventsourcing.passkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * Unit tests for the PasskeyDecider pure logic.
 */
class PasskeyDeciderTest {
    private val decider = PasskeyDecider()

    @Test
    fun `Register command emits Registered event`() {
        val command =
            PasskeyCommand.Register(
                id = "test-id",
                rpId = "example.com",
                userId = "user-123",
                userName = "user@example.com",
                userDisplayName = "User Name",
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
            )
        val events = decider.decide(decider.initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as PasskeyEvent.Registered
        assertEquals("test-id", event.aggregateId)
        assertEquals(1L, event.sequenceNumber)
        assertEquals("example.com", event.rpId)
        assertTrue(event.credentialId.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
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
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = "public-key",
                aaguid = "aaguid",
                signCount = 0L,
            )
        val state = decider.evolve(decider.initialState, event)

        assertEquals("test-id", state.id)
        assertEquals("example.com", state.rpId)
        assertEquals("user-123", state.userId)
        assertTrue(state.credentialId.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(1L, state.sequenceNumber)
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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
}
