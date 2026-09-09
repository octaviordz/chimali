package com.chimali.core.domain.eventsourcing.vault

import com.chimali.core.domain.eventsourcing.DomainEvent
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain events for the VaultEntry aggregate.
 */
@Serializable
sealed interface VaultEvent : DomainEvent {
    /**
     * Emitted when a new vault entry is created.
     */
    @Serializable
    data class Created(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
        val type: String,
        @Serializable(with = SensitiveCharArraySerializer::class)
        val title: CharArray,
        val payload: ByteArray,
        val identityId: String,
    ) : VaultEvent {
        constructor(
            aggregateId: String,
            sequenceNumber: Long,
            timestamp: Instant,
            type: String,
            title: String,
            payload: ByteArray,
            identityId: String,
        ) : this(aggregateId, sequenceNumber, timestamp, type, title.toCharArray(), payload, identityId)
    }

    /**
     * Emitted when an existing vault entry is updated.
     */
    @Serializable
    data class Updated(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
        @Serializable(with = SensitiveCharArraySerializer::class)
        val title: CharArray?,
        val payload: ByteArray?,
    ) : VaultEvent {
        constructor(
            aggregateId: String,
            sequenceNumber: Long,
            timestamp: Instant,
            title: String,
            payload: ByteArray?,
        ) : this(aggregateId, sequenceNumber, timestamp, title.toCharArray(), payload)
    }

    /**
     * Emitted when a vault entry is marked as deleted.
     */
    @Serializable
    data class Deleted(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
    ) : VaultEvent
}

/** Clears event-owned sensitive text after the state has made its own copy. */
fun VaultEvent.clearSensitiveMemory() {
    when (this) {
        is VaultEvent.Created -> title.fill('\u0000')
        is VaultEvent.Updated -> title?.fill('\u0000')
        is VaultEvent.Deleted -> Unit
    }
}
