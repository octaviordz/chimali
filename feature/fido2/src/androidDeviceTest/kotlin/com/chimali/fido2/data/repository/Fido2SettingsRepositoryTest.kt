package com.chimali.fido2.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests use isolated instrumentation-package preferences and unique DataStore files. */
@RunWith(AndroidJUnit4::class)
class Fido2SettingsRepositoryTest {
    @Test
    fun legacyValueTakesPrecedenceAndMigrationPreservesUnrelatedPreferences() =
        withSettingsFixture { fixture ->
            fixture.dataStore.updateData {
                it.copy(
                    maxCredentialCount = PREEXISTING_DATASTORE_COUNT,
                    onboardingCompleted = true,
                    vaultFeatureEnabled = true,
                    lastVisitedMainScreen = "vault/home",
                )
            }
            assertTrue(
                fixture.legacyPrefs
                    .edit()
                    .putInt(KEY_MAX_CREDENTIALS, LEGACY_PREFERRED_COUNT)
                    .commit(),
            )

            assertEquals(LEGACY_PREFERRED_COUNT, fixture.repository.getMaxCredentialCount())

            val stored = fixture.dataStore.data.first()
            assertEquals(LEGACY_PREFERRED_COUNT, stored.maxCredentialCount)
            assertTrue(stored.onboardingCompleted)
            assertTrue(stored.vaultFeatureEnabled)
            assertEquals("vault/home", stored.lastVisitedMainScreen)
            assertFalse(fixture.legacyPrefs.contains(KEY_MAX_CREDENTIALS))
        }

    @Test
    fun defaultAndCurrentValuesRemainStableAcrossRepositoryInstances() =
        withSettingsFixture { fixture ->
            assertEquals(DEFAULT_STORAGE_LIMIT, fixture.repository.getMaxCredentialCount())
            assertEquals(
                DEFAULT_STORAGE_LIMIT,
                fixture.dataStore.data
                    .first()
                    .maxCredentialCount,
            )

            fixture.repository.setMaxCredentialCount(SET_COUNT)
            fixture.dataStore.updateData { it.copy(passkeyAuthenticatorFeatureEnabled = true) }
            assertEquals(SET_COUNT, fixture.repository.getMaxCredentialCount())

            val restartedRepository = Fido2SettingsRepositoryImpl(fixture.context, fixture.dataStore)
            assertEquals(SET_COUNT, restartedRepository.getMaxCredentialCount())
            assertTrue(
                fixture.dataStore.data
                    .first()
                    .passkeyAuthenticatorFeatureEnabled,
            )
        }

    @Test
    fun unavailableLegacyPreferencesFallBackToTheCurrentDataStoreValue() =
        withSettingsFixture { fixture ->
            fixture.dataStore.updateData { it.copy(maxCredentialCount = ERROR_FALLBACK_COUNT) }
            val unavailableLegacyContext =
                object : ContextWrapper(fixture.context) {
                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences = throw SecurityException("Synthetic legacy preference access failure")
                }

            val repository = Fido2SettingsRepositoryImpl(unavailableLegacyContext, fixture.dataStore)

            assertEquals(ERROR_FALLBACK_COUNT, repository.getMaxCredentialCount())
        }

    @Test
    fun failedMigrationWriteLeavesLegacyValueAvailableForRetry() =
        withSettingsFixture { fixture ->
            assertTrue(
                fixture.legacyPrefs
                    .edit()
                    .putInt(KEY_MAX_CREDENTIALS, MIGRATION_WRITE_FAILURE_COUNT)
                    .commit(),
            )
            val failingStore =
                object : androidx.datastore.core.DataStore<UserPreferences> by fixture.dataStore {
                    override suspend fun updateData(
                        transform: suspend (UserPreferences) -> UserPreferences,
                    ): UserPreferences = throw IOException("Synthetic DataStore write failure")
                }
            val repository = Fido2SettingsRepositoryImpl(fixture.context, failingStore)

            var observedFailure = false
            try {
                repository.getMaxCredentialCount()
            } catch (_: IOException) {
                observedFailure = true
            }

            assertTrue(observedFailure)
            assertTrue(fixture.legacyPrefs.contains(KEY_MAX_CREDENTIALS))
            assertEquals(MIGRATION_WRITE_FAILURE_COUNT, fixture.legacyPrefs.getInt(KEY_MAX_CREDENTIALS, MISSING_COUNT))
        }

    @Test
    fun concurrentCurrentValueLookupsReturnTheStoredValue() =
        withSettingsFixture { fixture ->
            fixture.dataStore.updateData { it.copy(maxCredentialCount = CONCURRENT_CURRENT_COUNT) }

            val values =
                coroutineScope {
                    (1..CONCURRENT_LOOKUP_COUNT)
                        .map {
                            async(Dispatchers.IO) { fixture.repository.getMaxCredentialCount() }
                        }.awaitAll()
                }

            assertEquals(List(CONCURRENT_LOOKUP_COUNT) { CONCURRENT_CURRENT_COUNT }, values)
            assertEquals(
                CONCURRENT_CURRENT_COUNT,
                fixture.dataStore.data
                    .first()
                    .maxCredentialCount,
            )
        }

    @Test
    fun concurrentLegacyMigrationPreservesTheValueAndRemovesTheLegacyKey() =
        withSettingsFixture { fixture ->
            assertTrue(
                fixture.legacyPrefs
                    .edit()
                    .putInt(KEY_MAX_CREDENTIALS, CONCURRENT_LEGACY_COUNT)
                    .commit(),
            )

            val values =
                coroutineScope {
                    (1..CONCURRENT_LOOKUP_COUNT)
                        .map {
                            async(Dispatchers.IO) { fixture.repository.getMaxCredentialCount() }
                        }.awaitAll()
                }

            assertEquals(List(CONCURRENT_LOOKUP_COUNT) { CONCURRENT_LEGACY_COUNT }, values)
            assertEquals(
                CONCURRENT_LEGACY_COUNT,
                fixture.dataStore.data
                    .first()
                    .maxCredentialCount,
            )
            assertFalse(fixture.legacyPrefs.contains(KEY_MAX_CREDENTIALS))
        }

    private fun withSettingsFixture(block: suspend (SettingsFixture) -> Unit) =
        runBlocking {
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val testId = UUID.randomUUID().toString()
            val isolatedContext = IsolatedSettingsContext(testContext, testId)
            val dataStoreDirectory = File(testContext.filesDir, "fido2-settings-tests/$testId")
            check(dataStoreDirectory.mkdirs())
            val dataStoreFile = File(dataStoreDirectory, "user-preferences.pb")
            val dataStore = createUserPreferencesDataStore { dataStoreFile.absolutePath }
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val legacyPrefs =
                EncryptedSharedPreferences.create(
                    LEGACY_PREFS_FILE_NAME,
                    masterKeyAlias,
                    isolatedContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            try {
                block(
                    SettingsFixture(
                        isolatedContext,
                        dataStore,
                        legacyPrefs,
                        Fido2SettingsRepositoryImpl(isolatedContext, dataStore),
                    ),
                )
            } finally {
                legacyPrefs.edit().clear().commit()
                isolatedContext.deleteIsolatedSharedPreferences(LEGACY_PREFS_FILE_NAME)
                dataStoreDirectory.deleteRecursively()
            }
        }

    private data class SettingsFixture(
        val context: Context,
        val dataStore: androidx.datastore.core.DataStore<UserPreferences>,
        val legacyPrefs: SharedPreferences,
        val repository: Fido2SettingsRepositoryImpl,
    )

    private class IsolatedSettingsContext(
        base: Context,
        private val testId: String,
    ) : ContextWrapper(base) {
        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences = super.getSharedPreferences("$name-$testId", mode)

        fun deleteIsolatedSharedPreferences(name: String) {
            super.deleteSharedPreferences("$name-$testId")
        }
    }

    private companion object {
        const val LEGACY_PREFS_FILE_NAME = "fido2_settings"
        const val KEY_MAX_CREDENTIALS = "max_credential_count"
        const val DEFAULT_STORAGE_LIMIT = 1000
        const val PREEXISTING_DATASTORE_COUNT = 42
        const val LEGACY_PREFERRED_COUNT = 17
        const val SET_COUNT = 23
        const val ERROR_FALLBACK_COUNT = 37
        const val MIGRATION_WRITE_FAILURE_COUNT = 31
        const val CONCURRENT_CURRENT_COUNT = 29
        const val CONCURRENT_LEGACY_COUNT = 41
        const val CONCURRENT_LOOKUP_COUNT = 8
        const val MISSING_COUNT = -1
    }
}
