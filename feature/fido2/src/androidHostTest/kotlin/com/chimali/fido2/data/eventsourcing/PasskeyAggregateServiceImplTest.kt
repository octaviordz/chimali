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
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * Unit tests for PasskeyAggregateServiceImpl.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyAggregateServiceImplTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository =
        mockk<SnapshotRepository> {
            coEvery { getLatest<com.chimali.core.domain.eventsourcing.passkey.PasskeyState>(any(), any()) } returns
                Result.success(null)
        }
    private val service = PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
                        ByteArray(32) { it.toByte() },
                        "pubKey",
                        "aaguid",
                        0,
                        CredentialKeyDescriptor.COSE_ES256,
                        CredentialKeyDescriptor.P256_PROFILE,
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
            assertEquals("userName", state.userName.concatToString())
            assertContentEquals(CharArray(4), (events[0] as PasskeyEvent.Registered).rpId)
            assertContentEquals(CharArray(6), (events[0] as PasskeyEvent.Registered).userId)
            assertEquals(CredentialKeyDescriptor.COSE_ES256, state.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.P256_PROFILE, state.derivationProfile)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `getState reconstructs committed deletion history after projection failure`() =
        runTest {
            val registered = registeredEvent()
            val deleted =
                PasskeyEvent.Deleted(
                    aggregateId = aggregateId,
                    sequenceNumber = 2L,
                    timestamp = Instant.parse("2026-10-18T00:00:00Z"),
                )
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(listOf(registered.copyOwnedForTest(), deleted))

            val reconstructed = service.getState(aggregateId).getOrThrow()

            assertTrue(reconstructed.isDeleted)
            assertEquals(2L, reconstructed.sequenceNumber)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
                        ByteArray(32) { it.toByte() },
                        "pubKey",
                        "aaguid",
                        0,
                        CredentialKeyDescriptor.COSE_EDDSA,
                        CredentialKeyDescriptor.ED25519_PROFILE,
                    ),
                    PasskeyEvent.Authenticated(aggregateId, 2, t2, 1),
                )

            // Mocking getEvents to filter by timestamp
            val registered = events[0] as PasskeyEvent.Registered
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, t1) } returns
                Result.success(listOf(registered.copyOwnedForTest()))
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, t2) } returns
                Result.success(listOf(registered.copyOwnedForTest(), events[1]))

            val stateAtT1 = service.getState(aggregateId, t1).getOrThrow()
            val stateAtT2 = service.getState(aggregateId, t2).getOrThrow()

            assertEquals("userName 1", stateAtT1.userName.concatToString())
            assertEquals(0L, stateAtT1.signCount)
            assertEquals(1L, stateAtT1.sequenceNumber)

            assertEquals("userName 1", stateAtT2.userName.concatToString())
            assertEquals(1L, stateAtT2.signCount)
            assertEquals(2L, stateAtT2.sequenceNumber)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
                            ByteArray(32) { it.toByte() },
                            "pubKey",
                            "aaguid",
                            0,
                            CredentialKeyDescriptor.COSE_ML_DSA_65,
                            CredentialKeyDescriptor.MLDSA65_PROFILE,
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `snapshot hydration preserves current key identity and matches full replay`() =
        runTest {
            val registered =
                PasskeyEvent.Registered(
                    aggregateId = aggregateId,
                    sequenceNumber = 1L,
                    timestamp = Instant.parse("2026-10-14T00:00:00Z"),
                    rpId = "rp.example",
                    userId = "user-1",
                    userName = "user@example.com",
                    userDisplayName = "User",
                    credentialId = ByteArray(32) { it.toByte() },
                    publicKey = "encoded-public-key",
                    aaguid = "aaguid",
                    signCount = 0L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ML_DSA_65,
                    derivationProfile = CredentialKeyDescriptor.MLDSA65_PROFILE,
                )
            val authenticated =
                PasskeyEvent.Authenticated(
                    aggregateId = aggregateId,
                    sequenceNumber = 2L,
                    timestamp = Instant.parse("2026-10-14T00:01:00Z"),
                    newSignCount = 1L,
                )
            coEvery {
                eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, registered.timestamp)
            } returns
                Result.success(listOf(registered, authenticated))
            val snapshotState = PasskeyDecider().evolve(PasskeyDecider().initialState, registered)
            coEvery {
                snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId)
            } returns
                Result.success(
                    Snapshot(
                        aggregateId = aggregateId,
                        sequenceNumber = 1L,
                        state = snapshotState,
                        timestamp = registered.timestamp,
                    ),
                )
            coEvery { eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, 1L) } returns
                Result.success(listOf(authenticated))

            val replayed = service.getState(aggregateId, asOf = registered.timestamp).getOrThrow()
            val fromSnapshot = service.getState(aggregateId).getOrThrow()

            assertEquals(replayed, fromSnapshot)
            assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, fromSnapshot.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.MLDSA65_PROFILE, fromSnapshot.derivationProfile)
            assertTrue(fromSnapshot.credentialId.contentEquals(registered.credentialId))
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `corrupt snapshots fall back to full history with empty and nonempty suffixes`() =
        runTest {
            data class Corruption(
                val coseAlgorithm: Int,
                val derivationProfile: String,
                val mismatchStateId: Boolean = false,
                val mismatchSnapshotSequence: Boolean = false,
            )

            val corruptions =
                listOf(
                    Corruption(0, ""),
                    Corruption(CredentialKeyDescriptor.COSE_ES256, CredentialKeyDescriptor.MLDSA65_PROFILE),
                    Corruption(
                        CredentialKeyDescriptor.COSE_ML_DSA_65,
                        CredentialKeyDescriptor.MLDSA65_PROFILE,
                        mismatchStateId = true,
                    ),
                    Corruption(
                        CredentialKeyDescriptor.COSE_ML_DSA_65,
                        CredentialKeyDescriptor.MLDSA65_PROFILE,
                        mismatchSnapshotSequence = true,
                    ),
                )

            corruptions.forEach { corruption ->
                listOf(false, true).forEach { hasSuffix ->
                    val registered = registeredEvent()
                    val suffix =
                        if (hasSuffix) {
                            listOf(
                                PasskeyEvent.Authenticated(
                                    aggregateId = aggregateId,
                                    sequenceNumber = 2L,
                                    timestamp = registered.timestamp,
                                    newSignCount = 1L,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    val completeHistory = listOf(registered) + suffix
                    val sourceState = PasskeyDecider().evolve(PasskeyState(), registered)
                    val invalidSnapshotState =
                        sourceState.copy(
                            id = if (corruption.mismatchStateId) "different-aggregate" else sourceState.id,
                            rpId = sourceState.rpId.copyOf(),
                            userId = sourceState.userId.copyOf(),
                            userName = sourceState.userName.copyOf(),
                            userDisplayName = sourceState.userDisplayName.copyOf(),
                            coseAlgorithm = corruption.coseAlgorithm,
                            derivationProfile = corruption.derivationProfile,
                        )
                    sourceState.eraseSensitiveMetadata()
                    val snapshotSequence = if (corruption.mismatchSnapshotSequence) 2L else 1L
                    val snapshot = Snapshot(aggregateId, snapshotSequence, invalidSnapshotState, registered.timestamp)

                    coEvery { snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId) } returns
                        Result.success(snapshot)
                    coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                        Result.success(completeHistory)
                    coEvery { eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, 1L) } returns
                        Result.success(suffix)

                    val restored = service.getState(aggregateId).getOrThrow()

                    try {
                        assertEquals(if (hasSuffix) 2L else 1L, restored.sequenceNumber)
                        assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, restored.coseAlgorithm)
                        assertEquals(CredentialKeyDescriptor.MLDSA65_PROFILE, restored.derivationProfile)
                        assertContentEquals(registered.credentialId, restored.credentialId)
                        assertContentEquals(CharArray(invalidSnapshotState.rpId.size), invalidSnapshotState.rpId)
                        assertContentEquals(CharArray(invalidSnapshotState.userId.size), invalidSnapshotState.userId)
                        assertContentEquals(
                            CharArray(invalidSnapshotState.userName.size),
                            invalidSnapshotState.userName,
                        )
                        assertContentEquals(
                            CharArray(invalidSnapshotState.userDisplayName.size),
                            invalidSnapshotState.userDisplayName,
                        )
                    } finally {
                        restored.eraseSensitiveMetadata()
                        registered.eraseSensitiveMetadata()
                    }
                }
            }

            io.mockk.coVerify(exactly = 0) {
                eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, any())
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `partially initialized snapshots are rejected and sensitive fields erased before replay`() =
        runTest {
            val partialStates =
                listOf(
                    PasskeyState().copy(id = aggregateId),
                    PasskeyState().copy(rpId = "rp.example".toCharArray()),
                    PasskeyState().copy(userId = "user".toCharArray()),
                    PasskeyState().copy(userName = "name".toCharArray()),
                    PasskeyState().copy(userDisplayName = "display".toCharArray()),
                    PasskeyState().copy(credentialId = byteArrayOf(1)),
                    PasskeyState().copy(publicKey = "public"),
                    PasskeyState().copy(aaguid = "aaguid"),
                    PasskeyState().copy(signCount = 1L),
                    PasskeyState().copy(isDeleted = true),
                    PasskeyState().copy(sequenceNumber = 1L),
                    PasskeyState().copy(coseAlgorithm = CredentialKeyDescriptor.COSE_ES256),
                    PasskeyState().copy(derivationProfile = CredentialKeyDescriptor.P256_PROFILE),
                )

            partialStates.forEach { invalidSnapshotState ->
                val registered = registeredEvent()
                coEvery { snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId) } returns
                    Result.success(
                        Snapshot(
                            aggregateId,
                            invalidSnapshotState.sequenceNumber,
                            invalidSnapshotState,
                            registered.timestamp,
                        ),
                    )
                coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                    Result.success(listOf(registered.copyOwnedForTest()))

                val restored = service.getState(aggregateId).getOrThrow()

                try {
                    assertEquals(1L, restored.sequenceNumber)
                    assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, restored.coseAlgorithm)
                    assertEquals(CredentialKeyDescriptor.MLDSA65_PROFILE, restored.derivationProfile)
                    assertContentEquals(CharArray(invalidSnapshotState.rpId.size), invalidSnapshotState.rpId)
                    assertContentEquals(CharArray(invalidSnapshotState.userId.size), invalidSnapshotState.userId)
                    assertContentEquals(CharArray(invalidSnapshotState.userName.size), invalidSnapshotState.userName)
                    assertContentEquals(
                        CharArray(invalidSnapshotState.userDisplayName.size),
                        invalidSnapshotState.userDisplayName,
                    )
                } finally {
                    restored.eraseSensitiveMetadata()
                    registered.eraseSensitiveMetadata()
                }
            }

            io.mockk.coVerify(exactly = partialStates.size) {
                eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any())
            }
            io.mockk.coVerify(exactly = 0) {
                eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, any())
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `invalid current Registered event returns a fixed failure and erases event metadata`() =
        runTest {
            val invalid = registeredEvent(coseAlgorithm = 0, derivationProfile = "")
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(listOf(invalid))

            val result = service.getState(aggregateId)

            assertTrue(result.isFailure)
            assertEquals("Passkey aggregate data is invalid", result.exceptionOrNull()?.message)
            assertContentEquals(CharArray(invalid.rpId.size), invalid.rpId)
            assertContentEquals(CharArray(invalid.userId.size), invalid.userId)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `snapshot cancellation propagates after erasing snapshot metadata`() =
        runTest {
            val registered = registeredEvent()
            val snapshotState = PasskeyDecider().evolve(PasskeyState(), registered)
            val snapshot = Snapshot(aggregateId, 1L, snapshotState, registered.timestamp)
            coEvery { snapshotRepository.getLatest<PasskeyState>(EventKind.PASSKEY, aggregateId) } returns
                Result.success(snapshot)
            coEvery { eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, 1L) } throws
                CancellationException("synthetic snapshot replay cancellation")

            assertFailsWith<CancellationException> { service.getState(aggregateId) }
            assertContentEquals(CharArray(snapshotState.rpId.size), snapshotState.rpId)
            assertContentEquals(CharArray(snapshotState.userId.size), snapshotState.userId)
            registered.eraseSensitiveMetadata()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `snapshot cadence remains every twentieth event`() =
        runTest {
            val events = mutableListOf<com.chimali.core.domain.eventsourcing.DomainEvent>()
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } coAnswers {
                Result.success(events.toList())
            }
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } coAnswers {
                events.addAll(secondArg())
                Result.success(Unit)
            }
            coEvery { snapshotRepository.save(EventKind.PASSKEY, any<Snapshot<PasskeyState>>()) } returns
                Result.success(Unit)

            service
                .execute(
                    aggregateId,
                    PasskeyCommand.Register(
                        id = aggregateId,
                        rpId = "rp.example".toCharArray(),
                        userId = "synthetic-user".toCharArray(),
                        userName = "synthetic-user-name".toCharArray(),
                        userDisplayName = "Synthetic User".toCharArray(),
                        credentialId = ByteArray(32) { it.toByte() },
                        publicKey = "synthetic-public-key",
                        aaguid = "synthetic-aaguid",
                        signCount = 0L,
                        coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    ),
                ).getOrThrow()

            repeat(19) { index ->
                service
                    .execute(
                        aggregateId,
                        PasskeyCommand.Authenticate(aggregateId, (index + 1).toLong()),
                    ).getOrThrow()
            }

            io.mockk.coVerify(exactly = 1) { snapshotRepository.save(EventKind.PASSKEY, any<Snapshot<PasskeyState>>()) }
            assertEquals(20L, service.getState(aggregateId).getOrThrow().sequenceNumber)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `registration erases command and event owners while returning independent state`() =
        runTest {
            val rpId = "rp.example".toCharArray()
            val userId = "synthetic-user".toCharArray()
            val userName = "synthetic-name".toCharArray()
            val displayName = "Synthetic User".toCharArray()
            val command =
                PasskeyCommand.Register(
                    id = aggregateId,
                    rpId = rpId,
                    userId = userId,
                    userName = userName,
                    userDisplayName = displayName,
                    credentialId = ByteArray(16) { 0x4a },
                    publicKey = "synthetic-public-key",
                    aaguid = "synthetic-aaguid",
                    signCount = 0L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            lateinit var storedEvent: PasskeyEvent.Registered
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } coAnswers {
                storedEvent = secondArg<List<PasskeyEvent.Registered>>().single()
                Result.success(Unit)
            }

            val state = service.execute(aggregateId, command).getOrThrow()

            assertContentEquals(CharArray(rpId.size), rpId)
            assertContentEquals(CharArray(userId.size), userId)
            assertContentEquals(CharArray(userName.size), userName)
            assertContentEquals(CharArray(displayName.size), displayName)
            assertContentEquals(CharArray(storedEvent.rpId.size), storedEvent.rpId)
            assertContentEquals(CharArray(storedEvent.userId.size), storedEvent.userId)
            assertContentEquals("rp.example".toCharArray(), state.rpId)
            assertContentEquals("synthetic-user".toCharArray(), state.userId)
            assertFalse(state.toString().contains("rp.example"))
            assertFalse(state.toString().contains("synthetic-user"))
        }

    /** @see FR-003, FR-004 and NFR-SEC-041 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `invalid commands fail before append and leave valid history writable`() =
        runTest {
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } returns Result.success(Unit)

            val invalidCommands =
                listOf(
                    PasskeyCommand.Register(
                        id = aggregateId,
                        rpId = "rp.example".toCharArray(),
                        userId = "user".toCharArray(),
                        userName = "name".toCharArray(),
                        userDisplayName = "display".toCharArray(),
                        credentialId = byteArrayOf(1, 2, 3),
                        publicKey = "valid-public-key",
                        aaguid = "synthetic-aaguid",
                        signCount = 0,
                        coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    ),
                    PasskeyCommand.Register(
                        id = aggregateId,
                        rpId = "rp.example".toCharArray(),
                        userId = "user".toCharArray(),
                        userName = "name".toCharArray(),
                        userDisplayName = "display".toCharArray(),
                        credentialId = ByteArray(16) { it.toByte() },
                        publicKey = " ",
                        aaguid = "synthetic-aaguid",
                        signCount = 0,
                        coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    ),
                    PasskeyCommand.Authenticate(aggregateId, 1),
                    PasskeyCommand.Delete(aggregateId),
                )

            invalidCommands.forEach { command ->
                val result = service.execute(aggregateId, command)
                assertTrue(result.isFailure)
                assertEquals("Passkey command is invalid", result.exceptionOrNull()?.message)
            }
            io.mockk.coVerify(exactly = 0) { eventStoreRepository.append(EventKind.PASSKEY, any()) }
            io.mockk.coVerify(exactly = 0) { snapshotRepository.save(EventKind.PASSKEY, any<Snapshot<PasskeyState>>()) }

            val valid =
                service.execute(
                    aggregateId,
                    PasskeyCommand.Register(
                        id = aggregateId,
                        rpId = "rp.example".toCharArray(),
                        userId = "user".toCharArray(),
                        userName = "name".toCharArray(),
                        userDisplayName = "display".toCharArray(),
                        credentialId = ByteArray(16) { it.toByte() },
                        publicKey = "valid-public-key",
                        aaguid = "synthetic-aaguid",
                        signCount = 0,
                        coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                        derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    ),
                )
            assertTrue(valid.isSuccess)
            assertEquals(1L, valid.getOrThrow().sequenceNumber)
            io.mockk.coVerify(exactly = 1) { eventStoreRepository.append(EventKind.PASSKEY, any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `snapshot failure after append is returned without being mislabeled as invalid command`() =
        runTest {
            val registered =
                registeredEvent(
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val history =
                buildList<PasskeyEvent> {
                    add(registered)
                    for (sequence in 2L..19L) {
                        add(
                            PasskeyEvent.Authenticated(
                                aggregateId = aggregateId,
                                sequenceNumber = sequence,
                                timestamp = registered.timestamp,
                                newSignCount = sequence - 1,
                            ),
                        )
                    }
                }
            val snapshotFailure = IllegalArgumentException("synthetic snapshot persistence failure")
            var appendedEvents: List<PasskeyEvent>? = null
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(
                    history.map { event ->
                        if (event is PasskeyEvent.Registered) {
                            event.copyOwnedForTest()
                        } else {
                            event
                        }
                    },
                )
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } coAnswers {
                appendedEvents = secondArg()
                Result.success(Unit)
            }
            coEvery { snapshotRepository.save<PasskeyState>(EventKind.PASSKEY, any()) } returns
                Result.failure(snapshotFailure)

            val result = service.execute(aggregateId, PasskeyCommand.Authenticate(aggregateId, newSignCount = 19L))

            assertTrue(result.isFailure)
            assertSame(snapshotFailure, result.exceptionOrNull())
            assertEquals(1, appendedEvents?.size)
            assertEquals(20L, appendedEvents?.single()?.sequenceNumber)
            io.mockk.coVerify(exactly = 1) { eventStoreRepository.append(EventKind.PASSKEY, any()) }
            io.mockk.coVerify(exactly = 1) { snapshotRepository.save<PasskeyState>(EventKind.PASSKEY, any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `append IllegalArgumentException remains a persistence failure`() =
        runTest {
            val appendFailure = IllegalArgumentException("synthetic append persistence failure")
            val registered =
                registeredEvent(
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(listOf(registered.copyOwnedForTest()))
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } throws appendFailure

            val result = service.execute(aggregateId, PasskeyCommand.Delete(aggregateId))

            assertTrue(result.isFailure)
            assertSame(appendFailure, result.exceptionOrNull())
            io.mockk.coVerify(exactly = 1) { eventStoreRepository.append(EventKind.PASSKEY, any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `cancelled registration append erases command and prepared event owners`() =
        runTest {
            val rpId = "cancel.example".toCharArray()
            val userId = "cancel-user".toCharArray()
            val userName = "cancel-name".toCharArray()
            val displayName = "Cancel User".toCharArray()
            val command =
                PasskeyCommand.Register(
                    id = aggregateId,
                    rpId = rpId,
                    userId = userId,
                    userName = userName,
                    userDisplayName = displayName,
                    credentialId = ByteArray(16) { 0x4b },
                    publicKey = "synthetic-public-key",
                    aaguid = "synthetic-aaguid",
                    signCount = 0L,
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            lateinit var preparedEvent: PasskeyEvent.Registered
            coEvery { eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any()) } returns
                Result.success(emptyList())
            coEvery { eventStoreRepository.append(EventKind.PASSKEY, any()) } coAnswers {
                preparedEvent = (secondArg<List<PasskeyEvent>>().single() as PasskeyEvent.Registered)
                throw CancellationException("synthetic cancellation")
            }

            assertFailsWith<CancellationException> { service.execute(aggregateId, command) }

            assertContentEquals(CharArray(rpId.size), rpId)
            assertContentEquals(CharArray(userId.size), userId)
            assertContentEquals(CharArray(userName.size), userName)
            assertContentEquals(CharArray(displayName.size), displayName)
            assertContentEquals(CharArray(preparedEvent.rpId.size), preparedEvent.rpId)
            assertContentEquals(CharArray(preparedEvent.userId.size), preparedEvent.userId)
        }

    private fun PasskeyEvent.Registered.copyOwnedForTest(): PasskeyEvent.Registered =
        copy(
            rpId = rpId.copyOf(),
            userId = userId.copyOf(),
            userName = userName.copyOf(),
            userDisplayName = userDisplayName.copyOf(),
            credentialId = credentialId.copyOf(),
        )

    private fun registeredEvent(
        coseAlgorithm: Int = CredentialKeyDescriptor.COSE_ML_DSA_65,
        derivationProfile: String = CredentialKeyDescriptor.MLDSA65_PROFILE,
    ): PasskeyEvent.Registered =
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
            coseAlgorithm = coseAlgorithm,
            derivationProfile = derivationProfile,
        )
}
