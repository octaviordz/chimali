package com.chimali.fido2.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    @Req("NFR-SEC-030", "NFR-SEC-041", "TR-AUTH-031")
    fun `legacy encrypted preference initialization failure logs no provider details`() =
        runTest {
            val marker = "synthetic-legacy-preference-provider-secret"
            mockkStatic(MasterKeys::class)
            mockkStatic(EncryptedSharedPreferences::class)
            every { MasterKeys.getOrCreate(any()) } returns "synthetic-master-key"
            every {
                EncryptedSharedPreferences.create(
                    any<String>(),
                    any<String>(),
                    any<Context>(),
                    any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                    any<EncryptedSharedPreferences.PrefValueEncryptionScheme>(),
                )
            } throws GeneralSecurityException(marker)

            try {
                val dataStore = TestDataStore(UserPreferences())
                val repository = Fido2SettingsRepositoryImpl(context, dataStore)
                CapturedDiagnostics().use { diagnostics ->
                    assertEquals(1000, repository.getMaxCredentialCount())
                    assertTrue(diagnostics.size > 0, "The provider failure should produce a fixed diagnostic")
                    diagnostics.assertNoSensitiveValues(marker, "synthetic-master-key")
                }
                assertEquals(1000, dataStore.state.value.maxCredentialCount)
            } finally {
                unmockkStatic(EncryptedSharedPreferences::class)
                unmockkStatic(MasterKeys::class)
            }
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
            dataStore.failOnRead = IOException("synthetic-settings-read-marker")
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val result = repository.getTransportPreference()
            assertEquals(TransportPreferenceResult.ReadError, result)
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020", "NFR-SEC-041")
    fun `observed read error returns a cause-free failure result`() =
        runTest {
            val dataStore =
                TestDataStore().apply {
                    failOnRead = IOException("synthetic-observed-settings-read-marker")
                }
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            assertEquals(TransportPreferenceResult.ReadError, repository.observeTransportPreference().first())
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
            val failure = IOException("synthetic datastore exception marker")
            dataStore.failOnUpdate = failure
            val repository = Fido2SettingsRepositoryImpl(context, dataStore)

            val captured = mutableListOf<Triple<String, String, Throwable?>>()
            val writer =
                object : LogWriter() {
                    override fun log(
                        severity: Severity,
                        message: String,
                        tag: String,
                        throwable: Throwable?,
                    ) {
                        captured += Triple(message, tag, throwable)
                    }
                }
            val priorWriters = Logger.mutableConfig.logWriterList
            try {
                Logger.setLogWriters(writer)

                val result = repository.saveTransportPreference("classic_hid", 200L)
                assertEquals(TransportSaveResult.WriteError, result)
                assertTrue(captured.isNotEmpty(), "The write error retains a fixed diagnostic event")
                assertTrue(
                    captured.all { (message, _, throwable) ->
                        !message.contains("synthetic datastore exception marker") &&
                            !message.contains("classic_hid") &&
                            throwable == null
                    },
                    "Logger output must omit exception details and transport codes",
                )

                // Prior state preserved
                assertEquals("fido_ble", dataStore.state.value.bluetoothTransport)
                assertEquals(100L, dataStore.state.value.bluetoothTransportSelectedAtEpochMillis)
            } finally {
                Logger.setLogWriters(priorWriters)
            }
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
