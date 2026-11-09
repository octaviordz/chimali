package com.chimali.core.common.datastore

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class UserPreferencesDataStorePerformanceTest {
    @Test
    fun testDataStorePerformance() =
        runTest {
            val fileSystem = FileSystem.SYSTEM
            val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "chimali-prefs-performance-${Random.nextLong()}"
            fileSystem.createDirectory(directory, mustCreate = true)

            try {
                val dataStore =
                    createUserPreferencesDataStore(
                        producePath = { (directory / "performance_prefs.pb").toString() },
                    )

                val writeTime =
                    measureTime {
                        dataStore.updateData { prefs ->
                            prefs.copy(
                                migrationVersion = 1,
                                migrationCompleted = true,
                                maxCredentialCount = 100,
                            )
                        }
                    }

                val readTime =
                    measureTime {
                        val prefs = dataStore.data.first()
                        assertTrue(prefs.migrationCompleted)
                    }

                // Assert that read and write are fast enough (e.g. < 50ms in tests)
                assertTrue(writeTime.inWholeMilliseconds < 500, "Write time too slow: $writeTime")
                assertTrue(readTime.inWholeMilliseconds < 500, "Read time too slow: $readTime")
            } finally {
                fileSystem.deleteRecursively(directory)
            }
        }
}
