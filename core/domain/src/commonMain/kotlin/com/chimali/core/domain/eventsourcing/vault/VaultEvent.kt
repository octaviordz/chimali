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
        val title: String,
        val payload: ByteArray,
        val identityId: String,
    ) : VaultEvent

    /**
     * Emitted when an existing vault entry is updated.
     */
    @Serializable
    data class Updated(
        override val aggregateId: String,
        override val sequenceNumber: Long,
        override val timestamp: Instant,
        val title: String?,
        val payload: ByteArray?,
    ) : VaultEvent

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
