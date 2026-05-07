package com.chimali.feature.vault.internal

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.database.ChimaliDatabase
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class VaultRepositoryImpl(
    private val database: ChimaliDatabase,
) : VaultService {
    override suspend fun getItems(labelId: UUID?): Outcome<List<VaultItem>, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val entries =
                    if (labelId == null) {
                        database.vaultQueries.getVaultEntries().executeAsList()
                    } else {
                        database.vaultQueries.getVaultEntriesByLabel(labelId.toString()).executeAsList()
                    }

                val items =
                    entries.map { entry ->
                        VaultItem(
                            id = UUID.fromString(entry.id),
                            type = VaultType.valueOf(entry.type),
                            title = entry.title,
                            payload = entry.encrypted_payload,
                            crdtState = entry.crdt_state,
                            dateCreated = entry.date_created,
                            dateModified = entry.date_modified,
                            lastBackedUpAt = entry.last_backed_up_at,
                            identityId = UUID.fromString(entry.identity_id),
                        )
                    }
                Outcome.Success(items)
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to load vault items" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to load vault items", e))
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "VaultRepositoryImpl: Invalid data in vault entries" }
                Outcome.Error(DomainError.StorageError(e.message ?: "Invalid vault data", e))
            }
        }

    @Suppress("ForbiddenComment")
    override suspend fun saveItem(item: VaultItem): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                // TODO: Integrate actual Android Keystore encryption for payload,
                // Uniffi bridge for CRDT merge state logic mapping
                database.vaultQueries.insertVaultEntry(
                    id = item.id.toString(),
                    doc_id = item.id.toString(), // Using item ID as doc_id for now
                    type = item.type.name,
                    title = item.title,
                    encrypted_payload = item.payload,
                    crdt_state = item.crdtState,
                    date_created = item.dateCreated,
                    date_modified = item.dateModified,
                    last_backed_up_at = item.lastBackedUpAt,
                    identity_id = item.identityId.toString(),
                )
                Outcome.Success(Unit)
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to save vault item id=${item.id}" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to save vault item", e))
            }
        }

    override suspend fun deleteItem(id: UUID): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                database.vaultQueries.deleteVaultEntry(id.toString())
                Outcome.Success(Unit)
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to delete vault item id=$id" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to delete vault item", e))
            }
        }
}
