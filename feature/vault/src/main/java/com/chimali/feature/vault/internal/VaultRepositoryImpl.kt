package com.chimali.feature.vault.internal

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
@Suppress("TooGenericExceptionCaught")
class VaultRepositoryImpl(
    private val database: VaultDatabase,
    private val aggregateService: AggregateService<VaultCommand, VaultState>,
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

    override suspend fun saveItem(item: VaultItem): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val exists = database.vaultQueries.getVaultEntryById(item.id.toString()).executeAsOneOrNull() != null

                val command =
                    if (!exists) {
                        VaultCommand.Create(
                            id = item.id.toString(),
                            type = item.type.name,
                            title = item.title,
                            payload = item.payload,
                            identityId = item.identityId.toString(),
                        )
                    } else {
                        VaultCommand.Update(
                            id = item.id.toString(),
                            title = item.title,
                            payload = item.payload,
                        )
                    }

                val result = aggregateService.execute(item.id.toString(), command)

                if (result.isSuccess) {
                    val state = result.getOrThrow()
                    // Projection: Update the read model table
                    database.vaultQueries.insertVaultEntry(
                        id = state.id,
                        doc_id = state.id,
                        type = state.type,
                        title = state.title,
                        encrypted_payload = state.payload,
                        crdt_state = item.crdtState,
                        date_created = item.dateCreated,
                        date_modified = item.dateModified,
                        last_backed_up_at = item.lastBackedUpAt,
                        identity_id = state.identityId.ifEmpty { item.identityId.toString() },
                    )
                    Outcome.Success(Unit)
                } else {
                    val error = result.exceptionOrNull()
                    Logger.e(error) { "VaultRepositoryImpl: Aggregate execution failed for id=${item.id}" }
                    Outcome.Error(DomainError.DatabaseError(error?.message ?: "Failed to save vault item"))
                }
            } catch (e: Exception) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to save vault item id=${item.id}" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to save vault item", e))
            }
        }

    override suspend fun deleteItem(id: UUID): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val result = aggregateService.execute(id.toString(), VaultCommand.Delete(id.toString()))
                if (result.isSuccess) {
                    database.vaultQueries.deleteVaultEntry(id.toString())
                    Outcome.Success(Unit)
                } else {
                    val error = result.exceptionOrNull()
                    Logger.e(error) { "VaultRepositoryImpl: Aggregate delete failed for id=$id" }
                    Outcome.Error(DomainError.DatabaseError(error?.message ?: "Failed to delete vault item"))
                }
            } catch (e: Exception) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to delete vault item id=$id" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to delete vault item", e))
            }
        }
}
