package com.chimali.feature.vault.internal

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.domain.eventsourcing.vault.clearSensitiveMemory
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
@Suppress("TooGenericExceptionCaught")
class VaultRepositoryImpl(
    private val database: VaultDatabase,
    private val aggregateService: AggregateService<VaultCommand, VaultState>,
) : VaultService {
    override suspend fun getItems(labelId: UUID?): Outcome<List<VaultItem>, DomainError> {
        var stagedItems: List<VaultItem>? = null
        try {
            val result =
                withContext(Dispatchers.IO) {
                    var items: MutableList<VaultItem>? = null
                    var transferToCaller = false
                    try {
                        val entries =
                            if (labelId == null) {
                                database.vaultQueries.get_vault_entries().executeAsList()
                            } else {
                                database.vaultQueries.get_vault_entries_by_label(labelId.toString()).executeAsList()
                            }
                        items = ArrayList(entries.size)
                        try {
                            entries.forEach { entry ->
                                var title: CharArray? = null
                                var payload: ByteArray? = null
                                var crdtState: ByteArray? = null
                                var item: VaultItem? = null
                                try {
                                    val id = UUID.fromString(entry.id)
                                    val type = VaultType.valueOf(entry.type)
                                    val identityId = UUID.fromString(entry.identity_id)
                                    val ownedTitle = decodeTitle(entry.title)
                                    title = ownedTitle
                                    val ownedPayload = entry.encrypted_payload.copyOf()
                                    payload = ownedPayload
                                    val ownedCrdtState = entry.crdt_state.copyOf()
                                    crdtState = ownedCrdtState
                                    item =
                                        VaultItem(
                                            id = id,
                                            type = type,
                                            title = ownedTitle,
                                            payload = ownedPayload,
                                            crdtState = ownedCrdtState,
                                            dateCreated = entry.date_created,
                                            dateModified = entry.date_modified,
                                            lastBackedUpAt = entry.last_backed_up_at,
                                            identityId = identityId,
                                        )
                                    title = null
                                    payload = null
                                    crdtState = null
                                    items.add(item)
                                    item = null
                                } finally {
                                    title?.fill('\u0000')
                                    payload?.fill(0)
                                    crdtState?.fill(0)
                                    item?.clearMemory()
                                }
                            }
                            stagedItems = items
                            transferToCaller = true
                            Outcome.Success(items)
                        } finally {
                            entries.forEach { entry ->
                                entry.title.fill(0)
                                entry.encrypted_payload.fill(0)
                                entry.crdt_state.fill(0)
                            }
                        }
                    } catch (e: android.database.SQLException) {
                        Logger.e { "VaultRepositoryImpl: Failed to load vault items" }
                        Outcome.Error(DomainError.DatabaseError("Failed to load vault items"))
                    } catch (e: IllegalArgumentException) {
                        Logger.e { "VaultRepositoryImpl: Invalid data in vault entries" }
                        Outcome.Error(DomainError.StorageError("Invalid vault data"))
                    } finally {
                        if (!transferToCaller) items?.forEach(VaultItem::clearMemory)
                    }
                }
            stagedItems = null // Ownership transfers only after withContext returns to its caller.
            return result
        } finally {
            stagedItems?.forEach(VaultItem::clearMemory)
        }
    }

    private fun decodeTitle(source: ByteArray): CharArray {
        val decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(source))
        return try {
            CharArray(decoded.remaining()).also(decoded::get)
        } finally {
            decoded.array().fill('\u0000')
        }
    }

    private fun encodeTitle(source: CharArray): ByteArray {
        val encoded =
            StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .encode(CharBuffer.wrap(source))
        return try {
            ByteArray(encoded.remaining()).also(encoded::get)
        } finally {
            encoded.clear()
            while (encoded.hasRemaining()) encoded.put(0)
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
                            title = item.title.copyOf(),
                            payload = item.payload,
                            identityId = item.identityId.toString(),
                        )
                    } else {
                        VaultCommand.Update(
                            id = item.id.toString(),
                            title = item.title.copyOf(),
                            payload = item.payload,
                        )
                    }

                try {
                    val result = aggregateService.execute(item.id.toString(), command)

                    if (result.isSuccess) {
                        val state = result.getOrThrow()
                        try {
                            var titleBytes: ByteArray? = null
                            try {
                                titleBytes = encodeTitle(state.title)
                                database.vaultQueries.insert_vault_entry(
                                    id = state.id,
                                    doc_id = state.id,
                                    type = state.type,
                                    title = titleBytes,
                                    encrypted_payload = state.payload,
                                    crdt_state = item.crdtState,
                                    date_created = item.dateCreated,
                                    date_modified = item.dateModified,
                                    last_backed_up_at = item.lastBackedUpAt,
                                    identity_id = state.identityId.ifEmpty { item.identityId.toString() },
                                )
                            } finally {
                                titleBytes?.fill(0)
                            }
                        } finally {
                            state.clearSensitiveMemory()
                        }
                        Outcome.Success(Unit)
                    } else {
                        Logger.e { "VaultRepositoryImpl: Aggregate execution failed while saving vault item" }
                        Outcome.Error(DomainError.DatabaseError("Failed to save vault item"))
                    }
                } finally {
                    command.clearSensitiveMemory()
                }
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Database failure during save" }
                Outcome.Error(DomainError.DatabaseError("Database error during save"))
            } catch (e: IllegalStateException) {
                Logger.e { "VaultRepositoryImpl: State failure during save" }
                Outcome.Error(DomainError.StorageError("Invalid state during save"))
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
                    Logger.e { "VaultRepositoryImpl: Aggregate delete failed while deleting vault item" }
                    Outcome.Error(DomainError.DatabaseError("Failed to delete vault item"))
                }
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Database failure during delete" }
                Outcome.Error(DomainError.DatabaseError("Database error during delete"))
            } catch (e: IllegalStateException) {
                Logger.e { "VaultRepositoryImpl: State failure during delete" }
                Outcome.Error(DomainError.StorageError("Invalid state during delete"))
            }
        }

    override suspend fun setItemLabels(
        itemId: UUID,
        labelIds: List<UUID>,
    ): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                database.transaction {
                    database.vaultQueries.delete_vault_entry_labels(itemId.toString())
                    labelIds.forEach { labelId ->
                        database.vaultQueries.insert_vault_entry_label(itemId.toString(), labelId.toString())
                    }
                }
                Outcome.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Failed to set item labels" }
                Outcome.Error(DomainError.DatabaseError("Failed to save item labels"))
            }
        }

    @Suppress("SwallowedException") // Return stable storage categories without exposing provider details.
    override suspend fun getItemLabelIds(itemId: UUID): Outcome<List<UUID>, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                Outcome.Success(
                    database.vaultQueries.get_labels_for_entry(itemId.toString()).executeAsList().map {
                        UUID.fromString(it.id)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: android.database.SQLException) {
                Outcome.Error(DomainError.DatabaseError("Failed to load item labels"))
            } catch (e: IllegalArgumentException) {
                Outcome.Error(DomainError.StorageError("Invalid item label data"))
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Failed to get labels" }
                Outcome.Error(DomainError.DatabaseError("Failed to get labels"))
            } catch (e: IllegalArgumentException) {
                Logger.e { "VaultRepositoryImpl: Invalid label data" }
                Outcome.Error(DomainError.StorageError("Invalid label data"))
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Failed to create label" }
                Outcome.Error(DomainError.DatabaseError("Failed to create label"))
            }
        }

    override suspend fun deleteLabel(id: UUID): Outcome<Unit, DomainError> =
        withContext(Dispatchers.IO) {
            try {
                database.vaultQueries.delete_label(id.toString())
                Outcome.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: android.database.SQLException) {
                Logger.e { "VaultRepositoryImpl: Failed to delete label" }
                Outcome.Error(DomainError.DatabaseError("Failed to delete label"))
            }
        }
}
