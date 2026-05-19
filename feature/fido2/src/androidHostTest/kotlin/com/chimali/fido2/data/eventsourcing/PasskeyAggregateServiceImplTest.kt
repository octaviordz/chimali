package com.chimali.fido2.data.eventsourcing

import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for PasskeyAggregateServiceImpl.
 */
class PasskeyAggregateServiceImplTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository =
        mockk<SnapshotRepository> {
            coEvery { getLatest<com.chimali.core.domain.eventsourcing.passkey.PasskeyState>(any(), any()) } returns
                Result.success(null)
        }
    private val service = PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

    @Test
    fun `getState should hydrate state from events`() =
        runTest {
            val events =
                listOf(
                    PasskeyEvent.Registered(
                        aggregateId,
                        1,
                        kotlinx.datetime.Clock.System
                            .now(),
                        "rpId",
                        "userId",
                        "userName",
                        "displayName",
                        byteArrayOf(1, 2, 3),
                        "pubKey",
                        "aaguid",
                        0,
                    ),
                    PasskeyEvent.Authenticated(
                        aggregateId,
                        2,
                        kotlinx.datetime.Clock.System
                            .now(),
                        1,
                    ),
                )
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(events)

            val state = service.getState(aggregateId).getOrThrow()

            assertEquals(1L, state.signCount)
            assertEquals(2L, state.sequenceNumber)
            assertEquals("userName", state.userName)
        }

    @Test
    fun `getState with asOf should reconstruct state at specific point in time`() =
        runTest {
            val t1 = kotlinx.datetime.Instant.parse("2026-05-13T00:00:00Z")
            val t2 = kotlinx.datetime.Instant.parse("2026-05-13T01:00:00Z")
            val events =
                listOf(
                    PasskeyEvent.Registered(
                        aggregateId,
                        1,
                        t1,
                        "rpId",
                        "userId",
                        "userName 1",
                        "displayName 1",
                        byteArrayOf(1, 2, 3),
                        "pubKey",
                        "aaguid",
                        0,
                    ),
                    PasskeyEvent.Authenticated(aggregateId, 2, t2, 1),
                )

            // Mocking getEvents to filter by timestamp
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, t1) } returns
                Result.success(listOf(events[0]))
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, t2) } returns
                Result.success(events)

            val stateAtT1 = service.getState(aggregateId, t1).getOrThrow()
            val stateAtT2 = service.getState(aggregateId, t2).getOrThrow()

            assertEquals("userName 1", stateAtT1.userName)
            assertEquals(0L, stateAtT1.signCount)
            assertEquals(1L, stateAtT1.sequenceNumber)

            assertEquals("userName 1", stateAtT2.userName)
            assertEquals(1L, stateAtT2.signCount)
            assertEquals(2L, stateAtT2.sequenceNumber)
        }

    @Test
    fun `execute should retry on optimistic concurrency failure`() =
        runTest {
            val command = PasskeyCommand.Delete(aggregateId)

            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(
                    listOf(
                        PasskeyEvent.Registered(
                            aggregateId,
                            1,
                            kotlinx.datetime.Clock.System
                                .now(),
                            "rpId",
                            "userId",
                            "userName",
                            "displayName",
                            byteArrayOf(1, 2, 3),
                            "pubKey",
                            "aaguid",
                            0,
                        ),
                    ),
                )
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } returnsMany
                listOf(
                    Result.failure(RuntimeException("Concurrency error")),
                    Result.success(Unit),
                )

            val state = service.execute(aggregateId, command).getOrThrow()

            assertTrue(state.isDeleted)
            assertEquals(2L, state.sequenceNumber)
        }
}
