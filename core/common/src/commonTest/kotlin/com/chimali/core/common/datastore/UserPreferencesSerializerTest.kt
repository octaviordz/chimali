package com.chimali.core.common.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import okio.Buffer

class UserPreferencesSerializerTest {
    @Test
    fun testSerializationAndDeserialization() =
        runTest {
            val original =
                UserPreferences(
                    migrationVersion = 1,
                    migrationCompleted = true,
                    encryptedWalletSeed = "encrypted_seed_data",
                    maxCredentialCount = 10,
                )

            val buffer = Buffer()
            UserPreferencesSerializer.writeTo(original, buffer)

            val deserialized = UserPreferencesSerializer.readFrom(buffer)
            assertEquals(original, deserialized)
        }

    @Test
    fun testDefaultValue() {
        val default = UserPreferencesSerializer.defaultValue
        assertEquals(0, default.migrationVersion)
        assertFalse(default.migrationCompleted)
        assertEquals("", default.encryptedWalletSeed)
        assertEquals(0, default.maxCredentialCount)
    }
}
