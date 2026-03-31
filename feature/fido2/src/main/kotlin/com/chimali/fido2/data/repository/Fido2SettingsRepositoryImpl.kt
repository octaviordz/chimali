package com.chimali.fido2.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "fido2_settings"
private const val KEY_MAX_CREDENTIALS = "max_credential_count"
private const val DEFAULT_MAX_CREDENTIALS = 1000

/**
 * Persisted implementation of [Fido2SettingsRepository] backed by [EncryptedSharedPreferences].
 *
 * This ensures that critical authenticator limits are stored securely and persist
 * across application restarts.
 */
@Singleton
class Fido2SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : Fido2SettingsRepository {

    private val prefs by lazy {
        val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
            androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
        )
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun getMaxCredentialCount(): Int {
        return prefs.getInt(KEY_MAX_CREDENTIALS, DEFAULT_MAX_CREDENTIALS)
    }

    override suspend fun setMaxCredentialCount(count: Int) {
        prefs.edit(commit = true) {
            putInt(KEY_MAX_CREDENTIALS, count)
        }
    }
}
