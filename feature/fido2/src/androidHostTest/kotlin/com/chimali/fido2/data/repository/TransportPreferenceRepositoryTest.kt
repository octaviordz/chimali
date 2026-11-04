package com.chimali.fido2.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest

class TransportPreferenceRepositoryTest {
    private val context = mockk<Context>(relaxed = true)

    private class TestDataStore(
        initial: UserPreferences = UserPreferences(),
    ) : DataStore<UserPreferences> {
        val state = MutableStateFlow(initial)
        private val mutex = Mutex()
        var failOnRead: Throwable? = null
        var failOnUpdate: Throwable? = null

        override val data: Flow<UserPreferences> =
            flow {
                failOnRead?.let { throw it }
                state.collect { emit(it) }
            }

        override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences): UserPreferences =
            mutex.withLock {
                failOnUpdate?.let { throw it }
                transform(state.value).also { state.value = it }
            }
    }

    @Test
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    fun testDefaultReadReturnsFidoBle() =
        runTest {
            val dataStore = TestDataStore(UserPreferences())
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.getTransportPreference()
            assertIs<TransportPreferenceResult.Success>(result)
            assertEquals(BluetoothTransportMode.FIDO_BLE, result.preference.mode)
            assertEquals(0L, result.preference.selectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testAtomicSaveSuccess() =
        runTest {
            val dataStore = TestDataStore(UserPreferences())
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val timestamp = 1712000000000L
            val result = repository.saveTransportPreference("classic_hid", timestamp)

            assertIs<TransportSaveResult.Success>(result)
            assertEquals(BluetoothTransportMode.CLASSIC_HID, result.preference.mode)
            assertEquals(timestamp, result.preference.selectedAtEpochMillis)
            assertEquals(false, result.isNoOp)

            val stored = dataStore.state.value
            assertEquals("classic_hid", stored.bluetoothTransport)
            assertEquals(timestamp, stored.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testPreservedUnrelatedFields() =
        runTest {
            val initial =
                UserPreferences(
                    migrationVersion = 2,
                    migrationCompleted = true,
                    encryptedWalletSeed = "seed_123",
                    maxCredentialCount = 25,
                    onboardingCompleted = true,
                    vaultFeatureEnabled = true,
                    passkeyAuthenticatorFeatureEnabled = true,
                    lastVisitedMainScreen = "settings/auth",
                    bluetoothTransport = "fido_ble",
                    bluetoothTransportSelectedAtEpochMillis = 0L,
                )
            val dataStore = TestDataStore(initial)
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            repository.saveTransportPreference("classic_hid", 1712000000000L)

            val stored = dataStore.state.value
            assertEquals(2, stored.migrationVersion)
            assertEquals(true, stored.migrationCompleted)
            assertEquals("seed_123", stored.encryptedWalletSeed)
            assertEquals(25, stored.maxCredentialCount)
            assertEquals(true, stored.onboardingCompleted)
            assertEquals(true, stored.vaultFeatureEnabled)
            assertEquals(true, stored.passkeyAuthenticatorFeatureEnabled)
            assertEquals("settings/auth", stored.lastVisitedMainScreen)
            assertEquals("classic_hid", stored.bluetoothTransport)
            assertEquals(1712000000000L, stored.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testUnchangedTimestampOnNoOp() =
        runTest {
            val initialTimestamp = 1700000000000L
            val initial =
                UserPreferences(
                    bluetoothTransport = "classic_hid",
                    bluetoothTransportSelectedAtEpochMillis = initialTimestamp,
                )
            val dataStore = TestDataStore(initial)
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val newTimestamp = 1712000000000L
            val result = repository.saveTransportPreference("classic_hid", newTimestamp)

            assertIs<TransportSaveResult.Success>(result)
            assertEquals(true, result.isNoOp)
            assertEquals(BluetoothTransportMode.CLASSIC_HID, result.preference.mode)
            assertEquals(initialTimestamp, result.preference.selectedAtEpochMillis)

            // DataStore state must remain unchanged
            val stored = dataStore.state.value
            assertEquals("classic_hid", stored.bluetoothTransport)
            assertEquals(initialTimestamp, stored.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testTypedRejectionOfUnknownInputCodeWithoutOverwrite() =
        runTest {
            val initial =
                UserPreferences(
                    bluetoothTransport = "fido_ble",
                    bluetoothTransportSelectedAtEpochMillis = 100L,
                )
            val dataStore = TestDataStore(initial)
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.saveTransportPreference("unknown_custom_mode", 200L)

            assertIs<TransportSaveResult.InvalidInput>(result)
            assertEquals("unknown_custom_mode", result.rawValue)

            // DataStore must not be modified
            val stored = dataStore.state.value
            assertEquals("fido_ble", stored.bluetoothTransport)
            assertEquals(100L, stored.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun testTypedRejectionOfUnknownStoredCodeWithoutFallback() =
        runTest {
            val initial =
                UserPreferences(
                    bluetoothTransport = "corrupted_or_future_value",
                    bluetoothTransportSelectedAtEpochMillis = 500L,
                )
            val dataStore = TestDataStore(initial)
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.getTransportPreference()
            assertIs<TransportPreferenceResult.InvalidPreference>(result)
            assertEquals("corrupted_or_future_value", result.rawValue)
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun testReadErrorOutcomeOnDataStoreFailure() =
        runTest {
            val dataStore = TestDataStore()
            dataStore.failOnRead = IOException("Disk read error")
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.getTransportPreference()
            assertIs<TransportPreferenceResult.ReadError>(result)
            assertIs<IOException>(result.cause)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testWriteErrorOutcomePreservesPriorValues() =
        runTest {
            val initial =
                UserPreferences(
                    bluetoothTransport = "fido_ble",
                    bluetoothTransportSelectedAtEpochMillis = 100L,
                )
            val dataStore = TestDataStore(initial)
            dataStore.failOnUpdate = IOException("Disk write error")
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.saveTransportPreference("classic_hid", 200L)
            assertIs<TransportSaveResult.WriteError>(result)
            assertIs<IOException>(result.cause)

            // Prior state preserved
            assertEquals("fido_ble", dataStore.state.value.bluetoothTransport)
            assertEquals(100L, dataStore.state.value.bluetoothTransportSelectedAtEpochMillis)
        }

    @Test
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    fun testSerializedSavesPreventRaceConditions() =
        runTest {
            val dataStore = TestDataStore(UserPreferences())
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val jobs =
                (1..10).map { i ->
                    async(Dispatchers.Default) {
                        val mode = if (i % 2 == 0) "classic_hid" else "fido_ble"
                        repository.saveTransportPreference(mode, i.toLong())
                    }
                }
            jobs.awaitAll()

            val finalState = dataStore.state.value
            assertTrue(finalState.bluetoothTransport in listOf("classic_hid", "fido_ble"))
            assertTrue(finalState.bluetoothTransportSelectedAtEpochMillis > 0L)
        }
}
