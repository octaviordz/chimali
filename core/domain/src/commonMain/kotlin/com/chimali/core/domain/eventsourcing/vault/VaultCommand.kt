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
        val title: CharArray,
        val payload: ByteArray,
        val identityId: String,
    ) : VaultCommand {
        constructor(
            id: String,
            type: String,
            title: String,
            payload: ByteArray,
            identityId: String,
        ) : this(id, type, title.toCharArray(), payload, identityId)
    }

    /**
     * Request to update an existing vault entry.
     */
    data class Update(
        val id: String,
        val title: CharArray? = null,
        val payload: ByteArray? = null,
    ) : VaultCommand {
        constructor(
            id: String,
            title: String,
            payload: ByteArray?,
        ) : this(id, title.toCharArray(), payload)
    }

    /**
     * Request to delete a vault entry.
     */
    data class Delete(
        val id: String,
    ) : VaultCommand
}

/** Clears command-owned sensitive text once command processing has finished. */
fun VaultCommand.clearSensitiveMemory() {
    when (this) {
        is VaultCommand.Create -> title.fill('\u0000')
        is VaultCommand.Update -> title?.fill('\u0000')
        is VaultCommand.Delete -> Unit
    }
}
