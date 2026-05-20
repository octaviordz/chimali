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

            // Update value
            dataStore.updateData { prefs ->
                prefs.copy(
                    migrationVersion = 1,
                    migrationCompleted = true,
                    maxCredentialCount = 42,
                )
            }

            val updatedPrefs = dataStore.data.first()
            assertEquals(1, updatedPrefs.migrationVersion)
            assertTrue(updatedPrefs.migrationCompleted)
            assertEquals(42, updatedPrefs.maxCredentialCount)
        }
}
