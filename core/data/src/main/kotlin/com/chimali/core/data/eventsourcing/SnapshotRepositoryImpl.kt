// Raw storage/codec exceptions are deliberately discarded after cancellation is rethrown.
@file:Suppress("SwallowedException", "TooGenericExceptionCaught")

package com.chimali.core.data.eventsourcing

import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
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
    override suspend fun <T> save(
        kind: EventKind,
        snapshot: Snapshot<T>,
    ): Result<Unit> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(Unit)

        var ownedKey: ByteArray? = null
        var ownedPlaintext: ByteArray? = null
        return try {
            val key = keyProvider.getEventStoreKey("chimali_vault_es_v1").also { ownedKey = it }

            database.transaction {
                @Suppress("UNCHECKED_CAST")
                val vaultSnapshot =
                    snapshot as? Snapshot<VaultState> ?: throw IllegalArgumentException("Unsupported Vault snapshot")
                val plaintext = VaultMutableJsonCodec.encodeSnapshot(vaultSnapshot).also { ownedPlaintext = it }
                val encryptedPayload = encryptionManager.encrypt(plaintext, key)

                database.vaultQueries.insert_snapshot(
                    aggregate_id = snapshot.aggregateId,
                    sequence_number = snapshot.sequenceNumber,
                    timestamp = snapshot.timestamp.toString(),
                    payload = encryptedPayload,
                )

                // Retain only the last 2 snapshots to save space
                database.vaultQueries.delete_old_snapshots(
                    aggregate_id = snapshot.aggregateId,
                    aggregate_id_ = snapshot.aggregateId,
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(IllegalStateException("Vault snapshot storage operation failed"))
        } catch (e: SerializationException) {
            Result.failure(SerializationException("Vault snapshot serialization failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Vault snapshot save failed"))
        } finally {
            ownedPlaintext?.fill(0)
            ownedKey?.fill(0)
        }
    }

    override suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?> {
        if (kind != EventKind.VAULT_ENTRY) return Result.success(null)

        var ownedKey: ByteArray? = null
        var ownedPlaintext: ByteArray? = null
        return try {
            val row = database.vaultQueries.get_latest_snapshot(aggregate_id = aggregateId).executeAsOneOrNull()
            if (row == null) {
                Result.success(null)
            } else {
                val key = keyProvider.getEventStoreKey("chimali_vault_es_v1").also { ownedKey = it }
                val decryptedPayload = encryptionManager.decrypt(row.payload, key).also { ownedPlaintext = it }

                @Suppress("UNCHECKED_CAST")
                val result = VaultMutableJsonCodec.decodeSnapshot(decryptedPayload) as Snapshot<T>
                Result.success(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: android.database.SQLException) {
            Result.failure(IllegalStateException("Vault snapshot storage operation failed"))
        } catch (e: SerializationException) {
            Result.failure(SerializationException("Vault snapshot serialization failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Vault snapshot load failed"))
        } finally {
            ownedPlaintext?.fill(0)
            ownedKey?.fill(0)
        }
    }
}
