package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Frozen independently authored bytes, verified against the pre-title-remediation serializers. */
class VaultMetadataLegacyCompatibilityTest {
    private val timestamp = Instant.parse("2026-09-09T00:00:00Z")
    private val title = "line\n\"秘密\"\\🔐"

    @Test
    fun `title-bearing events match frozen legacy JSON string schema`() {
        val events =
            mapOf(
                "created.json" to
                    VaultEvent.Created("aggregate", 1, timestamp, "PASSWORD", title, byteArrayOf(1, -2, 3), "identity"),
                "updated.json" to VaultEvent.Updated("aggregate", 2, timestamp, title, byteArrayOf(3, -4)),
                "updated-null.json" to VaultEvent.Updated("aggregate", 2, timestamp, null, null),
            )
        events.forEach { (fixture, event) ->
            val expected = fixture(fixture)
            assertEquals(expected, VaultEventSerializer.json.encodeToString<DomainEvent>(event))
            val decoded = VaultEventSerializer.json.decodeFromString<DomainEvent>(expected)
            assertEquals(event.aggregateId, decoded.aggregateId)
            assertEquals(event.sequenceNumber, decoded.sequenceNumber)
            when (event) {
                is VaultEvent.Created -> {
                    assertArrayEquals(event.title, (decoded as VaultEvent.Created).title)
                    assertArrayEquals(event.payload, decoded.payload)
                }
                is VaultEvent.Updated -> {
                    assertArrayEquals(event.title, (decoded as VaultEvent.Updated).title)
                    assertArrayEquals(event.payload, decoded.payload)
                }
                else -> error("Unexpected fixture event")
            }
        }
    }

    @Test
    fun `title-bearing snapshot matches frozen legacy nested string schema`() {
        val state = VaultState("aggregate", "NOTE", title, byteArrayOf(1, -2, 3), "identity", false, 2)
        val snapshot = Snapshot("aggregate", 2, state, timestamp)
        val serializer = Snapshot.serializer(VaultState.serializer())
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(fixture("snapshot.json"), json.encodeToString(serializer, snapshot))
        assertEquals(snapshot, json.decodeFromString(serializer, fixture("snapshot.json")))
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/vault-legacy-metadata-v1/$name"))
            .readText(Charsets.UTF_8)
            .trimEnd('\n', '\r')
}
