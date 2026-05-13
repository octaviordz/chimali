package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.api.EncryptionManager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * Implementation of SnapshotRepository for the VaultDatabase.
 * Handles serialization and encryption of Vault aggregate snapshots.
 */
@Single
@Suppress("ForbiddenComment")
class SnapshotRepositoryImpl(
    private val database: VaultDatabase,
    private val encryptionManager: EncryptionManager,
) : SnapshotRepository {
    companion object {
        private const val KEY_SIZE = 32
    }

    // TODO: Replace with real key derivation from MasterSeedProvider (T032)
    private val dummyKey = ByteArray(KEY_SIZE) { 0 }

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
    ): Result<Unit> =
        runCatching {
            if (kind != EventKind.VAULT_ENTRY) return@runCatching

            database.transaction {
                val stateSerializer = getSerializer<T>(kind)
                val snapshotSerializer = Snapshot.serializer(stateSerializer)
                val payloadJson = json.encodeToString(snapshotSerializer, snapshot)
                val encryptedPayload = encryptionManager.encrypt(payloadJson.encodeToByteArray(), dummyKey)

                database.vaultQueries.insertSnapshot(
                    aggregate_id = snapshot.aggregateId,
                    sequence_number = snapshot.sequenceNumber,
                    timestamp = snapshot.timestamp.toString(),
                    payload = encryptedPayload,
                )

                // Retain only the last 2 snapshots to save space
                database.vaultQueries.deleteOldSnapshots(snapshot.aggregateId, snapshot.aggregateId)
            }
        }

    override suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?> =
        runCatching {
            if (kind != EventKind.VAULT_ENTRY) return@runCatching null

            val row = database.vaultQueries.getLatestSnapshot(aggregateId).executeAsOneOrNull()
            if (row == null) return@runCatching null

            val decryptedPayload = encryptionManager.decrypt(row.payload, dummyKey)
            val stateSerializer = getSerializer<T>(kind)
            val snapshotSerializer = Snapshot.serializer(stateSerializer)

            json.decodeFromString(snapshotSerializer, decryptedPayload.decodeToString())
        }
}
