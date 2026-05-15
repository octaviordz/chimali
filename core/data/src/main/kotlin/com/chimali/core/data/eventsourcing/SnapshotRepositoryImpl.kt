package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * Implementation of SnapshotRepository for the VaultDatabase.
 * Handles serialization and encryption of Vault aggregate snapshots.
 */
@Single
class SnapshotRepositoryImpl(
    private val database: VaultDatabase,
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
            EventKind.VAULT_ENTRY -> VaultState.serializer() as KSerializer<T>
            else -> throw IllegalArgumentException("Unsupported aggregate kind: $kind")
        }

    override suspend fun <T> save(
        kind: EventKind,
        snapshot: Snapshot<T>,
    ): Result<Unit> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(Unit)

        return try {
            val key = keyProvider.getEventStoreKey("chimali_vault_es_v1")

            database.transaction {
                val stateSerializer = getSerializer<T>(kind)
                val snapshotSerializer = Snapshot.serializer(stateSerializer)
                val payloadJson = json.encodeToString(snapshotSerializer, snapshot)
                val encryptedPayload = encryptionManager.encrypt(payloadJson.encodeToByteArray(), key)

                database.vaultQueries.insertSnapshot(
                    aggregate_id = snapshot.aggregateId,
                    sequence_number = snapshot.sequenceNumber,
                    timestamp = snapshot.timestamp.toString(),
                    payload = encryptedPayload,
                )

                // Retain only the last 2 snapshots to save space
                database.vaultQueries.deleteOldSnapshots(snapshot.aggregateId, snapshot.aggregateId)
            }
            Result.success(Unit)
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }

    override suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(null)

        return try {
            val row = database.vaultQueries.getLatestSnapshot(aggregateId).executeAsOneOrNull()
            if (row == null) {
                Result.success(null)
            } else {
                val key = keyProvider.getEventStoreKey("chimali_vault_es_v1")
                val decryptedPayload = encryptionManager.decrypt(row.payload, key)
                val stateSerializer = getSerializer<T>(kind)
                val snapshotSerializer = Snapshot.serializer(stateSerializer)

                val result = json.decodeFromString(snapshotSerializer, decryptedPayload.decodeToString())
                Result.success(result)
            }
        } catch (e: android.database.SQLException) {
            Result.failure(e)
        }
    }
}
