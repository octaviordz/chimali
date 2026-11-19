// Raw storage/codec exceptions are deliberately discarded after cancellation is rethrown.
@file:Suppress("SwallowedException", "TooGenericExceptionCaught")

package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.clearSensitiveMemory
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import org.koin.core.annotation.Single

/** Decodes a batch as one ownership transfer; no prefix escapes when a later row fails. */
internal fun <T> decodeOwnedVaultEvents(
    rows: Iterable<T>,
    decode: (T) -> VaultEvent,
): List<VaultEvent> {
    val owned = mutableListOf<VaultEvent>()
    var transferred = false
    try {
        rows.forEach { row ->
            val event = decode(row)
            var appended = false
            try {
                owned.add(event)
                appended = true
            } finally {
                if (!appended) event.clearSensitiveMemory()
            }
        }
        transferred = true
        return owned
    } finally {
        if (!transferred) owned.forEach(VaultEvent::clearSensitiveMemory)
    }
}

/**
 * Implementation of EventStoreRepository for the VaultDatabase.
 * Handles serialization and encryption of Vault aggregate events.
 */
@Single
class EventStoreRepositoryImpl(
    private val database: VaultDatabase,
    private val encryptionManager: EncryptionManager,
    private val keyProvider: EventStoreKeyProvider,
) : EventStoreRepository {
    override suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
    ): Result<Unit> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(Unit)

        var key: ByteArray? = null
        return try {
            val eventStoreKey = keyProvider.getEventStoreKey("chimali_vault_es_v1")
            key = eventStoreKey

            database.transaction {
                events.forEach { event ->
                    val vaultEvent =
                        event as? VaultEvent ?: throw SerializationException("Unsupported Vault event type")
                    val plaintext = VaultMutableJsonCodec.encodeEvent(vaultEvent)
                    try {
                        val encryptedPayload = encryptionManager.encrypt(plaintext, eventStoreKey)
                        database.vaultQueries.insert_event(
                            aggregate_id = event.aggregateId,
                            sequence_number = event.sequenceNumber,
                            timestamp = event.timestamp.toString(),
                            payload = encryptedPayload,
                        )
                    } finally {
                        plaintext.fill(0)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(IllegalStateException("Vault event storage operation failed"))
        } catch (e: SerializationException) {
            Result.failure(SerializationException("Vault event serialization failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Vault event operation failed"))
        } finally {
            key?.fill(0)
        }
    }

    override suspend fun getEvents(
        kind: EventKind,
        aggregateId: String,
        upTo: Instant?,
    ): Result<List<DomainEvent>> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(emptyList())

        var key: ByteArray? = null
        return try {
            // Using a very large timestamp string if upTo is null to fetch all events
            val timestampLimit = upTo?.toString() ?: "9999-12-31T23:59:59Z"

            val eventStoreKey = keyProvider.getEventStoreKey("chimali_vault_es_v1")
            key = eventStoreKey

            val rows =
                database.vaultQueries
                    .get_events(
                        aggregate_id = aggregateId,
                        timestamp = timestampLimit,
                    ).executeAsList()
            val events =
                decodeOwnedVaultEvents(rows) { row ->
                    val decryptedPayload = encryptionManager.decrypt(row.payload, eventStoreKey)
                    try {
                        VaultMutableJsonCodec.decodeEvent(decryptedPayload)
                    } finally {
                        decryptedPayload.fill(0)
                    }
                }
            Result.success(events)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(IllegalStateException("Vault event storage operation failed"))
        } catch (e: SerializationException) {
            Result.failure(SerializationException("Vault event serialization failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Vault event operation failed"))
        } finally {
            key?.fill(0)
        }
    }

    override suspend fun getEventsFrom(
        kind: EventKind,
        aggregateId: String,
        fromSequence: Long,
    ): Result<List<DomainEvent>> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(emptyList())

        var key: ByteArray? = null
        return try {
            val eventStoreKey = keyProvider.getEventStoreKey("chimali_vault_es_v1")
            key = eventStoreKey

            val rows =
                database.vaultQueries
                    .get_events_from(
                        aggregate_id = aggregateId,
                        sequence_number = fromSequence,
                    ).executeAsList()
            val events =
                decodeOwnedVaultEvents(rows) { row ->
                    val decryptedPayload = encryptionManager.decrypt(row.payload, eventStoreKey)
                    try {
                        VaultMutableJsonCodec.decodeEvent(decryptedPayload)
                    } finally {
                        decryptedPayload.fill(0)
                    }
                }
            Result.success(events)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(IllegalStateException("Vault event storage operation failed"))
        } catch (e: SerializationException) {
            Result.failure(SerializationException("Vault event serialization failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Vault event operation failed"))
        } finally {
            key?.fill(0)
        }
    }
}
