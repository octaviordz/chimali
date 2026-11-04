package com.chimali.fido2.presentation.viewmodel

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.BluetoothCapabilityChecker
import com.chimali.fido2.data.transport.CapabilityCheckResult
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.data.transport.ProcessTransportSelection
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportPreference
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TransportSettingsViewModelTest {
    private class FakeCeremonyTransport : Fido2Transport {
        private val mutableConnectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
        override val connectionState: StateFlow<Fido2ConnectionState> = mutableConnectionState.asStateFlow()
        var connectCalls = 0
        var disconnectCalls = 0
        val completedCeremonies = mutableListOf<String>()

        override suspend fun connect(): Result<Unit> {
            connectCalls++
            mutableConnectionState.value = Fido2ConnectionState.Advertising
            return Result.success(Unit)
        }

        override suspend fun connectDevice(macAddress: String): Result<Unit> =
            Result.failure(UnsupportedOperationException("Synthetic ceremony transport is host initiated"))

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls++
            mutableConnectionState.value = Fido2ConnectionState.Idle
            return Result.success(Unit)
        }

        suspend fun performCeremony(
            ceremony: String,
            responseGate: CompletableDeferred<String>,
        ): String = responseGate.await().also { completedCeremonies += ceremony }
    }

    private class FakeSettingsRepository(
        var readResult: TransportPreferenceResult =
            TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L)),
        var writeResult: TransportSaveResult? = null,
    ) : Fido2SettingsRepository {
        var writes = 0

        override suspend fun getMaxCredentialCount(): Int = 50

        override suspend fun setMaxCredentialCount(count: Int) = Unit

        override suspend fun getTransportPreference(): TransportPreferenceResult = readResult

        override suspend fun saveTransportPreference(
            mode: BluetoothTransportMode,
            epochMillis: Long,
        ): TransportSaveResult {
            writes++
            return writeResult ?: TransportSaveResult.Success(TransportPreference(mode, epochMillis))
        }
    }

    private class FakeCapabilityChecker : BluetoothCapabilityChecker {
        override fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult =
            CapabilityCheckResult.Supported
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    @Req("FR-BT-SEL-010", "FR-BT-SEL-050")
    fun `read failure leaves choice unresolved and allows retry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeSettingsRepository(TransportPreferenceResult.ReadError(IllegalStateException("offline")))
            val process =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { error("settings must not construct transport") },
                    classicTransportFactory = { error("settings must not construct transport") },
                    coroutineScope = backgroundScope,
                )
            process.startSnapshot()
            advanceUntilIdle()
            val viewModel = TransportSettingsViewModel(repository, process)
            advanceUntilIdle()

            assertNull(viewModel.state.value.savedChoice)
            assertEquals("read_failed", viewModel.state.value.readError)
            assertFalse(viewModel.state.value.restartRequired)

            viewModel.select(BluetoothTransportMode.CLASSIC_HID)
            advanceUntilIdle()
            assertNull(viewModel.state.value.savedChoice)
            assertEquals(0, repository.writes)

            viewModel.retryRead()
            advanceUntilIdle()
            assertNull(viewModel.state.value.savedChoice)
            assertEquals("read_failed", viewModel.state.value.readError)

            repository.readResult =
                TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L))
            viewModel.retryRead()
            advanceUntilIdle()
            assertEquals(BluetoothTransportMode.FIDO_BLE, viewModel.state.value.savedChoice)
            assertNull(viewModel.state.value.readError)
            process.close()
        }

    @Test
    @Req("FR-BT-SEL-010", "FR-BT-SEL-050")
    fun `invalid saved choice stays unresolved and cannot be overwritten before retry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeSettingsRepository(TransportPreferenceResult.InvalidPreference("unknown"))
            val process =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { error("settings must not construct transport") },
                    classicTransportFactory = { error("settings must not construct transport") },
                    coroutineScope = backgroundScope,
                )
            process.startSnapshot()
            advanceUntilIdle()
            val viewModel = TransportSettingsViewModel(repository, process)
            advanceUntilIdle()

            assertNull(viewModel.state.value.savedChoice)
            assertEquals("invalid_preference", viewModel.state.value.readError)
            viewModel.select(BluetoothTransportMode.CLASSIC_HID)
            advanceUntilIdle()
            assertEquals(0, repository.writes)

            repository.readResult =
                TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L))
            viewModel.retryRead()
            advanceUntilIdle()
            assertEquals(BluetoothTransportMode.FIDO_BLE, viewModel.state.value.savedChoice)
            process.close()
        }

    @Test
    @Req("FR-BT-SEL-020", "FR-BT-SEL-030")
    fun `saved choice changes only after a successful write and active process choice stays frozen`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeSettingsRepository()
            val process =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { error("settings must not construct transport") },
                    classicTransportFactory = { error("settings must not construct transport") },
                    coroutineScope = backgroundScope,
                )
            process.startSnapshot()
            advanceUntilIdle()
            val viewModel = TransportSettingsViewModel(repository, process)
            advanceUntilIdle()

            viewModel.select(BluetoothTransportMode.CLASSIC_HID)
            advanceUntilIdle()
            assertEquals(BluetoothTransportMode.CLASSIC_HID, viewModel.state.value.savedChoice)
            assertEquals(BluetoothTransportMode.FIDO_BLE, viewModel.state.value.processState.processChoice)
            assertTrue(viewModel.state.value.restartRequired)

            repository.writeResult = TransportSaveResult.WriteError(IllegalStateException("disk full"))
            viewModel.select(BluetoothTransportMode.FIDO_BLE)
            advanceUntilIdle()
            assertEquals(BluetoothTransportMode.CLASSIC_HID, viewModel.state.value.savedChoice)
            assertEquals("write_failed", viewModel.state.value.saveError)
            assertTrue(viewModel.state.value.restartRequired)
            assertEquals(2, repository.writes)
            process.close()
        }

    @Test
    @Req("FR-BT-SEL-020", "FR-BT-SEL-030")
    fun `cancel pending selection restores active choice and no-op selection does not write`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeSettingsRepository()
            val process =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { error("settings must not construct transport") },
                    classicTransportFactory = { error("settings must not construct transport") },
                    coroutineScope = backgroundScope,
                )
            process.startSnapshot()
            advanceUntilIdle()
            val viewModel = TransportSettingsViewModel(repository, process)
            advanceUntilIdle()

            viewModel.select(BluetoothTransportMode.FIDO_BLE)
            advanceUntilIdle()
            assertEquals(0, repository.writes)

            viewModel.select(BluetoothTransportMode.CLASSIC_HID)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.restartRequired)
            viewModel.cancelPendingChange()
            advanceUntilIdle()

            assertEquals(BluetoothTransportMode.FIDO_BLE, viewModel.state.value.savedChoice)
            assertFalse(viewModel.state.value.restartRequired)
            assertEquals(2, repository.writes)
            process.close()
        }

    @Test
    @Req("FR-BT-SEL-020", "FR-BT-SEL-030")
    fun `classic process keeps its frozen handle while BLE choice awaits restart`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeSettingsRepository(
                    TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.CLASSIC_HID, 0L)),
                )
            val process =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { error("settings must not construct transport") },
                    classicTransportFactory = { error("settings must not construct transport") },
                    coroutineScope = backgroundScope,
                )
            process.startSnapshot()
            advanceUntilIdle()
            val viewModel = TransportSettingsViewModel(repository, process)
            advanceUntilIdle()

            viewModel.select(BluetoothTransportMode.FIDO_BLE)
            advanceUntilIdle()

            assertEquals(BluetoothTransportMode.FIDO_BLE, viewModel.state.value.savedChoice)
            assertEquals(BluetoothTransportMode.CLASSIC_HID, viewModel.state.value.processState.processChoice)
            assertTrue(viewModel.state.value.restartRequired)
            assertEquals(1, repository.writes)
            process.close()
        }

    @Test
    @Req("FR-BT-SEL-060", "FR-BT-SEL-030", "FR-BT-SEL-040")
    fun `saving a different mode does not disconnect either in-flight ceremony`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val ceremonyTypes = listOf("registration", "authentication")
            for (processMode in listOf(BluetoothTransportMode.FIDO_BLE, BluetoothTransportMode.CLASSIC_HID)) {
                for (ceremonyType in ceremonyTypes) {
                    val repository =
                        FakeSettingsRepository(
                            TransportPreferenceResult.Success(TransportPreference(processMode, 0L)),
                        )
                    val transport = FakeCeremonyTransport()
                    val process =
                        ProcessTransportSelection(
                            settingsRepository = repository,
                            capabilityChecker = FakeCapabilityChecker(),
                            bleTransportFactory = {
                                if (processMode ==
                                    BluetoothTransportMode.FIDO_BLE
                                ) {
                                    transport
                                } else {
                                    error("unselected BLE factory")
                                }
                            },
                            classicTransportFactory = {
                                if (processMode ==
                                    BluetoothTransportMode.CLASSIC_HID
                                ) {
                                    transport
                                } else {
                                    error("unselected Classic factory")
                                }
                            },
                            coroutineScope = backgroundScope,
                        )
                    try {
                        process.startSnapshot()
                        advanceUntilIdle()
                        assertTrue(process.connect().isSuccess)
                        val viewModel = TransportSettingsViewModel(repository, process)
                        advanceUntilIdle()

                        val responseGate = CompletableDeferred<String>()
                        val operation = async { transport.performCeremony(ceremonyType, responseGate) }
                        runCurrent()
                        val savedMode =
                            if (processMode == BluetoothTransportMode.FIDO_BLE) {
                                BluetoothTransportMode.CLASSIC_HID
                            } else {
                                BluetoothTransportMode.FIDO_BLE
                            }
                        viewModel.select(savedMode)
                        advanceUntilIdle()

                        assertEquals(savedMode, viewModel.state.value.savedChoice)
                        assertEquals(processMode, viewModel.state.value.processState.processChoice)
                        assertEquals(TransportAvailability.Ready, viewModel.state.value.processState.availability)
                        assertTrue(viewModel.state.value.restartRequired)
                        assertEquals(1, transport.connectCalls)
                        assertEquals(0, transport.disconnectCalls)
                        assertTrue(operation.isActive)

                        val expectedResponse = "$ceremonyType-response"
                        responseGate.complete(expectedResponse)
                        assertEquals(expectedResponse, operation.await())
                        assertEquals(listOf(ceremonyType), transport.completedCeremonies)
                        assertEquals(0, transport.disconnectCalls)
                    } finally {
                        process.disconnect()
                        process.close()
                    }
                }
            }
        }
}
