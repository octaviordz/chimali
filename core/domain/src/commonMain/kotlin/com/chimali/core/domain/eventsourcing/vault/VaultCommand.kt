package com.chimali.core.domain.eventsourcing.vault

/**
 * Commands for the VaultEntry aggregate.
 */
sealed interface VaultCommand {
    /**
     * Request to create a new vault entry.
     */
    data class Create(
        val id: String,
        val type: String,
        val title: String,
        val payload: ByteArray,
        val identityId: String,
    ) : VaultCommand

    /**
     * Request to update an existing vault entry.
     */
    data class Update(
        val id: String,
        val title: String? = null,
        val payload: ByteArray? = null,
    ) : VaultCommand

    /**
     * Request to delete a vault entry.
     */
    data class Delete(
        val id: String,
    ) : VaultCommand
}
