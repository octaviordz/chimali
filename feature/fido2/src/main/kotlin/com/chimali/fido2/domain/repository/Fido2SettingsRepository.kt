package com.chimali.fido2.domain.repository

/**
 * Repository for managing FIDO2 authenticator configuration and settings.
 *
 * This repository abstracts the storage of persistent settings that define the
 * authenticator's behavior and limits.
 */
interface Fido2SettingsRepository {

    /**
     * Returns the maximum number of passkey credentials the authenticator will store.
     *
     * This limit is configurable and defaults to 1000 per FR-HID-022.
     */
    suspend fun getMaxCredentialCount(): Int

    /**
     * Updates the maximum credential count storage limit.
     */
    suspend fun setMaxCredentialCount(count: Int)
}
