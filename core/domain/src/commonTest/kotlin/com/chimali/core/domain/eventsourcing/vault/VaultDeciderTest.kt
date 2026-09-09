package com.chimali.core.domain.eventsourcing.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * Unit tests for the VaultDecider pure logic.
 */
class VaultDeciderTest {
    private val decider = VaultDecider()

    @Test
    fun `Create command emits Created event`() {
        val command =
            VaultCommand.Create(
                id = "test-id",
                type = "PASSWORD",
                title = "Test Entry",
                payload = byteArrayOf(1, 2, 3),
                identityId = "identity-id",
            )
        val events = decider.decide(decider.initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as VaultEvent.Created
        assertEquals("test-id", event.aggregateId)
        assertEquals(1L, event.sequenceNumber)
        assertEquals("PASSWORD", event.type)
        assertTrue(event.title.contentEquals("Test Entry".toCharArray()))
        assertTrue(event.payload.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `Created event evolves initial state`() {
        val event =
            VaultEvent.Created(
                aggregateId = "test-id",
                sequenceNumber = 1L,
                timestamp = Clock.System.now(),
                type = "PASSWORD",
                title = "Test Entry",
                payload = byteArrayOf(1, 2, 3),
                identityId = "identity-id",
            )
        val state = decider.evolve(decider.initialState, event)

        assertEquals("test-id", state.id)
        assertEquals("PASSWORD", state.type)
        assertTrue(state.title.contentEquals("Test Entry".toCharArray()))
        assertTrue(state.payload.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals("identity-id", state.identityId)
        assertEquals(1L, state.sequenceNumber)
    }

    @Test
    fun `Update command emits Updated event`() {
        val initialState =
            VaultState(
                id = "test-id",
                type = "PASSWORD",
                title = "Old Title",
                payload = byteArrayOf(1),
                sequenceNumber = 1L,
            )
        val command =
            VaultCommand.Update(
                id = "test-id",
                title = "New Title",
                payload = byteArrayOf(2),
            )
        val events = decider.decide(initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as VaultEvent.Updated
        assertEquals("test-id", event.aggregateId)
        assertEquals(2L, event.sequenceNumber)
        assertTrue(event.title.contentEquals("New Title".toCharArray()))
        assertTrue(event.payload!!.contentEquals(byteArrayOf(2)))
    }

    @Test
    fun `Updated event evolves state`() {
        val initialState =
            VaultState(
                id = "test-id",
                type = "PASSWORD",
                title = "Old Title",
                payload = byteArrayOf(1),
                sequenceNumber = 1L,
            )
        val event =
            VaultEvent.Updated(
                aggregateId = "test-id",
                sequenceNumber = 2L,
                timestamp = Clock.System.now(),
                title = "New Title",
                payload = byteArrayOf(2),
            )
        val state = decider.evolve(initialState, event)

        assertTrue(state.title.contentEquals("New Title".toCharArray()))
        assertTrue(state.payload.contentEquals(byteArrayOf(2)))
        assertEquals(2L, state.sequenceNumber)
    }

    @Test
    fun `Delete command emits Deleted event`() {
        val initialState =
            VaultState(
                id = "test-id",
                sequenceNumber = 1L,
            )
        val command = VaultCommand.Delete(id = "test-id")
        val events = decider.decide(initialState, command)

        assertEquals(1, events.size)
        val event = events[0] as VaultEvent.Deleted
        assertEquals("test-id", event.aggregateId)
        assertEquals(2L, event.sequenceNumber)
    }

    @Test
    fun `Deleted event evolves state`() {
        val initialState =
            VaultState(
                id = "test-id",
                sequenceNumber = 1L,
            )
        val event =
            VaultEvent.Deleted(
                aggregateId = "test-id",
                sequenceNumber = 2L,
                timestamp = Clock.System.now(),
            )
        val state = decider.evolve(initialState, event)

        assertTrue(state.isDeleted)
        assertEquals(2L, state.sequenceNumber)
    }
}
