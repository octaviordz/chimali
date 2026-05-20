package com.chimali.core.common.datastore

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class UserPreferencesDataStorePerformanceTest {
    @Test
    fun testDataStorePerformance() =
        runTest {
            val path = "/performance_prefs.pb"

            val dataStore =
                createUserPreferencesDataStore(
                    producePath = { path },
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
        }
}
