package com.chimali.feature.vault.internal.payload

/** FR-VAULT-026: cleanup is idempotent; the owner must call it at every terminal boundary. */
sealed interface SensitivePayload {
    fun clearMemory()
}
