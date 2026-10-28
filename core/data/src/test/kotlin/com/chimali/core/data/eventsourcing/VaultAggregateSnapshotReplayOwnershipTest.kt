package com.chimali.core.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
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

class VaultAggregateSnapshotReplayOwnershipTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository =
        mockk<SnapshotRepository> {
            coEvery { getLatest<VaultState>(any(), any()) } returns Result.success(null)
        }
    private val service = VaultAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

    // Requirement: NFR-ARCH-050 (Snapshot acceleration and differential hydration)
    @Req("NFR-ARCH-050")
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
            assertTrue(snapshotState.title.all { it == '\u0000' })
            assertTrue(snapshotState.payload.all { it == 0.toByte() })
            assertTrue(subsequentEvents.single().title!!.all { it == '\u0000' })
            assertTrue(state.payload.contentEquals(byteArrayOf(1, 2)))
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState preserves snapshot when replay has no later events`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "snapshot title".toCharArray(),
                    byteArrayOf(1, 2),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } returns
                Result.success(emptyList())

            val state = service.getState(aggregateId).getOrThrow()

            assertTrue(state === snapshotState)
            assertTrue(state.title.contentEquals("snapshot title".toCharArray()))
            assertTrue(state.payload.contentEquals(byteArrayOf(1, 2)))
            coVerify(exactly = 0) { eventStoreRepository.getEvents(EventKind.VAULT_ENTRY, aggregateId, any()) }
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState preserves snapshot when replay decider returns the same owned instance`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "snapshot title".toCharArray(),
                    byteArrayOf(1, 2),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            val event =
                VaultEvent.Updated(
                    aggregateId,
                    3L,
                    kotlinx.datetime.Clock.System
                        .now(),
                    "replay title",
                    byteArrayOf(3),
                )
            val replayDecider = mockk<VaultDecider>()
            every { replayDecider.evolve(snapshotState, event) } returns snapshotState
            val replayService = VaultAggregateServiceImpl(eventStoreRepository, snapshotRepository, replayDecider)
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } returns
                Result.success(listOf(event))

            val state = replayService.getState(aggregateId).getOrThrow()

            assertTrue(state === snapshotState)
            assertTrue(state.title.contentEquals("snapshot title".toCharArray()))
            assertTrue(state.payload.contentEquals(byteArrayOf(1, 2)))
            assertTrue(event.title!!.all { it == '\u0000' })
            assertTrue(event.payload!!.all { it == 0.toByte() })
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState clears snapshot buffers when replay read returns failure`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "sensitive title".toCharArray(),
                    byteArrayOf(7, 8),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            val failure = IllegalStateException("replay unavailable")
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } returns
                Result.failure(failure)

            val result = service.getState(aggregateId)

            assertTrue(result.exceptionOrNull() === failure)
            assertTrue(snapshotState.title.all { it == '\u0000' })
            assertTrue(snapshotState.payload.all { it == 0.toByte() })
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState clears snapshot buffers when replay read throws cancellation`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "sensitive title".toCharArray(),
                    byteArrayOf(7, 8),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            val cancellation = kotlinx.coroutines.CancellationException("replay cancelled")
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } throws cancellation

            var propagated: Throwable? = null
            try {
                service.getState(aggregateId)
            } catch (failure: Throwable) {
                propagated = failure
            }

            assertTrue(propagated === cancellation)
            assertTrue(snapshotState.title.all { it == '\u0000' })
            assertTrue(snapshotState.payload.all { it == 0.toByte() })
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState clears owned state and remaining events when replay fold throws`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "sensitive title".toCharArray(),
                    byteArrayOf(7, 8),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            val first =
                VaultEvent.Updated(
                    aggregateId,
                    3L,
                    kotlinx.datetime.Clock.System
                        .now(),
                    "next title",
                    byteArrayOf(9),
                )
            val second =
                VaultEvent.Updated(
                    aggregateId,
                    4L,
                    kotlinx.datetime.Clock.System
                        .now(),
                    "later title",
                    byteArrayOf(10),
                )
            val decider = mockk<VaultDecider>()
            every { decider.evolve(any(), first) } throws IllegalStateException("replay interrupted")
            val replayService = VaultAggregateServiceImpl(eventStoreRepository, snapshotRepository, decider)
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } returns
                Result.success(listOf(first, second))

            var propagated: Throwable? = null
            try {
                replayService.getState(aggregateId)
            } catch (failure: Throwable) {
                propagated = failure
            }

            assertTrue(propagated is IllegalStateException)
            assertEquals("replay interrupted", propagated?.message)
            assertTrue(snapshotState.title.all { it == '\u0000' })
            assertTrue(snapshotState.payload.all { it == 0.toByte() })
            assertTrue(first.title!!.all { it == '\u0000' })
            assertTrue(first.payload!!.all { it == 0.toByte() })
            assertTrue(second.title!!.all { it == '\u0000' })
            assertTrue(second.payload!!.all { it == 0.toByte() })
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }

    @Req("NFR-ARCH-050", "NFR-SEC-041")
    @Test
    fun `getState clears snapshot and later events when replay contains a non-vault event`() =
        runTest {
            val snapshotState =
                VaultState(
                    aggregateId,
                    "login",
                    "sensitive title".toCharArray(),
                    byteArrayOf(7, 8),
                    "identity",
                    false,
                    2L,
                )
            val snapshot =
                Snapshot(
                    aggregateId,
                    2L,
                    snapshotState,
                    kotlinx.datetime.Clock.System
                        .now(),
                )
            val unsupportedEvent = mockk<DomainEvent>()
            val laterEvent =
                VaultEvent.Updated(
                    aggregateId,
                    4L,
                    kotlinx.datetime.Clock.System
                        .now(),
                    "later title",
                    byteArrayOf(10),
                )
            coEvery { snapshotRepository.getLatest<VaultState>(EventKind.VAULT_ENTRY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.VAULT_ENTRY, aggregateId, 2L) } returns
                Result.success(listOf(unsupportedEvent, laterEvent))

            var propagated: Throwable? = null
            try {
                service.getState(aggregateId)
            } catch (failure: Throwable) {
                propagated = failure
            }

            assertTrue(propagated is ClassCastException)
            assertTrue(snapshotState.title.all { it == '\u0000' })
            assertTrue(snapshotState.payload.all { it == 0.toByte() })
            assertTrue(laterEvent.title!!.all { it == '\u0000' })
            assertTrue(laterEvent.payload!!.all { it == 0.toByte() })
            coVerify(exactly = 0) { eventStoreRepository.append(EventKind.VAULT_ENTRY, any()) }
            coVerify(exactly = 0) { snapshotRepository.save(EventKind.VAULT_ENTRY, any<Snapshot<VaultState>>()) }
        }
}
