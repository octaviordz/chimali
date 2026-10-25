package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyDecider
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * Focused lifecycle tests for PasskeyAggregateServiceImpl snapshot threshold,
 * cancellation propagation, no-op commands, and deleted state protection.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyAggregateServiceLifecycleTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository =
        mockk<SnapshotRepository> {
            coEvery { getLatest<PasskeyState>(any(), any()) } returns Result.success(null)
        }
    private val service = PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `execute returns failure when snapshot save fails at threshold`() =
        runTest {
            val snapshotState =
                PasskeyState(
                    id = aggregateId,
                    rpId = "rp.example".toCharArray(),
                    userId = "user".toCharArray(),
                    userName = "name".toCharArray(),
                    userDisplayName = "Display".toCharArray(),
                    credentialId = ByteArray(32) { it.toByte() },
                    publicKey = "synthetic-public-key",
                    aaguid = "synthetic-aaguid",
                    signCount = 0L,
                    sequenceNumber = 19L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val snapshot =
                Snapshot(
                    aggregateId = aggregateId,
                    sequenceNumber = 19L,
                    state = snapshotState,
                    timestamp = Instant.parse("2026-10-18T00:00:00Z"),
                )
            coEvery { snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, 19L) } returns
                Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } returns Result.success(Unit)
            val snapshotFailure = RuntimeException("synthetic snapshot disk full")
            coEvery { snapshotRepository.save(EventKind.PASSKEY, any<Snapshot<PasskeyState>>()) } returns
                Result.failure(snapshotFailure)

            val authCommand = PasskeyCommand.Authenticate(aggregateId, 1L)
            val result = service.execute(aggregateId, authCommand)

            assertTrue(result.isFailure)
            assertSame(snapshotFailure, result.exceptionOrNull())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `getState propagates CancellationException`() =
        runTest {
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } throws
                CancellationException("synthetic cancel")

            assertFailsWith<CancellationException> {
                service.getState(aggregateId)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `execute returns current state when decider yields no events`() =
        runTest {
            val registered =
                PasskeyEvent.Registered(
                    aggregateId = aggregateId,
                    sequenceNumber = 1L,
                    timestamp = Instant.parse("2026-10-17T00:00:00Z"),
                    rpId = "rp.example",
                    userId = "synthetic-user",
                    userName = "synthetic-user-name",
                    userDisplayName = "Synthetic User",
                    credentialId = ByteArray(32) { it.toByte() },
                    publicKey = "synthetic-public-key",
                    aaguid = "synthetic-aaguid",
                    signCount = 0L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(listOf(registered))

            val mockDecider = mockk<PasskeyDecider>()
            val baseState =
                PasskeyState(
                    id = aggregateId,
                    sequenceNumber = 1L,
                    publicKey = "pub",
                    credentialId = ByteArray(32) { it.toByte() },
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            every { mockDecider.initialState } returns PasskeyState()
            every { mockDecider.evolve(any<PasskeyState>(), any<PasskeyEvent>()) } returns baseState
            every { mockDecider.decide(any<PasskeyState>(), any<PasskeyCommand>()) } returns emptyList<PasskeyEvent>()

            val customService = PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository, mockDecider)
            val authCommand = PasskeyCommand.Authenticate(aggregateId, 1L)
            val result = customService.execute(aggregateId, authCommand)

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) {
                eventStoreRepository.append(
                    any<EventKind>(),
                    any<List<com.chimali.core.domain.eventsourcing.DomainEvent>>(),
                )
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `execute rejects command on deleted state`() =
        runTest {
            val registered =
                PasskeyEvent.Registered(
                    aggregateId = aggregateId,
                    sequenceNumber = 1L,
                    timestamp = Instant.parse("2026-10-17T00:00:00Z"),
                    rpId = "rp.example",
                    userId = "synthetic-user",
                    userName = "synthetic-user-name",
                    userDisplayName = "Synthetic User",
                    credentialId = ByteArray(32) { it.toByte() },
                    publicKey = "synthetic-public-key",
                    aaguid = "synthetic-aaguid",
                    signCount = 0L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val deleted = PasskeyEvent.Deleted(aggregateId, 2L, Instant.parse("2026-10-18T00:00:00Z"))
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(listOf(registered, deleted))

            val authCommand = PasskeyCommand.Authenticate(aggregateId, 1L)
            val result = service.execute(aggregateId, authCommand)

            assertTrue(result.isFailure)
        }
}
