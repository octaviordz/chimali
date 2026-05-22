package com.chimali.core.common.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class UserPreferencesDataStoreTest {
    @Test
    fun testDataStoreCreateAndUpdate() =
        runTest {
            val path = "/test_prefs.pb"

            val dataStore =
                createUserPreferencesDataStore(
                    producePath = { path },
                )

            // Default value
            val defaultPrefs = dataStore.data.first()
            assertEquals(0, defaultPrefs.migrationVersion)
            assertEquals(false, defaultPrefs.migrationCompleted)
            assertEquals(false, defaultPrefs.onboardingCompleted)
            assertEquals(false, defaultPrefs.vaultFeatureEnabled)
            assertEquals(false, defaultPrefs.passkeyAuthenticatorFeatureEnabled)
            assertEquals("", defaultPrefs.lastVisitedMainScreen)

            // Update value
            dataStore.updateData { prefs ->
                prefs.copy(
                    migrationVersion = 1,
                    migrationCompleted = true,
                    maxCredentialCount = 42,
                    onboardingCompleted = true,
                    vaultFeatureEnabled = true,
                    passkeyAuthenticatorFeatureEnabled = false,
                    lastVisitedMainScreen = "vault/home",
                )
            }

            val updatedPrefs = dataStore.data.first()
            assertEquals(1, updatedPrefs.migrationVersion)
            assertTrue(updatedPrefs.migrationCompleted)
            assertEquals(42, updatedPrefs.maxCredentialCount)
            assertTrue(updatedPrefs.onboardingCompleted)
            assertTrue(updatedPrefs.vaultFeatureEnabled)
            assertEquals(false, updatedPrefs.passkeyAuthenticatorFeatureEnabled)
            assertEquals("vault/home", updatedPrefs.lastVisitedMainScreen)
        }
}
