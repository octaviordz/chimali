package com.chimali.feature.vault.internal

import com.chimali.core.database.ChimaliDatabase
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class VaultRepositoryImpl(
    private val database: ChimaliDatabase
) : VaultService {

    override suspend fun getItems(labelId: UUID?): List<VaultItem> = withContext(Dispatchers.IO) {
        val entries = if (labelId == null) {
            database.vaultQueries.getVaultEntries().executeAsList()
        } else {
            database.vaultQueries.getVaultEntriesByLabel(labelId.toString()).executeAsList()
        }

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
                identityId = UUID.fromString(entry.identity_id)
            )
        }
    }

    override suspend fun saveItem(item: VaultItem) = withContext(Dispatchers.IO) {
        // TODO: Integrate actual Android Keystore encryption for payload, Uniffi bridge for CRDT merge state logic mapping
        
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
            identity_id = item.identityId.toString()
        )
    }

    override suspend fun deleteItem(id: UUID) = withContext(Dispatchers.IO) {
        database.vaultQueries.deleteVaultEntry(id.toString())
    }
}
