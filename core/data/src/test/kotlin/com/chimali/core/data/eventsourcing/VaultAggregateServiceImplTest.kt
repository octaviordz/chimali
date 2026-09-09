package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for VaultAggregateServiceImpl.
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

    @Test
    fun `execute should retry on optimistic concurrency failure`() =
        runTest {
            val command = VaultCommand.Create(aggregateId, "type", "title", byteArrayOf(1), "identity")

            coEvery {
                eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any())
            } returns Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("Concurrency error")),
                    Result.success(Unit),
                )

            val state = service.execute(aggregateId, command).getOrThrow()

            assertTrue(state.title.contentEquals("title".toCharArray()))
            assertEquals(1L, state.sequenceNumber)
        }
}
