package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.eventsourcing.vault.clearSensitiveMemory
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compatibility and caller-ownership checks for production Vault persistence serialization. */
class VaultMutableJsonCodecTest {
    private val timestamp = Instant.parse("2026-09-09T00:00:00Z")
    private val title = "line\n\"秘密\"\\🔐".toCharArray()

    @After
    fun eraseTitleFixture() {
        title.fill('\u0000')
    }

    @Test
    fun `event codec preserves frozen legacy JSON and decodes it into mutable owners`() {
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

        events.forEach { (fixtureName, event) ->
            val expected = fixtureBytes(fixtureName)
            val encoded = VaultMutableJsonCodec.encodeEvent(event)
            try {
                assertArrayEquals(expected, encoded)
                val decoded = VaultMutableJsonCodec.decodeEvent(encoded)
                try {
                    assertEquals(event.aggregateId, decoded.aggregateId)
                    assertEquals(event.sequenceNumber, decoded.sequenceNumber)
                    assertTrue(eventsMatch(event, decoded))
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
    fun `malformed event errors do not retain parser details or input markers`() {
        val marker = "synthetic invalid timestamp marker"
        val malformed =
            """
            {"eventType":"com.chimali.core.domain.eventsourcing.vault.VaultEvent.Deleted",
            "aggregateId":"aggregate","sequenceNumber":1,"timestamp":"$marker"}
            """.trimIndent().encodeToByteArray()
        try {
            val error =
                assertThrows(SerializationException::class.java) {
                    VaultMutableJsonCodec.decodeEvent(malformed)
                }
            assertNull(error.cause)
            assertFalse(error.toString().contains(marker))
        } finally {
            malformed.fill(0)
        }
    }

    @Test
    fun `snapshot codec preserves legacy nested JSON and round trips mutable fields`() {
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

    @Test
    fun `snapshot codec omits default state fields like legacy serializer`() {
        val snapshot = Snapshot("aggregate", 1, VaultState(), timestamp)
        val encoded = VaultMutableJsonCodec.encodeSnapshot(snapshot)
        try {
            assertEquals(
                """{"aggregateId":"aggregate","sequenceNumber":1,"state":{},"timestamp":"$timestamp"}""",
                encoded.decodeToString(),
            )
            val decoded = VaultMutableJsonCodec.decodeSnapshot(encoded)
            try {
                assertEquals(VaultState(), decoded.state)
            } finally {
                decoded.state.clearSensitiveMemory()
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun eventsMatch(
        expected: VaultEvent,
        actual: VaultEvent,
    ): Boolean =
        when {
            expected is VaultEvent.Created && actual is VaultEvent.Created ->
                expected.type == actual.type &&
                    expected.identityId == actual.identityId &&
                    expected.title.contentEquals(actual.title) &&
                    expected.payload.contentEquals(actual.payload)
            expected is VaultEvent.Updated && actual is VaultEvent.Updated ->
                (
                    expected.title == null &&
                        actual.title == null ||
                        expected.title != null &&
                        actual.title != null &&
                        expected.title.contentEquals(actual.title)
                ) &&
                    (
                        expected.payload == null &&
                            actual.payload == null ||
                            expected.payload != null &&
                            actual.payload != null &&
                            expected.payload.contentEquals(actual.payload)
                    )
            expected is VaultEvent.Deleted && actual is VaultEvent.Deleted -> true
            else -> false
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
