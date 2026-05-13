package com.chimali.fido2.data.eventsourcing

import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.fido2.data.database.Fido2Database
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Implementation of EventStoreRepository for the Fido2Database.
 * Handles serialization and encryption of Passkey aggregate events.
 */
@Suppress("ForbiddenComment")
class PasskeyEventStoreRepositoryImpl(
    private val database: Fido2Database,
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
                        subclass(PasskeyEvent.Registered::class)
                        subclass(PasskeyEvent.Authenticated::class)
                        subclass(PasskeyEvent.Deleted::class)
                    }
                }
        }

    override suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
    ): Result<Unit> =
        runCatching {
            if (kind != EventKind.PASSKEY) return@runCatching

            database.transaction {
                events.forEach { event ->
                    val payloadJson = json.encodeToString(event)
                    val encryptedPayload = encryptionManager.encrypt(payloadJson.encodeToByteArray(), dummyKey)

                    database.fido2DatabaseQueries.insertEvent(
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
            if (kind != EventKind.PASSKEY) return@runCatching emptyList()

            val timestampLimit = upTo?.toString() ?: "9999-12-31T23:59:59Z"

            database.fido2DatabaseQueries.getEvents(aggregateId, timestampLimit).executeAsList().map { row ->
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
            if (kind != EventKind.PASSKEY) return@runCatching emptyList()

            database.fido2DatabaseQueries.getEventsFrom(aggregateId, fromSequence).executeAsList().map { row ->
                val decryptedPayload = encryptionManager.decrypt(row.payload, dummyKey)
                json.decodeFromString<DomainEvent>(decryptedPayload.decodeToString())
            }
        }
}
