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
     * @return 32 bytes of key material.
     * @throws IllegalStateException if the master seed is not initialized.
     */
    suspend fun getEventStoreKey(aggregateLabel: String): ByteArray
}
