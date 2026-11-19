package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.eventsourcing.vault.clearSensitiveMemory
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Frozen legacy schema fixtures verified through the active mutable-buffer production codec. */
class VaultMetadataLegacyCompatibilityTest {
    private val timestamp = Instant.parse("2026-09-09T00:00:00Z")
    private val title = "line\n\"秘密\"\\🔐".toCharArray()

    @After
    fun eraseTitleFixture() {
        title.fill('\u0000')
    }

    @Test
    fun `title-bearing events match frozen legacy JSON byte fixtures`() {
        val events =
            mapOf(
                "created.json" to
                    VaultEvent.Created(
                        "aggregate",
                        1,
                        timestamp,
                        "PASSWORD",
                        title.copyOf(),
                        byteArrayOf(1, -2, 3),
                        "identity",
                    ),
                "updated.json" to
                    VaultEvent.Updated(
                        "aggregate",
                        2,
                        timestamp,
                        title.copyOf(),
                        byteArrayOf(3, -4),
                    ),
                "updated-null.json" to VaultEvent.Updated("aggregate", 2, timestamp, null, null),
            )
        events.forEach { (name, event) ->
            val expected = fixtureBytes(name)
            val encoded = VaultMutableJsonCodec.encodeEvent(event)
            try {
                assertArrayEquals(expected, encoded)
                val decoded = VaultMutableJsonCodec.decodeEvent(encoded)
                try {
                    assertEquals(event.aggregateId, decoded.aggregateId)
                    assertEquals(event.sequenceNumber, decoded.sequenceNumber)
                    when {
                        event is VaultEvent.Created && decoded is VaultEvent.Created -> {
                            assertArrayEquals(event.title, decoded.title)
                            assertArrayEquals(event.payload, decoded.payload)
                        }
                        event is VaultEvent.Updated && decoded is VaultEvent.Updated -> {
                            assertArrayEquals(event.title, decoded.title)
                            assertArrayEquals(event.payload, decoded.payload)
                        }
                        else -> throw AssertionError("Decoded event type changed")
                    }
                } finally {
                    decoded.clearSensitiveMemory()
                }
            } finally {
                expected.fill(0)
                encoded.fill(0)
                event.clearSensitiveMemory()
            }
        }
    }

    @Test
    fun `title-bearing snapshot matches frozen legacy nested JSON bytes`() {
        val state = VaultState("aggregate", "NOTE", title.copyOf(), byteArrayOf(1, -2, 3), "identity", false, 2)
        val snapshot = Snapshot("aggregate", 2, state, timestamp)
        val expected = fixtureBytes("snapshot.json")
        val encoded = VaultMutableJsonCodec.encodeSnapshot(snapshot)
        try {
            assertArrayEquals(expected, encoded)
            val decoded = VaultMutableJsonCodec.decodeSnapshot(encoded)
            try {
                assertEquals(snapshot.aggregateId, decoded.aggregateId)
                assertEquals(snapshot.sequenceNumber, decoded.sequenceNumber)
                assertEquals(snapshot.timestamp, decoded.timestamp)
                assertEquals(snapshot.state, decoded.state)
            } finally {
                decoded.state.clearSensitiveMemory()
            }
        } finally {
            expected.fill(0)
            encoded.fill(0)
            state.clearSensitiveMemory()
        }
    }

    private fun fixtureBytes(name: String): ByteArray {
        val fileBytes =
            checkNotNull(javaClass.getResourceAsStream("/vault-legacy-metadata-v1/$name")).use { it.readBytes() }
        var length = fileBytes.size
        while (length > 0) {
            val lastByte = fileBytes[length - 1]
            if (lastByte != '\n'.code.toByte() && lastByte != '\r'.code.toByte()) break
            length--
        }
        return fileBytes.copyOf(length).also { fileBytes.fill(0) }
    }
}
