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
                        database.vaultQueries.get_vault_entries().executeAsList()
                    } else {
                        database.vaultQueries.get_vault_entries_by_label(labelId.toString()).executeAsList()
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
                val exists =
                    database.vaultQueries.get_vault_entry_by_id(item.id.toString()).executeAsOneOrNull() != null

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
                    database.vaultQueries.insert_vault_entry(
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
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "VaultRepositoryImpl: Database failure during save id=${item.id}" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Database error during save", e))
            } catch (e: IllegalStateException) {
                Logger.e(e) { "VaultRepositoryImpl: State failure during save id=${item.id}" }
                Outcome.Error(DomainError.StorageError(e.message ?: "Invalid state during save", e))
            }
        }

    override suspend fun deleteItem(id: UUID): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val result = aggregateService.execute(id.toString(), VaultCommand.Delete(id.toString()))
                if (result.isSuccess) {
                    database.vaultQueries.delete_vault_entry(id.toString())
                    Outcome.Success(Unit)
                } else {
                    val error = result.exceptionOrNull()
                    Logger.e(error) { "VaultRepositoryImpl: Aggregate delete failed for id=$id" }
                    Outcome.Error(DomainError.DatabaseError(error?.message ?: "Failed to delete vault item"))
                }
            } catch (e: android.database.SQLException) {
                Logger.e(e) { "VaultRepositoryImpl: Database failure during delete id=$id" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Database error during delete", e))
            } catch (e: IllegalStateException) {
                Logger.e(e) { "VaultRepositoryImpl: State failure during delete id=$id" }
                Outcome.Error(DomainError.StorageError(e.message ?: "Invalid state during delete", e))
            }
        }

    override suspend fun getLabels(): Outcome<List<com.chimali.feature.vault.ui.model.LabelUiModel>, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val labels =
                    database.vaultQueries.get_labels().executeAsList().map {
                        com.chimali.feature.vault.ui.model.LabelUiModel(
                            id = UUID.fromString(it.id),
                            name = it.name,
                            colorHex = it.color_hex,
                        )
                    }
                Outcome.Success(labels)
            } catch (e: Exception) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to get labels" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to get labels", e))
            }
        }

    override suspend fun createLabel(
        name: String,
        colorHex: String,
    ): Outcome<com.chimali.feature.vault.ui.model.LabelUiModel, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                val id = UUID.randomUUID()
                database.vaultQueries.insert_label(id.toString(), colorHex, name)
                Outcome.Success(
                    com.chimali.feature.vault.ui.model
                        .LabelUiModel(id, name, colorHex),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to create label" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to create label", e))
            }
        }

    override suspend fun deleteLabel(id: UUID): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                database.vaultQueries.delete_label(id.toString())
                Outcome.Success(Unit)
            } catch (e: Exception) {
                Logger.e(e) { "VaultRepositoryImpl: Failed to delete label" }
                Outcome.Error(DomainError.DatabaseError(e.message ?: "Failed to delete label", e))
            }
        }
}
