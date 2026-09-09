package com.chimali.core.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventStoreRepositoryImplTest {
    private lateinit var repository: EventStoreRepositoryImpl
    private lateinit var database: VaultDatabase
    private val encryptionManager = mockk<EncryptionManager>()
    private val keyProvider = mockk<EventStoreKeyProvider>()
    private val key = ByteArray(32) { 7 }
    private val timestamp = Instant.parse("2026-09-08T00:00:00Z")

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VaultDatabase.Schema.create(driver)
        database = VaultDatabase(driver)
        coEvery { keyProvider.getEventStoreKey(any()) } returns key
        every { encryptionManager.encrypt(any(), any()) } answers { firstArg() }
        every { encryptionManager.decrypt(any(), any()) } answers { firstArg() }
        repository = EventStoreRepositoryImpl(database, encryptionManager, keyProvider)
    }

    @Test
    fun `append and read preserves created updated and deleted events`() =
        runTest {
            val events =
                listOf(
                    VaultEvent.Created(
                        "aggregate",
                        1,
                        timestamp,
                        "PASSWORD",
                        "Password",
                        byteArrayOf(1, 2),
                        "identity",
                    ),
                    VaultEvent.Created("aggregate", 2, timestamp, "CREDIT_CARD", "Card", byteArrayOf(3), "identity"),
                    VaultEvent.Created("aggregate", 3, timestamp, "NOTE", "Note", byteArrayOf(4), "identity"),
                    VaultEvent.Updated("aggregate", 4, timestamp, "Title 2", byteArrayOf(5, 6)),
                    VaultEvent.Deleted("aggregate", 5, timestamp),
                )

            repository.append(EventKind.VAULT_ENTRY, events).getOrThrow()

            val result = repository.getEvents(EventKind.VAULT_ENTRY, "aggregate", null).getOrThrow()
            assertEquals(5, result.size)
            assertEquals("PASSWORD", (result[0] as VaultEvent.Created).type)
            assertEquals("CREDIT_CARD", (result[1] as VaultEvent.Created).type)
            assertEquals("NOTE", (result[2] as VaultEvent.Created).type)
            assertEquals(listOf<Byte>(1, 2), (result[0] as VaultEvent.Created).payload.toList())
            assertTrue((result[3] as VaultEvent.Updated).title!!.contentEquals("Title 2".toCharArray()))
            assertEquals(listOf<Byte>(5, 6), (result[3] as VaultEvent.Updated).payload?.toList())
            assertEquals(result[0].aggregateId, events[0].aggregateId)
            val fromSequence = repository.getEventsFrom(EventKind.VAULT_ENTRY, "aggregate", 3).getOrThrow()
            assertEquals(2, fromSequence.size)
            assertEquals(4L, fromSequence.first().sequenceNumber)
            assertEquals(5L, fromSequence.last().sequenceNumber)
        }

    @Test
    fun `malformed stored event returns serialization failure`() =
        runTest {
            database.vaultQueries.insert_event(
                "aggregate",
                1,
                timestamp.toString(),
                "{\"eventType\":\"missing\"}".toByteArray(),
            )

            val result = repository.getEvents(EventKind.VAULT_ENTRY, "aggregate", null)

            assertTrue(result.isFailure)
        }

    @Test
    fun `append rolls back all events when one encryption fails`() =
        runTest {
            every { encryptionManager.encrypt(any(), any()) } answers {
                if (firstArg<ByteArray>().decodeToString().contains(
                        "Title 2",
                    )
                ) {
                    throw SerializationException("encryption failure")
                }
                firstArg()
            }
            val events =
                listOf(
                    VaultEvent.Created("aggregate", 1, timestamp, "PASSWORD", "Title", byteArrayOf(1), "identity"),
                    VaultEvent.Updated("aggregate", 2, timestamp, "Title 2", byteArrayOf(2)),
                )

            assertTrue(repository.append(EventKind.VAULT_ENTRY, events).isFailure)
            assertTrue(repository.getEvents(EventKind.VAULT_ENTRY, "aggregate", null).getOrThrow().isEmpty())
        }

    @Test
    fun `append clears the event store key after completion`() =
        runTest {
            val event = VaultEvent.Deleted("aggregate", 1, timestamp)

            repository.append(EventKind.VAULT_ENTRY, listOf(event)).getOrThrow()

            assertTrue(key.all { it == 0.toByte() })
        }

    @Test
    fun `read clears the decrypted event buffer`() =
        runTest {
            val event = VaultEvent.Deleted("aggregate", 1, timestamp)
            repository.append(EventKind.VAULT_ENTRY, listOf(event)).getOrThrow()
            val decrypted =
                VaultEventSerializer.json
                    .encodeToString<com.chimali.core.domain.eventsourcing.DomainEvent>(
                        event,
                    ).toByteArray()
            every { encryptionManager.decrypt(any(), any()) } returns decrypted

            repository.getEvents(EventKind.VAULT_ENTRY, "aggregate", null).getOrThrow()

            assertTrue(decrypted.all { it == 0.toByte() })
        }
}
