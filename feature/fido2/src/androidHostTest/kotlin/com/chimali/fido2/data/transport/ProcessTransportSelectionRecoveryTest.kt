package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportPreference
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTransportSelectionRecoveryTest {
    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun `activation retry maps fresh unsupported permission and unknown capability signals`() =
        runTest {
            val cases =
                listOf(
                    CapabilityCheckResult.Unsupported to TransportUnavailableReason.Unsupported,
                    CapabilityCheckResult.PermissionRequired to TransportUnavailableReason.PermissionRequired,
                    CapabilityCheckResult.Unknown to null,
                )

            for ((result, unavailableReason) in cases) {
                val checker = RecoveryCapabilityChecker()
                val controller =
                    ProcessTransportSelection(
                        settingsRepository = RecoverySettingsRepository(),
                        capabilityChecker = checker,
                        bleTransportFactory = { RecoveryTransport() },
                        classicTransportFactory = { RecoveryTransport() },
                        coroutineScope = this,
                    )
                try {
                    controller.startSnapshot()
                    advanceUntilIdle()
                    checker.result = result
                    controller.retryActivation()

                    val expected =
                        unavailableReason?.let { TransportAvailability.Unavailable(it) }
                            ?: TransportAvailability.SelectedNotStarted
                    assertEquals(expected, controller.state.value.availability, "Capability $result")
                    assertEquals(BluetoothTransportMode.FIDO_BLE, controller.state.value.processChoice)
                } finally {
                    controller.close()
                }
            }
        }

    @Test
    @Req("FR-BT-SEL-030", "TR-BT-SEL-020")
    fun `snapshot retry reads again only while transport choice is unresolved`() =
        runTest {
            val repository = RecoverySettingsRepository(TransportPreferenceResult.ReadError(IOException("offline")))
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = RecoveryCapabilityChecker(),
                    bleTransportFactory = { RecoveryTransport() },
                    classicTransportFactory = { RecoveryTransport() },
                    coroutineScope = this,
                )
            try {
                controller.retryActivation()
                controller.startSnapshot()
                advanceUntilIdle()
                assertNull(controller.state.value.processChoice)

                repository.result =
                    TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.CLASSIC_HID, 0L))
                controller.retrySnapshot()
                advanceUntilIdle()

                assertEquals(BluetoothTransportMode.CLASSIC_HID, controller.state.value.processChoice)
                assertEquals(2, repository.readCount.get())
                controller.retrySnapshot()
                assertEquals(2, repository.readCount.get())
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun `Classic outbound requests activate only the selected transport and propagate startup failure`() =
        runTest {
            val repository =
                RecoverySettingsRepository(
                    TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.CLASSIC_HID, 0L)),
                )
            val classic = RecoveryTransport()
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = RecoveryCapabilityChecker(),
                    bleTransportFactory = { error("BLE must not be selected") },
                    classicTransportFactory = { classic },
                    coroutineScope = this,
                )
            try {
                assertTrue(controller.connectDevice("AA:BB:CC:DD:EE:FF").isSuccess)
                assertEquals(1, classic.connectCallCount.get())
                assertEquals(1, classic.connectDeviceCallCount.get())
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
            } finally {
                controller.close()
            }

            val failedClassic = RecoveryTransport(Result.failure(IllegalStateException("HID profile unavailable")))
            val failedController =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = RecoveryCapabilityChecker(),
                    bleTransportFactory = { error("BLE must not be selected") },
                    classicTransportFactory = { failedClassic },
                    coroutineScope = this,
                )
            try {
                assertTrue(failedController.connectDevice("AA:BB:CC:DD:EE:FF").isFailure)
                assertEquals(1, failedClassic.connectCallCount.get())
                assertEquals(0, failedClassic.connectDeviceCallCount.get())
                assertEquals(
                    TransportAvailability.Unavailable(TransportUnavailableReason.StartupFailed),
                    failedController.state.value.availability,
                )
            } finally {
                failedController.close()
            }

            val throwingClassic =
                RecoveryTransport(connectException = IllegalStateException("HID activation threw"))
            val throwingController =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = RecoveryCapabilityChecker(),
                    bleTransportFactory = { error("BLE must not be selected") },
                    classicTransportFactory = { throwingClassic },
                    coroutineScope = this,
                )
            try {
                assertTrue(throwingController.connectDevice("AA:BB:CC:DD:EE:FF").isFailure)
                assertEquals(1, throwingClassic.connectCallCount.get())
                assertEquals(1, throwingClassic.disconnectCallCount.get())
                assertEquals(
                    TransportAvailability.Unavailable(TransportUnavailableReason.StartupFailed),
                    throwingController.state.value.availability,
                )
            } finally {
                throwingController.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun `disconnect is idempotent before activation and resets a ready selected transport`() =
        runTest {
            val ble = RecoveryTransport()
            val controller =
                ProcessTransportSelection(
                    settingsRepository = RecoverySettingsRepository(),
                    capabilityChecker = RecoveryCapabilityChecker(),
                    bleTransportFactory = { ble },
                    classicTransportFactory = { RecoveryTransport() },
                    coroutineScope = this,
                )
            try {
                controller.startSnapshot()
                advanceUntilIdle()
                assertTrue(controller.disconnect().isSuccess)
                assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)
                assertEquals(0, ble.disconnectCallCount.get())

                assertTrue(controller.connect().isSuccess)
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
                assertTrue(controller.disconnect().isSuccess)
                assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)
                assertEquals(1, ble.disconnectCallCount.get())
            } finally {
                controller.close()
            }
        }
}

private class RecoverySettingsRepository(
    var result: TransportPreferenceResult =
        TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L)),
) : Fido2SettingsRepository {
    val readCount = AtomicInteger()

    override suspend fun getMaxCredentialCount(): Int = 50

    override suspend fun setMaxCredentialCount(count: Int) = Unit

    override suspend fun getTransportPreference(): TransportPreferenceResult {
        readCount.incrementAndGet()
        return result
    }
}

private class RecoveryCapabilityChecker(
    var result: CapabilityCheckResult = CapabilityCheckResult.Supported,
) : BluetoothCapabilityChecker {
    override fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult = result
}

private class RecoveryTransport(
    private val connectResult: Result<Unit> = Result.success(Unit),
    private val connectException: Exception? = null,
) : Fido2Transport {
    private val mutableConnectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
    override val connectionState: StateFlow<Fido2ConnectionState> = mutableConnectionState.asStateFlow()
    val connectCallCount = AtomicInteger()
    val connectDeviceCallCount = AtomicInteger()
    val disconnectCallCount = AtomicInteger()

    override suspend fun connect(): Result<Unit> {
        connectCallCount.incrementAndGet()
        connectException?.let { throw it }
        if (connectResult.isSuccess) mutableConnectionState.value = Fido2ConnectionState.Advertising
        return connectResult
    }

    override suspend fun connectDevice(macAddress: String): Result<Unit> {
        connectDeviceCallCount.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        disconnectCallCount.incrementAndGet()
        mutableConnectionState.value = Fido2ConnectionState.Idle
        return Result.success(Unit)
    }
}
