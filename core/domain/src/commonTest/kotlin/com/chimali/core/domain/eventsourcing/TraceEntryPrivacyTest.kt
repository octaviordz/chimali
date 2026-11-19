package com.chimali.core.domain.eventsourcing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.Instant

class TraceEntryPrivacyTest {
    @Test
    fun genericRepresentationDoesNotExposeDescriptionOrMetadata() {
        val marker = "SYNTHETIC_TRACE_SECRET_MARKER"
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")
        val entry = TraceEntry(timestamp, marker, mapOf("detail" to marker))

        assertFalse(marker in entry.toString())
        assertEquals(marker, entry.description)
        assertEquals(marker, entry.metadata["detail"])
        assertEquals(timestamp, entry.timestamp)
    }
}
