package com.chimali.fido2

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.fido2.data.repository.Fido2SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Fido2SettingsMigrationTest {

    private lateinit var context: Context
    private lateinit var dataStore: androidx.datastore.core.DataStore<UserPreferences>
    private lateinit var repository: Fido2SettingsRepositoryImpl

    private val prefsFileName = "fido2_settings"
    private val keyMaxCredentials = "max_credential_count"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Clean up any existing data before test
        cleanupData()

        dataStore = createUserPreferencesDataStore(
            producePath = {
                context.filesDir.resolve("user_prefs_fido2_settings_test.pb").absolutePath
            }
        )

        repository = Fido2SettingsRepositoryImpl(
            context = context,
            dataStore = dataStore
        )
    }

    @After
    fun tearDown() {
        cleanupData()
    }

    @Suppress("SwallowedException")
    private fun cleanupData() {
        // Delete datastore file
        File(context.filesDir, "user_prefs_fido2_settings_test.pb").delete()
        
        // Clear EncryptedSharedPreferences
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                prefsFileName,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().clear().commit()
        } catch (e: Exception) {
            // Ignore setup errors
        }
    }

    @Test
    fun testMigrationFromEncryptedSharedPreferencesToDataStore() = runTest {
        // 1. Setup legacy data
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val legacyPrefs = EncryptedSharedPreferences.create(
            prefsFileName,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        legacyPrefs.edit().putInt(keyMaxCredentials, 42).commit()

        // Verify it was saved
        assertEquals(42, legacyPrefs.getInt(keyMaxCredentials, -1))

        // 2. Trigger migration
        val maxCount = repository.getMaxCredentialCount()

        // 3. Verify max count was correctly migrated
        assertEquals(42, maxCount)

        // 4. Verify DataStore has the data
        val prefs = dataStore.data.first()
        assertEquals(42, prefs.maxCredentialCount)

        // 5. Verify legacy prefs were deleted
        assertFalse(legacyPrefs.contains(keyMaxCredentials))
    }
}
