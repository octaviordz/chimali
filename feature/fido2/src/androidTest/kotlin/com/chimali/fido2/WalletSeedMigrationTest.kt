package com.chimali.fido2

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WalletSeedMigrationTest {

    private lateinit var context: Context
    private lateinit var dataStore: androidx.datastore.core.DataStore<UserPreferences>
    private lateinit var provider: WalletMasterSeedProvider
    private lateinit var mockGenerator: MasterSeedGenerator
    private lateinit var mockHdkManager: HdkManager
    private lateinit var encryptionWrapper: EncryptionWrapper

    private val prefsFileName = "chimali_wallet_seed"
    private val keyMnemonic = "bip39_mnemonic"
    private val fakeMnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockGenerator = mockk(relaxed = true)
        mockHdkManager = mockk(relaxed = true)
        encryptionWrapper = EncryptionWrapper()

        // Clean up any existing data before test
        cleanupData()

        dataStore = createUserPreferencesDataStore(
            producePath = {
                context.filesDir.resolve("user_prefs_test.pb").absolutePath
            }
        )

        provider = WalletMasterSeedProvider(
            context = context,
            masterSeedGenerator = mockGenerator,
            hdkManager = mockHdkManager,
            dataStore = dataStore,
            encryptionWrapper = encryptionWrapper
        )
    }

    @After
    fun tearDown() {
        cleanupData()
    }

    @Suppress("SwallowedException")
    private fun cleanupData() {
        // Delete datastore file
        File(context.filesDir, "user_prefs_test.pb").delete()
        
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
        legacyPrefs.edit().putString(keyMnemonic, fakeMnemonic).commit()

        // Verify it was saved
        assertEquals(fakeMnemonic, legacyPrefs.getString(keyMnemonic, null))

        // 2. Trigger migration
        val words = provider.getMnemonic()

        // 3. Verify mnemonic was correctly migrated
        assertNotNull(words)
        assertEquals(fakeMnemonic.split(" "), words)

        // 4. Verify DataStore has the data
        val prefs = dataStore.data.first()
        assertTrue(prefs.migrationCompleted)
        assertTrue(prefs.encryptedWalletSeed.isNotEmpty())

        // 5. Verify legacy prefs were deleted
        assertFalse(legacyPrefs.contains(keyMnemonic))
    }
}
