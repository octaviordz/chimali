package com.chimali.core.common.datastore

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class CrossPlatformDataStoreTest {
    @Test
    fun testCrossPlatformDataSharing() =
        runTest {
            val fileSystem = FileSystem.SYSTEM
            val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "chimali-cross-platform-${Random.nextLong()}"
            fileSystem.createDirectory(directory, mustCreate = true)
            val path = (directory / "cross_platform_prefs.pb").toString()

            try {
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
            } finally {
                fileSystem.deleteRecursively(directory)
            }
        }
}
