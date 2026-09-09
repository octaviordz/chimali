package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EventStoreSerializerTest {
    private val timestamp = Instant.parse("2026-09-08T00:00:00Z")

    @Test
    fun `created event round trips without type discriminator collision`() {
        val event = VaultEvent.Created("aggregate", 1, timestamp, "PASSWORD", "Title", byteArrayOf(1, 2), "identity")

        val encoded = VaultEventSerializer.json.encodeToString<DomainEvent>(event)
        val decoded =
            VaultEventSerializer.json.decodeFromString<DomainEvent>(
                encoded,
            )

        assertEquals(event.aggregateId, decoded.aggregateId)
        assertEquals(event.sequenceNumber, decoded.sequenceNumber)
        assertEquals(event.type, (decoded as VaultEvent.Created).type)
        assertArrayEquals(event.payload, decoded.payload)
    }

    @Test
    fun `updated and deleted events round trip`() {
        val updated = VaultEvent.Updated("aggregate", 2, timestamp, "Updated", byteArrayOf(3, 4))
        val deleted = VaultEvent.Deleted("aggregate", 3, timestamp)

        val decodedUpdated =
            VaultEventSerializer.json.decodeFromString<DomainEvent>(
                VaultEventSerializer.json.encodeToString<DomainEvent>(updated),
            )
        val decodedDeleted =
            VaultEventSerializer.json.decodeFromString<DomainEvent>(
                VaultEventSerializer.json.encodeToString<DomainEvent>(deleted),
            )

        assertEquals(updated.aggregateId, decodedUpdated.aggregateId)
        assertEquals(updated.sequenceNumber, decodedUpdated.sequenceNumber)
        assertArrayEquals(updated.title, (decodedUpdated as VaultEvent.Updated).title)
        assertArrayEquals(updated.payload, decodedUpdated.payload)
        assertEquals(deleted, decodedDeleted)
    }

    @Test(expected = SerializationException::class)
    fun `malformed event payload is rejected`() {
        VaultEventSerializer.json.decodeFromString<DomainEvent>("{\"eventType\":\"missing\"}")
    }
}
