package com.chimali.fido2.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import org.koin.core.annotation.Single

private const val PREFS_FILE_NAME = "fido2_settings"

/**
 * Persisted implementation of [Fido2SettingsRepository] backed by [EncryptedSharedPreferences].
 *
 * This ensures that critical authenticator limits are stored securely and persist
 * across application restarts.
 */
@Single
class Fido2SettingsRepositoryImpl(
    private val context: Context,
) : Fido2SettingsRepository {
    companion object {
        private const val KEY_MAX_CREDENTIALS = "max_credential_count"
        private const val DEFAULT_STORAGE_LIMIT = 1000
    }

    private val prefs by lazy {
        val masterKeyAlias =
            androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC,
            )
        // Query total credential count and fail with CTAP2_ERR_KEY_STORE_FULL (0x28)
        // if the device has reached capacity (default 1000, configurable).
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun getMaxCredentialCount(): Int = prefs.getInt(KEY_MAX_CREDENTIALS, DEFAULT_STORAGE_LIMIT)

    override suspend fun setMaxCredentialCount(count: Int) {
        prefs.edit(commit = true) {
            putInt(KEY_MAX_CREDENTIALS, count)
        }
    }
}
