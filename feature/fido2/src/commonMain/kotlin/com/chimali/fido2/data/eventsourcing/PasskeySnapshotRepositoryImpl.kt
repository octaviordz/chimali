package com.chimali.fido2.data.eventsourcing

import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Implementation of SnapshotRepository for the Fido2Database.
 * Handles serialization and encryption of Passkey aggregate snapshots.
 */
@Suppress("ForbiddenComment")
class PasskeySnapshotRepositoryImpl(
    private val database: Fido2Database,
    private val encryptionManager: EncryptionManager,
    private val keyProvider: EventStoreKeyProvider,
) : SnapshotRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getSerializer(kind: EventKind): KSerializer<T> =
        when (kind) {
            EventKind.PASSKEY -> PasskeyState.serializer() as KSerializer<T>
            else -> throw IllegalArgumentException("Unsupported aggregate kind: $kind")
        }

    override suspend fun <T> save(
        kind: EventKind,
        snapshot: Snapshot<T>,
    ): Result<Unit> =
        runCatching {
            if (kind != EventKind.PASSKEY) return@runCatching

            val key = keyProvider.getEventStoreKey("chimali_fido2_es_v1")

            database.transaction {
                val stateSerializer = getSerializer<T>(kind)
                val snapshotSerializer = Snapshot.serializer(stateSerializer)
                val payloadJson = json.encodeToString(snapshotSerializer, snapshot)
                val encryptedPayload = encryptionManager.encrypt(payloadJson.encodeToByteArray(), key)

                database.fido2DatabaseQueries.insertSnapshot(
                    aggregate_id = snapshot.aggregateId,
                    sequence_number = snapshot.sequenceNumber,
                    timestamp = snapshot.timestamp.toString(),
                    payload = encryptedPayload,
                )

                // Retain only the last 2 snapshots
                database.fido2DatabaseQueries.deleteOldSnapshots(snapshot.aggregateId, snapshot.aggregateId)
            }
        }

    override suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?> =
        runCatching {
            if (kind != EventKind.PASSKEY) return@runCatching null

            val row = database.fido2DatabaseQueries.getLatestSnapshot(aggregateId).executeAsOneOrNull()
            if (row == null) return@runCatching null

            val key = keyProvider.getEventStoreKey("chimali_fido2_es_v1")
            val decryptedPayload = encryptionManager.decrypt(row.payload, key)
            val stateSerializer = getSerializer<T>(kind)
            val snapshotSerializer = Snapshot.serializer(stateSerializer)

            json.decodeFromString(snapshotSerializer, decryptedPayload.decodeToString())
        }
}
