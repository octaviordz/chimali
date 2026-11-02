package com.chimali.fido2.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.security.crypto.EncryptedSharedPreferences
import co.touchlab.kermit.Logger
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.util.performance.PerformanceTrace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val legacyPrefsMutex = Mutex()
    private val legacyMigrationMutex = Mutex()

    @Volatile
    private var cachedLegacyPrefs: SharedPreferences? = null

    companion object {
        private const val KEY_MAX_CREDENTIALS = "max_credential_count"
        private const val DEFAULT_STORAGE_LIMIT = 1000
        private const val DATA_STORE_FIRST_SECTION = "Fido2.SettingsLookup.DataStoreFirst"
        private const val LEGACY_PREFS_OPEN_SECTION = "Fido2.SettingsLookup.LegacyPrefsOpen"
        private const val LEGACY_PREFS_CACHE_SECTION = "Fido2.SettingsLookup.LegacyPrefsCache"
        private const val DATA_STORE_REFRESH_SECTION = "Fido2.SettingsLookup.DataStoreRefresh"
        private const val LEGACY_MASTER_KEY_SECTION = "Fido2.SettingsLookup.LegacyMasterKey"
        private const val LEGACY_PREFS_CREATE_SECTION = "Fido2.SettingsLookup.LegacyPrefsCreate"
        private const val LEGACY_CONTAINS_SECTION = "Fido2.SettingsLookup.LegacyContains"
        private const val LEGACY_READ_SECTION = "Fido2.SettingsLookup.LegacyRead"
        private const val MIGRATION_WRITE_SECTION = "Fido2.SettingsLookup.MigrationWrite"
        private const val LEGACY_REMOVAL_SECTION = "Fido2.SettingsLookup.LegacyRemoval"
        private const val DATA_STORE_VALUE_SECTION = "Fido2.SettingsLookup.DataStoreValue"
        private const val DEFAULT_WRITE_SECTION = "Fido2.SettingsLookup.DefaultWrite"
    }

    override suspend fun getMaxCredentialCount(): Int {
        val prefs =
            PerformanceTrace.section(DATA_STORE_FIRST_SECTION) {
                dataStore.data.first()
            }

        // 1. If already migrated or max_credential_count has been set, return it
        // We use maxCredentialCount > 0 as a sign it's set, or if migration is completed.
        // But since maxCredentialCount defaults to 0 in protobuf, we need to check if it's not 0 or migration is done.
        // Actually, if it's 0 and migration is done, it could legitimately be 0 (though default is 1000).
        // Let's check if migration has completed (even if from another repository like WalletMasterSeedProvider).
        // Wait, UserPreferences is shared. If WalletMasterSeedProvider ran first, migration_completed is true!
        // This is a subtle bug: if Wallet runs first, migrationCompleted=true, but Fido2Settings hasn't migrated!
        // To fix this, we should check if FIDO2 settings were migrated.
        // We can check if legacy preferences exist instead.

        var openedLegacyPrefs: SharedPreferences? = null
        try {
            val legacyPrefs =
                PerformanceTrace
                    .section(LEGACY_PREFS_OPEN_SECTION) {
                        openEncryptedPrefs()
                    }.also { openedLegacyPrefs = it }
            val hasLegacyCount =
                PerformanceTrace.section(LEGACY_CONTAINS_SECTION) {
                    legacyPrefs.contains(KEY_MAX_CREDENTIALS)
                }
            if (hasLegacyCount) {
                return legacyMigrationMutex.withLock {
                    if (legacyPrefs.contains(KEY_MAX_CREDENTIALS)) {
                        val legacyCount =
                            PerformanceTrace.section(LEGACY_READ_SECTION) {
                                legacyPrefs.getInt(KEY_MAX_CREDENTIALS, DEFAULT_STORAGE_LIMIT)
                            }
                        Logger.i {
                            "Migrating max_credential_count from legacy EncryptedSharedPreferences to Proto DataStore"
                        }

                        PerformanceTrace.section(MIGRATION_WRITE_SECTION) {
                            dataStore.updateData { currentPrefs ->
                                currentPrefs.copy(maxCredentialCount = legacyCount)
                            }
                        }

                        PerformanceTrace.section(LEGACY_REMOVAL_SECTION) {
                            legacyPrefs.edit(commit = true) {
                                remove(KEY_MAX_CREDENTIALS)
                            }
                        }
                        Logger.i { "Migration of max_credential_count completed successfully, legacy key cleared" }
                        legacyCount
                    } else {
                        // Another lookup may have completed migration after this call's first read.
                        currentCountOrDefault(dataStore.data.first())
                    }
                }
            }
        } catch (e: java.security.GeneralSecurityException) {
            invalidateLegacyPrefsCache(openedLegacyPrefs)
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        } catch (e: java.io.IOException) {
            invalidateLegacyPrefsCache(openedLegacyPrefs)
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        } catch (e: SecurityException) {
            invalidateLegacyPrefsCache(openedLegacyPrefs)
            Logger.e(e) { "Failed to read legacy EncryptedSharedPreferences for FIDO2 settings — treating as missing" }
        }

        // If not in legacy or already migrated, check DataStore
        // The default in Proto is 0, but our application default is 1000
        val latestPrefs =
            PerformanceTrace.section(DATA_STORE_REFRESH_SECTION) {
                if (prefs.maxCredentialCount <= 0) dataStore.data.first() else prefs
            }
        return currentCountOrDefault(latestPrefs)
    }

    private suspend fun currentCountOrDefault(prefs: UserPreferences): Int {
        val count =
            PerformanceTrace.section(DATA_STORE_VALUE_SECTION) {
                prefs.maxCredentialCount
            }
        return if (count <= 0) {
            // Write default to DataStore if it's 0 so it's consistent
            if (count == 0) {
                PerformanceTrace.section(DEFAULT_WRITE_SECTION) {
                    dataStore.updateData { it.copy(maxCredentialCount = DEFAULT_STORAGE_LIMIT) }
                }
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

    private suspend fun openEncryptedPrefs(): SharedPreferences =
        PerformanceTrace.section(LEGACY_PREFS_CACHE_SECTION) {
            cachedLegacyPrefs?.let { return@section it }
            legacyPrefsMutex.withLock {
                cachedLegacyPrefs?.let { return@withLock it }
                val masterKeyAlias =
                    PerformanceTrace.section(LEGACY_MASTER_KEY_SECTION) {
                        androidx.security.crypto.MasterKeys.getOrCreate(
                            androidx.security.crypto.MasterKeys.AES256_GCM_SPEC,
                        )
                    }
                val createdPrefs =
                    PerformanceTrace.section(LEGACY_PREFS_CREATE_SECTION) {
                        EncryptedSharedPreferences.create(
                            PREFS_FILE_NAME,
                            masterKeyAlias,
                            context,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    }
                cachedLegacyPrefs = createdPrefs
                createdPrefs
            }
        }

    private suspend fun invalidateLegacyPrefsCache(prefs: SharedPreferences?) {
        if (prefs == null) return
        legacyPrefsMutex.withLock {
            if (cachedLegacyPrefs === prefs) cachedLegacyPrefs = null
        }
    }
}
