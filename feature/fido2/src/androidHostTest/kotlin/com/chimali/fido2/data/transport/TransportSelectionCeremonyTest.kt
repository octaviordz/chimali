package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.TransportPreference
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import com.chimali.fido2.presentation.viewmodel.TransportSettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/** Verifies saving a future process choice does not interrupt an admitted host ceremony. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportSelectionCeremonyTest {
    private enum class CeremonyKind {
        Registration,
        Authentication,
    }

    /** Synthetic host-side ceremony gate; production CTAP routing is covered by transport lifecycle tests. */
    private class CeremonyTransport(
        val mode: BluetoothTransportMode,
    ) : Fido2Transport {
        private val mutableConnectionState =
            kotlinx.coroutines.flow.MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
        override val connectionState = mutableConnectionState

        val ceremonyStarted = CompletableDeferred<String>()
        val userApproval = CompletableDeferred<Unit>()
        val responseHandles = mutableListOf<String>()
        val responses = mutableListOf<ByteArray>()
        var connectCalls = 0
        var disconnectCalls = 0
        var credentialMutations = 0
        var committedInteractionEvents = 0

        override suspend fun connect(): Result<Unit> {
            connectCalls++
            mutableConnectionState.value = Fido2ConnectionState.Advertising
            return Result.success(Unit)
        }

        override suspend fun connectDevice(macAddress: String): Result<Unit> =
            Result.failure(UnsupportedOutboundConnectionException())

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls++
            mutableConnectionState.value = Fido2ConnectionState.Idle
            return Result.success(Unit)
        }

        suspend fun performHostCeremony(
            kind: CeremonyKind,
            requestId: String,
        ): ByteArray {
            val handle = "$mode/$requestId"
            ceremonyStarted.complete(handle)
            userApproval.await()

            if (kind == CeremonyKind.Registration) credentialMutations++
            committedInteractionEvents++
            return responseFor(handle).also { response ->
                responseHandles += handle
                responses += response.copyOf()
            }
        }
    }

    private class SettingsRepository(
        initialMode: BluetoothTransportMode,
    ) : Fido2SettingsRepository {
        private var savedPreference = TransportPreference(initialMode, 0L)

        override suspend fun getMaxCredentialCount(): Int = 50

        override suspend fun setMaxCredentialCount(count: Int) = Unit

        override suspend fun getTransportPreference(): TransportPreferenceResult =
            TransportPreferenceResult.Success(savedPreference)

        override suspend fun saveTransportPreference(
            mode: BluetoothTransportMode,
            epochMillis: Long,
        ): TransportSaveResult {
            savedPreference = TransportPreference(mode, epochMillis)
            return TransportSaveResult.Success(savedPreference)
        }
    }

    private data class Fixture(
        val initialMode: BluetoothTransportMode,
        val active: CeremonyTransport,
        val inactive: CeremonyTransport,
        val process: ProcessTransportSelection,
        val viewModel: TransportSettingsViewModel,
    )

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    @Req("FR-BT-SEL-030", "FR-BT-SEL-040", "FR-BT-SEL-060", "TR-BT-SEL-030")
    fun `saving another mode preserves the original registration and authentication handle and response`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            for (mode in BluetoothTransportMode.entries) {
                for (kind in CeremonyKind.entries) {
                    val fixture = createFixture(this, mode)
                    val requestId = "${kind.name.lowercase()}-original"
                    val operation = async { fixture.active.performHostCeremony(kind, requestId) }
                    runCurrent()
                    val originalHandle =
                        withTimeout(SYNCHRONIZATION_TIMEOUT_MS) {
                            fixture.active.ceremonyStarted.await()
                        }

                    fixture.viewModel.select(mode.otherMode())
                    advanceUntilIdle()

                    assertEquals(mode, fixture.process.state.value.processChoice)
                    assertEquals(mode.otherMode(), fixture.viewModel.state.value.savedChoice)
                    assertTrue(fixture.viewModel.state.value.restartRequired)
                    assertEquals(1, fixture.active.connectCalls)
                    assertEquals(0, fixture.active.disconnectCalls)
                    assertEquals(0, fixture.inactive.connectCalls)
                    assertFalse(fixture.inactive.ceremonyStarted.isCompleted)
                    assertEquals(0, fixture.active.credentialMutations)
                    assertEquals(0, fixture.active.committedInteractionEvents)

                    fixture.active.userApproval.complete(Unit)
                    val actualResponse = withTimeout(SYNCHRONIZATION_TIMEOUT_MS) { operation.await() }
                    val expectedResponse = responseFor(originalHandle)

                    assertContentEquals(expectedResponse, actualResponse)
                    assertEquals(listOf(originalHandle), fixture.active.responseHandles)
                    assertEquals(1, fixture.active.responses.size)
                    assertContentEquals(expectedResponse, fixture.active.responses.single())
                    assertEquals(if (kind == CeremonyKind.Registration) 1 else 0, fixture.active.credentialMutations)
                    assertEquals(1, fixture.active.committedInteractionEvents)
                    assertEquals(0, fixture.active.disconnectCalls)
                    assertEquals(0, fixture.inactive.connectCalls)
                    assertFalse(fixture.inactive.ceremonyStarted.isCompleted)
                    fixture.process.disconnect()
                    fixture.process.close()
                }
            }
        }

    @Test
    @Req("FR-BT-SEL-030", "FR-BT-SEL-040", "FR-BT-SEL-060", "TR-BT-SEL-030")
    fun `saving another mode cannot commit or emit a response after registration or authentication cancellation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            for (mode in BluetoothTransportMode.entries) {
                for (kind in CeremonyKind.entries) {
                    val fixture = createFixture(this, mode)
                    val requestId = "${kind.name.lowercase()}-cancel"
                    val operation = async { fixture.active.performHostCeremony(kind, requestId) }
                    runCurrent()
                    val originalHandle =
                        withTimeout(SYNCHRONIZATION_TIMEOUT_MS) {
                            fixture.active.ceremonyStarted.await()
                        }

                    fixture.viewModel.select(mode.otherMode())
                    advanceUntilIdle()
                    assertEquals(mode, fixture.process.state.value.processChoice)
                    assertTrue(fixture.viewModel.state.value.restartRequired)
                    assertEquals(0, fixture.active.disconnectCalls)
                    assertEquals(0, fixture.inactive.connectCalls)
                    assertFalse(fixture.inactive.ceremonyStarted.isCompleted)

                    operation.cancel()
                    withTimeout(SYNCHRONIZATION_TIMEOUT_MS) { operation.join() }
                    fixture.active.userApproval.complete(Unit)
                    runCurrent()
                    advanceUntilIdle()

                    assertTrue(operation.isCancelled)
                    assertTrue(fixture.active.responses.isEmpty(), "Cancelled $kind must not deliver a late response")
                    assertTrue(fixture.active.responseHandles.isEmpty())
                    assertEquals(0, fixture.active.credentialMutations)
                    assertEquals(0, fixture.active.committedInteractionEvents)
                    assertEquals(0, fixture.active.disconnectCalls)
                    assertEquals(0, fixture.inactive.connectCalls)
                    assertFalse(fixture.inactive.ceremonyStarted.isCompleted)
                    assertEquals("$mode/$requestId", originalHandle)
                    fixture.process.disconnect()
                    fixture.process.close()
                }
            }
        }

    private suspend fun createFixture(
        testScope: TestScope,
        mode: BluetoothTransportMode,
    ): Fixture {
        val ble = CeremonyTransport(BluetoothTransportMode.FIDO_BLE)
        val classic = CeremonyTransport(BluetoothTransportMode.CLASSIC_HID)
        val repository = SettingsRepository(mode)
        val process =
            ProcessTransportSelection(
                settingsRepository = repository,
                capabilityChecker =
                    object : BluetoothCapabilityChecker {
                        override fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult =
                            CapabilityCheckResult.Supported
                    },
                bleTransportFactory = { ble },
                classicTransportFactory = { classic },
                coroutineScope = testScope.backgroundScope,
            )
        process.startSnapshot()
        testScope.advanceUntilIdle()
        val viewModel = TransportSettingsViewModel(repository, process)
        testScope.advanceUntilIdle()
        assertTrue(process.connect().isSuccess)
        testScope.runCurrent()
        val active = if (mode == BluetoothTransportMode.FIDO_BLE) ble else classic
        val inactive = if (mode == BluetoothTransportMode.FIDO_BLE) classic else ble
        return Fixture(mode, active, inactive, process, viewModel)
    }

    private fun BluetoothTransportMode.otherMode(): BluetoothTransportMode =
        when (this) {
            BluetoothTransportMode.FIDO_BLE -> BluetoothTransportMode.CLASSIC_HID
            BluetoothTransportMode.CLASSIC_HID -> BluetoothTransportMode.FIDO_BLE
        }

    private companion object {
        const val SYNCHRONIZATION_TIMEOUT_MS = 1000L

        fun responseFor(handle: String): ByteArray = byteArrayOf(0x00) + handle.encodeToByteArray()
    }
}
