package com.chimali.core.domain.eventsourcing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.Instant

class SnapshotPrivacyTest {
    @Test
    fun genericRepresentationDoesNotExposeAggregateOrMaterializedState() {
        val aggregateMarker = "SYNTHETIC_AGGREGATE_MARKER"
        val stateMarker = "SYNTHETIC_SECRET_STATE_MARKER"
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")
        val snapshot = Snapshot(aggregateMarker, 7, stateMarker, timestamp)

        assertFalse(aggregateMarker in snapshot.toString())
        assertFalse(stateMarker in snapshot.toString())
        assertEquals(aggregateMarker, snapshot.aggregateId)
        assertEquals(stateMarker, snapshot.state)
        assertEquals(7L, snapshot.sequenceNumber)
    }
}
