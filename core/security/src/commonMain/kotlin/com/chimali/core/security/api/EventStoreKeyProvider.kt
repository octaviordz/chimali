package com.chimali.core.security.api

/**
 * Service for deriving storage-specific encryption keys from the master seed.
 *
 * This ensures that different storage domains (e.g. Vault Event Store, FIDO2 Event Store)
 * use distinct, cryptographically isolated keys, even though they all derive from the
 * same master seed.
 */
interface EventStoreKeyProvider {
    /**
     * Derives a 32-byte (256-bit) AES key for the given [aggregateLabel].
     *
     * @param aggregateLabel A unique label for the storage domain (e.g. "chimali_vault_es_v1").
     * @return An independently owned 32-byte key buffer. Ownership transfers to the caller;
     *   the provider retains no reference, and the caller must erase the returned buffer in
     *   `finally` after the cryptographic operation, including failure and cancellation paths.
     * @throws IllegalStateException if the master seed is not initialized.
     */
    suspend fun getEventStoreKey(aggregateLabel: String): ByteArray
}
