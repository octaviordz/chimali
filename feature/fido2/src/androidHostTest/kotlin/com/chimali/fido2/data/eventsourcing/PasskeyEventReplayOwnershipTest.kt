package com.chimali.fido2.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

class PasskeyEventReplayOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay erases first decoded row plaintext when the next row is malformed`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                lateinit var key: ByteArray
                val keyProvider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } answers { ByteArray(32) { 0x31 }.also { key = it } }
                    }
                val decryptOutputs = mutableListOf<ByteArray>()
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers {
                    firstArg<ByteArray>().copyOf().also(decryptOutputs::add)
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, keyProvider)
                val firstEvent = testEvent("partial-replay-aggregate")
                assertTrue(repository.append(EventKind.PASSKEY, listOf(firstEvent)).isSuccess)
                database.fido2DatabaseQueries.insert_event(
                    aggregate_id = firstEvent.aggregateId,
                    sequence_number = 2,
                    timestamp = "9999-01-01T00:00:00Z",
                    payload = "{ malformed second row".encodeToByteArray(),
                )

                val result = repository.getEvents(EventKind.PASSKEY, firstEvent.aggregateId)

                assertTrue(result.isFailure)
                assertEquals(2, decryptOutputs.size)
                decryptOutputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                assertContentEquals(ByteArray(32), key)
                firstEvent.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay failure erases retained sensitive owners from decoded earlier rows`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val keyProvider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns ByteArray(32) { 0x32 }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, keyProvider)
                val firstEvent = testEvent("retained-partial-event")
                assertTrue(repository.append(EventKind.PASSKEY, listOf(firstEvent)).isSuccess)
                database.fido2DatabaseQueries.insert_event(
                    aggregate_id = firstEvent.aggregateId,
                    sequence_number = 2,
                    timestamp = "9999-01-01T00:00:00Z",
                    payload = "synthetic malformed second row".encodeToByteArray(),
                )
                val retainedEvent = testEvent("retained-partial-event")
                var decodeCount = 0

                val result =
                    repository.getEventsWithDecoder(EventKind.PASSKEY, firstEvent.aggregateId, null) {
                        decodeCount++
                        if (decodeCount == 1) retainedEvent else error("synthetic second-row decode failure")
                    }

                assertTrue(result.isFailure)
                assertEquals(2, decodeCount)
                listOf(
                    retainedEvent.rpId,
                    retainedEvent.userId,
                    retainedEvent.userName,
                    retainedEvent.userDisplayName,
                ).forEach { assertContentEquals(CharArray(it.size), it) }
                firstEvent.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay cancellation after decoding erases retained event owners`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val keyProvider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns ByteArray(32) { 0x33 }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, keyProvider)
                val firstEvent = testEvent("cancelled-partial-event")
                assertTrue(repository.append(EventKind.PASSKEY, listOf(firstEvent)).isSuccess)
                val retainedEvent = testEvent("cancelled-partial-event")
                lateinit var operationJob: Job
                val operation =
                    launch {
                        operationJob = currentCoroutineContext()[Job]!!
                        repository.getEventsWithDecoder(EventKind.PASSKEY, firstEvent.aggregateId, null) {
                            operationJob.cancel(CancellationException("synthetic replay cancellation"))
                            retainedEvent
                        }
                    }

                operation.join()

                assertTrue(operation.isCancelled)
                listOf(
                    retainedEvent.rpId,
                    retainedEvent.userId,
                    retainedEvent.userName,
                    retainedEvent.userDisplayName,
                ).forEach { assertContentEquals(CharArray(it.size), it) }
                firstEvent.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `snapshot cancellation after decoding erases retained state owners`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val keyProvider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns ByteArray(32) { 0x34 }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                val repository = PasskeySnapshotRepositoryImpl(database, encryptionManager, keyProvider)
                val persistedSnapshot =
                    Snapshot(
                        aggregateId = "snapshot-cancelled-owner",
                        sequenceNumber = 1,
                        state =
                            PasskeyState(
                                id = "snapshot-cancelled-owner",
                                rpId = "stored.example".toCharArray(),
                                userId = "stored-user".toCharArray(),
                                userName = "stored-name".toCharArray(),
                                userDisplayName = "Stored User".toCharArray(),
                                publicKey = "synthetic-key",
                                coseAlgorithm = -7,
                                derivationProfile = "chimali-local-hdk-p256-v1",
                            ),
                        timestamp = Clock.System.now(),
                    )
                assertTrue(repository.save(EventKind.PASSKEY, persistedSnapshot).isSuccess)
                val retainedSnapshot =
                    persistedSnapshot.copy(
                        state =
                            (persistedSnapshot.state as PasskeyState).copy(
                                rpId = "retained-rp".toCharArray(),
                                userId = "retained-user".toCharArray(),
                                userName = "retained-name".toCharArray(),
                                userDisplayName = "Retained User".toCharArray(),
                            ),
                    )
                lateinit var operationJob: Job
                val operation =
                    launch {
                        operationJob = currentCoroutineContext()[Job]!!
                        repository.getLatestWithDecoder<PasskeyState>(
                            EventKind.PASSKEY,
                            persistedSnapshot.aggregateId,
                        ) {
                            operationJob.cancel(CancellationException("synthetic snapshot cancellation"))
                            retainedSnapshot
                        }
                    }

                operation.join()

                assertTrue(operation.isCancelled)
                val retainedState = retainedSnapshot.state as PasskeyState
                listOf(
                    retainedState.rpId,
                    retainedState.userId,
                    retainedState.userName,
                    retainedState.userDisplayName,
                ).forEach { assertContentEquals(CharArray(it.size), it) }
                persistedSnapshot.state.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    private fun testEvent(aggregateId: String): PasskeyEvent.Registered =
        PasskeyEvent.Registered(
            aggregateId = aggregateId,
            sequenceNumber = 1,
            timestamp = Clock.System.now(),
            rpId = "example.com",
            userId = "user-1",
            userName = "user",
            userDisplayName = "User",
            credentialId = ByteArray(16) { 0x42 },
            publicKey = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
            aaguid = "synthetic-aaguid",
            signCount = 0,
            coseAlgorithm = -7,
            derivationProfile = "chimali-local-hdk-p256-v1",
        )
}
