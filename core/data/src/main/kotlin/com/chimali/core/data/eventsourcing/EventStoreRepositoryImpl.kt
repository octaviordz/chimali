package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.security.api.EncryptionManager
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.core.annotation.Single

/**
 * Implementation of EventStoreRepository for the VaultDatabase.
 * Handles serialization and encryption of Vault aggregate events.
 */
@Single
@Suppress("ForbiddenComment")
class EventStoreRepositoryImpl(
    private val database: VaultDatabase,
    private val encryptionManager: EncryptionManager,
) : EventStoreRepository {
    companion object {
        private const val KEY_SIZE = 32
    }

    // TODO: Replace with real key derivation from MasterSeedProvider (T032)
    private val dummyKey = ByteArray(KEY_SIZE) { 0 }

    private val json =
        Json {
            serializersModule =
                SerializersModule {
                    polymorphic(DomainEvent::class) {
                        subclass(VaultEvent.Created::class)
                        subclass(VaultEvent.Updated::class)
                        subclass(VaultEvent.Deleted::class)
                    }
                }
        }

    override suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
    ): Result<Unit> =
        runCatching {
            if (kind != EventKind.VAULT_ENTRY) return@runCatching // This implementation only handles Vault events

            database.transaction {
                events.forEach { event ->
                    val payloadJson = json.encodeToString(event)
                    val encryptedPayload = encryptionManager.encrypt(payloadJson.encodeToByteArray(), dummyKey)

                    database.vaultQueries.insertEvent(
                        aggregate_id = event.aggregateId,
                        sequence_number = event.sequenceNumber,
                        timestamp = event.timestamp.toString(),
                        payload = encryptedPayload,
                    )
                }
            }
        }

    override suspend fun getEvents(
        kind: EventKind,
        aggregateId: String,
        upTo: Instant?,
    ): Result<List<DomainEvent>> =
        runCatching {
            if (kind != EventKind.VAULT_ENTRY) return@runCatching emptyList()

            // Using a very large timestamp string if upTo is null to fetch all events
            val timestampLimit = upTo?.toString() ?: "9999-12-31T23:59:59Z"

            database.vaultQueries.getEvents(aggregateId, timestampLimit).executeAsList().map { row ->
                val decryptedPayload = encryptionManager.decrypt(row.payload, dummyKey)
                json.decodeFromString<DomainEvent>(decryptedPayload.decodeToString())
            }
        }

    override suspend fun getEventsFrom(
        kind: EventKind,
        aggregateId: String,
        fromSequence: Long,
    ): Result<List<DomainEvent>> =
        runCatching {
            if (kind != EventKind.VAULT_ENTRY) return@runCatching emptyList()

            database.vaultQueries.getEventsFrom(aggregateId, fromSequence).executeAsList().map { row ->
                val decryptedPayload = encryptionManager.decrypt(row.payload, dummyKey)
                json.decodeFromString<DomainEvent>(decryptedPayload.decodeToString())
            }
        }
}
