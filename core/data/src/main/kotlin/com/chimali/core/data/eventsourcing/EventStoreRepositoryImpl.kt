package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.core.annotation.Single

internal object VaultEventSerializer {
    val json =
        Json {
            classDiscriminator = "eventType"
            serializersModule =
                SerializersModule {
                    polymorphic(DomainEvent::class) {
                        subclass(VaultEvent.Created::class)
                        subclass(VaultEvent.Updated::class)
                        subclass(VaultEvent.Deleted::class)
                    }
                }
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
    private val json = VaultEventSerializer.json

    override suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
    ): Result<Unit> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(Unit)

        var key: ByteArray? = null
        return try {
            key = keyProvider.getEventStoreKey("chimali_vault_es_v1")

            database.transaction {
                events.forEach { event ->
                    val payloadJson = json.encodeToString(event)
                    val plaintext = payloadJson.encodeToByteArray()
                    try {
                        val encryptedPayload = encryptionManager.encrypt(plaintext, key!!)
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
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
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

            key = keyProvider.getEventStoreKey("chimali_vault_es_v1")

            val events =
                database.vaultQueries
                    .get_events(
                        aggregate_id = aggregateId,
                        timestamp = timestampLimit,
                    ).executeAsList()
                    .map { row ->
                        val decryptedPayload = encryptionManager.decrypt(row.payload, key!!)
                        try {
                            json.decodeFromString<DomainEvent>(decryptedPayload.decodeToString())
                        } finally {
                            decryptedPayload.fill(0)
                        }
                    }
            Result.success(events)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
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
            key = keyProvider.getEventStoreKey("chimali_vault_es_v1")

            val events =
                database.vaultQueries
                    .get_events_from(
                        aggregate_id = aggregateId,
                        sequence_number = fromSequence,
                    ).executeAsList()
                    .map { row ->
                        val decryptedPayload = encryptionManager.decrypt(row.payload, key!!)
                        try {
                            json.decodeFromString<DomainEvent>(decryptedPayload.decodeToString())
                        } finally {
                            decryptedPayload.fill(0)
                        }
                    }
            Result.success(events)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
        } finally {
            key?.fill(0)
        }
    }
}
