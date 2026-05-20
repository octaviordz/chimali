package com.chimali.fido2.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.security.crypto.EncryptedSharedPreferences
import co.touchlab.kermit.Logger
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

private const val PREFS_FILE_NAME = "fido2_settings"

/**
 * Persisted implementation of [Fido2SettingsRepository] backed by Proto DataStore.
 *
 * This ensures that critical authenticator limits are stored securely and persist
 * across application restarts.
 *
 * Migration: On first launch after upgrade, max credential count stored in legacy
 * EncryptedSharedPreferences is automatically migrated to Proto DataStore.
 */
@Single
class Fido2SettingsRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<UserPreferences>,
) : Fido2SettingsRepository {
    companion object {
        private const val KEY_MAX_CREDENTIALS = "max_credential_count"
        private const val DEFAULT_STORAGE_LIMIT = 1000
    }

    override suspend fun getMaxCredentialCount(): Int {
        val prefs = dataStore.data.first()

        // 1. If already migrated or max_credential_count has been set, return it
        // We use maxCredentialCount > 0 as a sign it's set, or if migration is completed.
        // But since maxCredentialCount defaults to 0 in protobuf, we need to check if it's not 0 or migration is done.
        // Actually, if it's 0 and migration is done, it could legitimately be 0 (though default is 1000).
        // Let's check if migration has completed (even if from another repository like WalletMasterSeedProvider).
        // Wait, UserPreferences is shared. If WalletMasterSeedProvider ran first, migration_completed is true!
        // This is a subtle bug: if Wallet runs first, migrationCompleted=true, but Fido2Settings hasn't migrated!
        // To fix this, we should check if FIDO2 settings were migrated.
        // We can check if legacy preferences exist instead.

        try {
            val legacyPrefs = openEncryptedPrefs()
            if (legacyPrefs.contains(KEY_MAX_CREDENTIALS)) {
                val legacyCount = legacyPrefs.getInt(KEY_MAX_CREDENTIALS, DEFAULT_STORAGE_LIMIT)
                Logger.i { "Migrating max_credential_count from legacy EncryptedSharedPreferences to Proto DataStore" }

                dataStore.updateData { currentPrefs ->
                    currentPrefs.copy(maxCredentialCount = legacyCount)
                }

                legacyPrefs.edit(commit = true) {
                    remove(KEY_MAX_CREDENTIALS)
                }
                Logger.i { "Migration of max_credential_count completed successfully, legacy key cleared" }

                return legacyCount
            }
        } catch (e: java.security.GeneralSecurityException) {
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        } catch (e: java.io.IOException) {
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        } catch (e: SecurityException) {
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        }

        // If not in legacy or already migrated, check DataStore
        // The default in Proto is 0, but our application default is 1000
        val count = prefs.maxCredentialCount
        return if (count <= 0) {
            // Write default to DataStore if it's 0 so it's consistent
            if (prefs.maxCredentialCount == 0) {
                dataStore.updateData { it.copy(maxCredentialCount = DEFAULT_STORAGE_LIMIT) }
            }
            DEFAULT_STORAGE_LIMIT
        } else {
            count
        }
    }

    override suspend fun setMaxCredentialCount(count: Int) {
        dataStore.updateData { prefs ->
            prefs.copy(maxCredentialCount = count)
        }
    }

    private fun openEncryptedPrefs(): android.content.SharedPreferences {
        val masterKeyAlias =
            androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC,
            )
        return EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
