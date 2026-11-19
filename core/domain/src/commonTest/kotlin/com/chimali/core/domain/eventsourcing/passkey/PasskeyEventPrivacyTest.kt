package com.chimali.core.domain.eventsourcing.passkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.Instant

class PasskeyEventPrivacyTest {
    @Test
    fun genericRepresentationsDoNotExposeAggregateIdentifier() {
        val marker = "SYNTHETIC_PASSKEY_EVENT_MARKER"
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")
        val events =
            listOf(
                PasskeyEvent.Registered(
                    aggregateId = marker,
                    sequenceNumber = 1,
                    timestamp = timestamp,
                    rpId = "example.test",
                    userId = "user",
                    userName = "name",
                    userDisplayName = "display",
                    credentialId = byteArrayOf(1),
                    publicKey = "public-key",
                    aaguid = "aaguid",
                    signCount = 0,
                ),
                PasskeyEvent.Authenticated(marker, 2, timestamp, 1),
                PasskeyEvent.Deleted(marker, 3, timestamp),
            )

        events.forEach { assertFalse(marker in it.toString()) }
        events.forEach { assertEquals(marker, it.aggregateId) }
    }
}
