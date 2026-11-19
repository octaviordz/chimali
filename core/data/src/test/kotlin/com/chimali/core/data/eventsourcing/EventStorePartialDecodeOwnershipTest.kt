package com.chimali.core.data.eventsourcing

import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStorePartialDecodeOwnershipTest {
    @Test
    fun `already decoded event owners are erased if a later row fails`() {
        val title = "synthetic title".toCharArray()
        val payload = byteArrayOf(1, 2, 3)
        val event =
            VaultEvent.Created(
                aggregateId = "synthetic-aggregate",
                sequenceNumber = 1,
                timestamp = kotlinx.datetime.Instant.parse("2026-09-08T00:00:00Z"),
                type = "NOTE",
                title = title,
                payload = payload,
                identityId = "synthetic-identity",
            )

        try {
            decodeOwnedVaultEvents(listOf(1, 2)) { row ->
                if (row == 1) event else throw SerializationException("synthetic decode failure")
            }
            throw AssertionError("The second row must fail decoding")
        } catch (_: SerializationException) {
            // Expected: the partially decoded batch must not transfer its event owners.
        }

        assertTrue("Previously decoded title must be erased", title.all { it == '\u0000' })
        assertTrue("Previously decoded payload must be erased", payload.all { it == 0.toByte() })
    }
}
