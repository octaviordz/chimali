package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultDecider
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for VaultAggregateServiceImpl.
 *
 * Traceability:
 * - NFR-ARCH-050: Core aggregates (VaultEntry) utilize Event Sourcing, Decider fold reconstruction,
 *   temporal queries (asOf), snapshot hydration, and optimistic concurrency retry.
 * - NFR-SEC-041: Sensitive memory zeroization on state transitions.
 */
class VaultAggregateServiceImplTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository =
        mockk<SnapshotRepository> {
            coEvery { getLatest<com.chimali.core.domain.eventsourcing.vault.VaultState>(any(), any()) } returns
                Result.success(null)
        }
    private val service = VaultAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

    // Requirement: NFR-ARCH-050 (State reconstruction from event stream)
    @Test
    fun `getState should hydrate state from events`() =
        runTest {
            val events =
                listOf(
                    VaultEvent.Created(
                        aggregateId,
                        1,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "type",
                        "title",
                        byteArrayOf(1),
                        "identity",
                    ),
                    VaultEvent.Updated(
                        aggregateId,
                        2,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "title updated",
                        null,
                    ),
                )
            coEvery { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any()) } returns
                Result.success(events)

            val state = service.getState(aggregateId).getOrThrow()

            assertTrue(state.title.contentEquals("title updated".toCharArray()))
            assertEquals(2L, state.sequenceNumber)
        }

    // Requirement: NFR-ARCH-050 (Temporal query / historical state reconstruction)
    @Test
    fun `getState with asOf should reconstruct state at specific point in time`() =
        runTest {
            val t1 = kotlinx.datetime.Instant.parse("2026-05-13T00:00:00Z")
            val t2 = kotlinx.datetime.Instant.parse("2026-05-13T01:00:00Z")
            val events =
                listOf(
                    VaultEvent.Created(aggregateId, 1, t1, "type", "title 1", byteArrayOf(1), "identity"),
                    VaultEvent.Updated(aggregateId, 2, t2, "title 2", null),
                )

            // Mocking getEvents to filter by timestamp
            coEvery { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, t1) } returns
                Result.success(listOf(events[0]))
            coEvery { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, t2) } returns
                Result.success(events)

            val stateAtT1 = service.getState(aggregateId, t1).getOrThrow()
            val stateAtT2 = service.getState(aggregateId, t2).getOrThrow()

            assertTrue(stateAtT1.title.contentEquals("title 1".toCharArray()))
            assertEquals(1L, stateAtT1.sequenceNumber)

            assertTrue(stateAtT2.title.contentEquals("title 2".toCharArray()))
            assertEquals(2L, stateAtT2.sequenceNumber)
        }

    // Requirement: NFR-ARCH-050 (Optimistic concurrency retry on conflict)
    @Test
    fun `execute should retry on optimistic concurrency failure`() =
        runTest {
            val commandPayload = byteArrayOf(0x41, 0x42)
            val command = VaultCommand.Create(aggregateId, "type", "title", commandPayload, "identity")
            val appendedEvents = mutableListOf<VaultEvent.Created>()
            val payloadsSeenAtAppend = mutableListOf<List<Byte>>()

            coEvery {
                eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any())
            } returns Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) } coAnswers {
                val event = secondArg<List<VaultEvent>>().single() as VaultEvent.Created
                appendedEvents += event
                payloadsSeenAtAppend += event.payload.toList()
                if (appendedEvents.size == 1) {
                    Result.failure(RuntimeException("Concurrency error"))
                } else {
                    Result.success(Unit)
                }
            }

            val state = service.execute(aggregateId, command).getOrThrow()

            assertTrue(state.title.contentEquals("title".toCharArray()))
            assertEquals(1L, state.sequenceNumber)
            assertTrue(state.payload.contentEquals(byteArrayOf(0x41, 0x42)))
            assertEquals(List(2) { listOf<Byte>(0x41, 0x42) }, payloadsSeenAtAppend)
            assertTrue(command.title.all { it == '\u0000' })
            assertTrue(commandPayload.all { it == 0.toByte() })
            assertTrue(appendedEvents.all { event -> event.title.all { it == '\u0000' } })
            assertTrue(appendedEvents.all { event -> event.payload.all { it == 0.toByte() } })
        }

    // Requirement: NFR-ARCH-050 (Snapshot acceleration and differential hydration)
    @Test
    fun `getState should hydrate from snapshot when available`() =
        runTest {
            val snapshotState =
                VaultState(
                    id = aggregateId,
                    type = "login",
                    title = "snapshot title".toCharArray(),
                    payload = byteArrayOf(1, 2),
                    identityId = "user1",
                    isDeleted = false,
                    sequenceNumber = 2L,
                )
            val snapshot =
                Snapshot<VaultState>(
                    aggregateId = aggregateId,
                    sequenceNumber = 2L,
                    state = snapshotState,
                    timestamp =
                        kotlinx.datetime.Clock.System
                            .now(),
                )
            val subsequentEvents =
                listOf(
                    VaultEvent.Updated(
                        aggregateId,
                        3L,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "title from event",
                        null,
                    ),
                )

            coEvery {
                snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId)
            } returns Result.success(snapshot)
            coEvery {
                eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L)
            } returns Result.success(subsequentEvents)

            val state = service.getState(aggregateId).getOrThrow()

            assertTrue(state.title.contentEquals("title from event".toCharArray()))
            assertEquals(3L, state.sequenceNumber)
        }

    // Requirement: NFR-ARCH-050 (Bounded retry exhaustion)
    @Test
    fun `execute returns failure when all retries are exhausted`() =
        runTest {
            val commandPayload = byteArrayOf(0x31, 0x32)
            val command = VaultCommand.Create(aggregateId, "type", "title", commandPayload, "identity")
            val appendedEvents = mutableListOf<VaultEvent.Created>()
            val payloadsSeenAtAppend = mutableListOf<List<Byte>>()

            coEvery {
                eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any())
            } returns Result.success(emptyList())
            coEvery {
                eventStoreRepository.append(EventKind.VAULT_ENTRY, any())
            } coAnswers {
                val event = secondArg<List<VaultEvent>>().single() as VaultEvent.Created
                appendedEvents += event
                payloadsSeenAtAppend += event.payload.toList()
                Result.failure(RuntimeException("Database lock"))
            }

            val result = service.execute(aggregateId, command)

            assertTrue(result.isFailure)
            assertEquals("Database lock", result.exceptionOrNull()?.message)
            coVerify(exactly = 3) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            assertEquals(List(3) { listOf<Byte>(0x31, 0x32) }, payloadsSeenAtAppend)
            assertTrue(command.title.all { it == '\u0000' })
            assertTrue(commandPayload.all { it == 0.toByte() })
            assertTrue(appendedEvents.all { event -> event.title.all { it == '\u0000' } })
            assertTrue(appendedEvents.all { event -> event.payload.all { it == 0.toByte() } })
        }

    // Requirement: NFR-SEC-041 (Sensitive memory cleanup when execution is cancelled)
    @Test
    fun `execute clears command and event payloads when append is cancelled`() =
        runTest {
            val commandPayload = byteArrayOf(0x51, 0x52)
            val command = VaultCommand.Create(aggregateId, "type", "title", commandPayload, "identity")
            lateinit var appendedEvent: VaultEvent.Created
            coEvery {
                eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any())
            } returns Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) } coAnswers {
                appendedEvent = secondArg<List<VaultEvent>>().single() as VaultEvent.Created
                throw kotlinx.coroutines.CancellationException("synthetic cancellation")
            }

            var cancellationPropagated = false
            try {
                service.execute(aggregateId, command)
            } catch (_: kotlinx.coroutines.CancellationException) {
                cancellationPropagated = true
            }

            assertTrue(cancellationPropagated)
            assertTrue(command.title.all { it == '\u0000' })
            assertTrue(commandPayload.all { it == 0.toByte() })
            assertTrue(appendedEvent.title.all { it == '\u0000' })
            assertTrue(appendedEvent.payload.all { it == 0.toByte() })
        }

    // Requirement: NFR-SEC-041 (Owned vault state cleanup)
    @Test
    fun `clearing vault state erases title and payload buffers`() {
        val state =
            VaultState(
                id = aggregateId,
                type = "type",
                title = "sensitive title",
                payload = byteArrayOf(0x61, 0x62),
                identityId = "identity",
            )

        state.clearSensitiveMemory()

        assertTrue(state.title.all { it == '\u0000' })
        assertTrue(state.payload.all { it == 0.toByte() })
    }

    // Requirement: NFR-ARCH-050 (State read failure propagation)
    @Test
    fun `execute returns early failure when getState fails`() =
        runTest {
            val command = VaultCommand.Create(aggregateId, "type", "title", byteArrayOf(1), "identity")

            coEvery {
                eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any())
            } returns Result.failure(RuntimeException("Read failure"))

            val result = service.execute(aggregateId, command)

            assertTrue(result.isFailure)
            assertEquals("Read failure", result.exceptionOrNull()?.message)
        }

    // Requirement: NFR-ARCH-050 (Snapshot generation at interval threshold)
    @Test
    fun `execute saves snapshot when sequence number reaches snapshot threshold`() =
        runTest {
            val events =
                listOf(
                    VaultEvent.Created(
                        aggregateId,
                        19L,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "login",
                        "title",
                        byteArrayOf(1),
                        "id1",
                    ),
                )
            coEvery { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any()) } returns
                Result.success(events)
            coEvery { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) } returns Result.success(Unit)
            coEvery { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) } returns
                Result.success(Unit)

            val command = VaultCommand.Update(aggregateId, "new title", null)
            val result = service.execute(aggregateId, command)

            assertTrue(result.isSuccess)
            assertEquals(20L, result.getOrThrow().sequenceNumber)
            coVerify(exactly = 1) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    // Requirement: NFR-ARCH-050 (No-op command execution bypasses append)
    @Test
    fun `execute returns current state without append when decider yields no events`() =
        runTest {
            val events =
                listOf(
                    VaultEvent.Created(
                        aggregateId,
                        1L,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "login",
                        "title",
                        byteArrayOf(1),
                        "id1",
                    ),
                )
            coEvery { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any()) } returns
                Result.success(events)

            val mockDecider = mockk<VaultDecider>()
            every { mockDecider.initialState } returns VaultState()
            every { mockDecider.evolve(any<VaultState>(), any<VaultEvent>()) } answers { firstArg<VaultState>() }
            every { mockDecider.decide(any<VaultState>(), any<VaultCommand>()) } returns emptyList<VaultEvent>()

            val customService = VaultAggregateServiceImpl(eventStoreRepository, snapshotRepository, mockDecider)
            val command = VaultCommand.Update(aggregateId, "same title", null)
            val result = customService.execute(aggregateId, command)

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) {
                eventStoreRepository.append(
                    any<EventKind>(),
                    any<List<com.chimali.core.domain.eventsourcing.DomainEvent>>(),
                )
            }
        }
}
