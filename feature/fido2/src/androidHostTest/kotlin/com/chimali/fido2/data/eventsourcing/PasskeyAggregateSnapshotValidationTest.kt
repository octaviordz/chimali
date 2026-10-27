package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/** Verifies partial persisted states cannot be mistaken for valid passkey snapshots. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyAggregateSnapshotValidationTest {
    private val eventStoreRepository = mockk<EventStoreRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val service = PasskeyAggregateServiceImpl(eventStoreRepository, snapshotRepository)
    private val aggregateId = UUID.randomUUID().toString()

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

            coVerify(exactly = partialStates.size) {
                eventStoreRepository.getEvents(EventKind.PASSKEY, aggregateId, any())
            }
            coVerify(exactly = 0) {
                eventStoreRepository.getEventsFrom(EventKind.PASSKEY, aggregateId, any())
            }
        }

    private fun PasskeyEvent.Registered.copyOwnedForTest(): PasskeyEvent.Registered =
        copy(
            rpId = rpId.copyOf(),
            userId = userId.copyOf(),
            userName = userName.copyOf(),
            userDisplayName = userDisplayName.copyOf(),
            credentialId = credentialId.copyOf(),
        )

    private fun registeredEvent(): PasskeyEvent.Registered =
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
            coseAlgorithm = CredentialKeyDescriptor.COSE_ML_DSA_65,
            derivationProfile = CredentialKeyDescriptor.MLDSA65_PROFILE,
        )
}
