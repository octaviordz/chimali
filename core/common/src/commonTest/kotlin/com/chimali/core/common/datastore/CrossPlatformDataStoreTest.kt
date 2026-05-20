package com.chimali.core.common.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CrossPlatformDataStoreTest {
    @Test
    fun testCrossPlatformDataSharing() =
        runTest {
            val path = "/cross_platform_prefs.pb"

            val dataStore =
                createUserPreferencesDataStore(
                    producePath = { path },
                )

            dataStore.updateData { prefs ->
                prefs.copy(
                    migrationVersion = 1,
                    migrationCompleted = true,
                    maxCredentialCount = 100,
                )
            }

            val updatedPrefs = dataStore.data.first()
            assertEquals(1, updatedPrefs.migrationVersion)
            assertTrue(updatedPrefs.migrationCompleted)
            assertEquals(100, updatedPrefs.maxCredentialCount)
        }
}
