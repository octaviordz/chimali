package com.chimali.core.common.datastore

import com.chimali.core.common.annotation.Req
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
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    fun testDefaultValue() {
        val default = UserPreferencesSerializer.defaultValue
        assertEquals(0, default.migrationVersion)
        assertFalse(default.migrationCompleted)
        assertEquals("", default.encryptedWalletSeed)
        assertEquals(0, default.maxCredentialCount)
        assertEquals("fido_ble", default.bluetoothTransport)
        assertEquals(0L, default.bluetoothTransportSelectedAtEpochMillis)
    }

    @Test
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    fun testTransportRoundTripSerialization() =
        runTest {
            val original =
                UserPreferences(
                    migrationVersion = 2,
                    migrationCompleted = true,
                    encryptedWalletSeed = "seed_data",
                    maxCredentialCount = 5,
                    onboardingCompleted = true,
                    vaultFeatureEnabled = true,
                    passkeyAuthenticatorFeatureEnabled = true,
                    lastVisitedMainScreen = "home",
                    bluetoothTransport = "classic_hid",
                    bluetoothTransportSelectedAtEpochMillis = 1712000000000L,
                )

            val buffer = Buffer()
            UserPreferencesSerializer.writeTo(original, buffer)

            val deserialized = UserPreferencesSerializer.readFrom(buffer)
            assertEquals(original, deserialized)
            assertEquals("classic_hid", deserialized.bluetoothTransport)
            assertEquals(1712000000000L, deserialized.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testUnrelatedFieldPreservation() =
        runTest {
            val baseline =
                UserPreferences(
                    migrationVersion = 3,
                    migrationCompleted = true,
                    encryptedWalletSeed = "wallet_seed_secure",
                    maxCredentialCount = 100,
                    onboardingCompleted = true,
                    vaultFeatureEnabled = true,
                    passkeyAuthenticatorFeatureEnabled = true,
                    lastVisitedMainScreen = "vault",
                    bluetoothTransport = "fido_ble",
                    bluetoothTransportSelectedAtEpochMillis = 0L,
                )

            val updated =
                baseline.copy(
                    bluetoothTransport = "classic_hid",
                    bluetoothTransportSelectedAtEpochMillis = 1700000000000L,
                )

            val buffer = Buffer()
            UserPreferencesSerializer.writeTo(updated, buffer)
            val deserialized = UserPreferencesSerializer.readFrom(buffer)

            assertEquals(3, deserialized.migrationVersion)
            assertEquals(true, deserialized.migrationCompleted)
            assertEquals("wallet_seed_secure", deserialized.encryptedWalletSeed)
            assertEquals(100, deserialized.maxCredentialCount)
            assertEquals(true, deserialized.onboardingCompleted)
            assertEquals(true, deserialized.vaultFeatureEnabled)
            assertEquals(true, deserialized.passkeyAuthenticatorFeatureEnabled)
            assertEquals("vault", deserialized.lastVisitedMainScreen)
            assertEquals("classic_hid", deserialized.bluetoothTransport)
            assertEquals(1700000000000L, deserialized.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    fun testLosslessUnknownTransportSerialization() =
        runTest {
            val original =
                UserPreferences(
                    bluetoothTransport = "unknown_future_transport_mode",
                    bluetoothTransportSelectedAtEpochMillis = 42L,
                )

            val buffer = Buffer()
            UserPreferencesSerializer.writeTo(original, buffer)

            val deserialized = UserPreferencesSerializer.readFrom(buffer)
            assertEquals("unknown_future_transport_mode", deserialized.bluetoothTransport)
            assertEquals(42L, deserialized.bluetoothTransportSelectedAtEpochMillis)
        }
}
