package com.chimali.core.domain.eventsourcing.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.Instant

class VaultEventPrivacyTest {
    @Test
    fun genericRepresentationsDoNotExposeAggregateIdentityOrTitle() {
        val marker = "SYNTHETIC_VAULT_EVENT_MARKER"
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")
        val events =
            listOf(
                VaultEvent.Created(marker, 1, timestamp, "password", marker, byteArrayOf(1), marker),
                VaultEvent.Updated(marker, 2, timestamp, marker, byteArrayOf(2)),
                VaultEvent.Deleted(marker, 3, timestamp),
            )

        events.forEach { assertFalse(marker in it.toString()) }
        assertEquals(marker, (events[0] as VaultEvent.Created).aggregateId)
        assertEquals(marker, (events[0] as VaultEvent.Created).identityId)
        assertEquals(marker, (events[0] as VaultEvent.Created).title.concatToString())
        assertEquals(marker, (events[1] as VaultEvent.Updated).aggregateId)
        assertEquals(marker, (events[1] as VaultEvent.Updated).title?.concatToString())
        assertEquals(marker, (events[2] as VaultEvent.Deleted).aggregateId)
    }
}
