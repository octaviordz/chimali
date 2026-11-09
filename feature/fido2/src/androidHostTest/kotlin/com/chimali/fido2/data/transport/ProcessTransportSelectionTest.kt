package com.chimali.fido2.data.transport

import android.os.Build
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportPreference
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTransportSelectionTest {
    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun classicHidCapabilityIsUnknownWhenAndroidCannotPreflightOemProfileBinding() {
        assertEquals(CapabilityCheckResult.Unsupported, classicHidCapabilityForApi(Build.VERSION_CODES.O))
        assertEquals(CapabilityCheckResult.Unknown, classicHidCapabilityForApi(Build.VERSION_CODES.P))
        assertEquals(CapabilityCheckResult.Unknown, classicHidCapabilityForApi(Build.VERSION_CODES.Q))
    }

    private class FakeSettingsRepository(
        var preferenceResult: TransportPreferenceResult =
            TransportPreferenceResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L)),
        var readDelayMs: Long = 0L,
    ) : Fido2SettingsRepository {
        var readCount = AtomicInteger(0)

        override suspend fun getMaxCredentialCount(): Int = 50

        override suspend fun setMaxCredentialCount(count: Int) = Unit

        override fun observeTransportPreference(): Flow<TransportPreferenceResult> = flowOf(preferenceResult)

        override suspend fun getTransportPreference(): TransportPreferenceResult {
            readCount.incrementAndGet()
            if (readDelayMs > 0) delay(readDelayMs)
            return preferenceResult
        }

        override suspend fun saveTransportPreference(
            transportCode: String,
            epochMillis: Long,
        ): TransportSaveResult =
            TransportSaveResult.Success(TransportPreference(BluetoothTransportMode.FIDO_BLE, epochMillis))
    }

    private class FakeCapabilityChecker(
        var result: CapabilityCheckResult = CapabilityCheckResult.Supported,
    ) : BluetoothCapabilityChecker {
        var checkCount = AtomicInteger(0)
        var lastCheckedMode: BluetoothTransportMode? = null

        override fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult {
            checkCount.incrementAndGet()
            lastCheckedMode = mode
            return result
        }
    }

    private class FakeTransport(
        val name: String,
        var connectDelayMs: Long = 0L,
        var connectResult: Result<Unit> = Result.success(Unit),
    ) : Fido2Transport {
        private val mutableConnectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
        override val connectionState: StateFlow<Fido2ConnectionState> = mutableConnectionState.asStateFlow()

        var connectCallCount = AtomicInteger(0)
        var disconnectCallCount = AtomicInteger(0)
        var connectDeviceCallCount = AtomicInteger(0)

        override suspend fun connect(): Result<Unit> {
            connectCallCount.incrementAndGet()
            if (connectDelayMs > 0) delay(connectDelayMs)
            if (connectResult.isSuccess) {
                mutableConnectionState.value = Fido2ConnectionState.Advertising
            }
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

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun testOneSnapshotBeforeTransportConstructionOrHardware() =
        runTest {
            val repository = FakeSettingsRepository()
            val capabilityChecker = FakeCapabilityChecker()
            var bleFactoryCalled = false
            var classicFactoryCalled = false

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = capabilityChecker,
                    bleTransportFactory = {
                        bleFactoryCalled = true
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = {
                        classicFactoryCalled = true
                        FakeTransport("Classic")
                    },
                    coroutineScope = this,
                )

            controller.startSnapshot()
            advanceUntilIdle()

            // Snapshot completed: processChoice frozen, state is SelectedNotStarted
            assertEquals(BluetoothTransportMode.FIDO_BLE, controller.state.value.processChoice)
            assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)

            // Transport factories MUST NOT have been called during snapshot resolution!
            assertEquals(false, bleFactoryCalled)
            assertEquals(false, classicFactoryCalled)
            assertEquals(1, repository.readCount.get())
            assertEquals(1, capabilityChecker.checkCount.get())
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun testServiceFirstCreationWithoutPrematureUse() =
        runTest {
            val repository = FakeSettingsRepository()
            val capabilityChecker = FakeCapabilityChecker()
            var bleFactoryCount = 0

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = capabilityChecker,
                    bleTransportFactory = {
                        bleFactoryCount++
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )

            try {
                // Creating the service or holding the handle before snapshot completes
                val handle: Fido2Transport = controller
                assertEquals(0, bleFactoryCount)
                assertEquals(Fido2ConnectionState.Idle, handle.connectionState.value)

                // Start snapshot
                controller.startSnapshot()
                advanceUntilIdle()
                assertEquals(0, bleFactoryCount)

                // First start request (connect) triggers lazy activation
                val result = handle.connect()
                assertTrue(result.isSuccess)
                assertEquals(1, bleFactoryCount)
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun testNonInitializingCapabilityCheckUnsupportedAndRecoverableStates() =
        runTest {
            // Case 1: Unsupported
            val repository = FakeSettingsRepository()
            val checkerUnsupported = FakeCapabilityChecker(CapabilityCheckResult.Unsupported)
            var bleCreated = false

            val controller1 =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = checkerUnsupported,
                    bleTransportFactory = {
                        bleCreated = true
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller1.startSnapshot()
            advanceUntilIdle()

            assertEquals(
                TransportAvailability.Unavailable(TransportUnavailableReason.Unsupported),
                controller1.state.value.availability,
            )
            assertEquals(false, bleCreated)

            // Attempting to connect when unsupported fails immediately without constructing transport
            val connectResult = controller1.connect()
            assertTrue(connectResult.isFailure)
            assertEquals(false, bleCreated)

            // Case 2: BluetoothOff
            val checkerOff = FakeCapabilityChecker(CapabilityCheckResult.BluetoothOff)
            val controller2 =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = checkerOff,
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller2.startSnapshot()
            advanceUntilIdle()
            assertEquals(
                TransportAvailability.Unavailable(TransportUnavailableReason.BluetoothOff),
                controller2.state.value.availability,
            )

            // Case 3: PermissionRequired
            val checkerPerm = FakeCapabilityChecker(CapabilityCheckResult.PermissionRequired)
            val controller3 =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = checkerPerm,
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller3.startSnapshot()
            advanceUntilIdle()
            assertEquals(
                TransportAvailability.Unavailable(TransportUnavailableReason.PermissionRequired),
                controller3.state.value.availability,
            )
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-020")
    fun testUnknownCapabilityYieldsSelectedNotStarted() =
        runTest {
            val repository = FakeSettingsRepository()
            val checkerUnknown = FakeCapabilityChecker(CapabilityCheckResult.Unknown)

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = checkerUnknown,
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller.startSnapshot()
            advanceUntilIdle()

            // If capability cannot be determined without activation, use SelectedNotStarted
            assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)
        }

    @Test
    @Req("FR-BT-SEL-030", "TR-BT-SEL-020")
    fun testImmutableSelectionOnceFrozen() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    preferenceResult =
                        TransportPreferenceResult.Success(
                            TransportPreference(BluetoothTransportMode.FIDO_BLE, 100L),
                        ),
                )
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller.startSnapshot()
            advanceUntilIdle()

            assertEquals(BluetoothTransportMode.FIDO_BLE, controller.state.value.processChoice)

            // Now preference in repository changes to Classic
            repository.preferenceResult =
                TransportPreferenceResult.Success(
                    TransportPreference(BluetoothTransportMode.CLASSIC_HID, 200L),
                )

            // Process choice MUST NOT change!
            assertEquals(BluetoothTransportMode.FIDO_BLE, controller.state.value.processChoice)
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-020")
    fun testSnapshotTimeoutEnforces5sDeadline() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    readDelayMs = 6000L, // Exceeds 5s deadline
                )
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                    snapshotTimeoutMs = 5000L,
                )

            controller.startSnapshot()
            advanceTimeBy(5001L)
            advanceUntilIdle()

            assertEquals(null, controller.state.value.processChoice)
            assertEquals(
                TransportAvailability.Unavailable(TransportUnavailableReason.InitializationTimeout),
                controller.state.value.availability,
            )
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-020")
    fun testLazyActivationTimeoutEnforces5sDeadline() =
        runTest {
            val repository = FakeSettingsRepository()
            val fakeBle = FakeTransport("BLE", connectDelayMs = 6000L) // Exceeds 5s activation deadline

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { fakeBle },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                    activationTimeoutMs = 5000L,
                )

            try {
                controller.startSnapshot()
                advanceUntilIdle()
                assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)

                val connectJob = async { controller.connect() }
                advanceTimeBy(5001L)
                advanceUntilIdle()

                val result = connectJob.await()
                assertTrue(result.isFailure)
                assertEquals(
                    TransportAvailability.Unavailable(TransportUnavailableReason.InitializationTimeout),
                    controller.state.value.availability,
                )
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun cancelledActivationReleasesResourcesAndCompletesConcurrentWaiters() =
        runTest {
            val repository = FakeSettingsRepository()
            val fakeBle = FakeTransport("BLE", connectDelayMs = 60_000L)
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { fakeBle },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            controller.startSnapshot()
            advanceUntilIdle()

            val owner = async { controller.connect() }
            testScheduler.runCurrent()
            val waiter = async { controller.connect() }
            testScheduler.runCurrent()
            owner.cancelAndJoin()
            advanceUntilIdle()

            assertTrue(waiter.await().isFailure)
            assertEquals(1, fakeBle.disconnectCallCount.get())
            assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun failedActivationCleansPartialTransportBeforeRetry() =
        runTest {
            val repository = FakeSettingsRepository()
            val failed = FakeTransport("first", connectResult = Result.failure(IllegalStateException("startup failed")))
            val recovered = FakeTransport("second")
            val attempts = ArrayDeque(listOf(failed, recovered))
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { attempts.removeFirst() },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )
            try {
                controller.startSnapshot()
                advanceUntilIdle()

                assertTrue(controller.connect().isFailure)
                assertEquals(1, failed.disconnectCallCount.get())
                assertEquals(
                    TransportAvailability.Unavailable(TransportUnavailableReason.StartupFailed),
                    controller.state.value.availability,
                )

                controller.retryActivation()
                assertTrue(controller.connect().isSuccess)
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
                assertEquals(1, recovered.connectCallCount.get())
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-040", "TR-BT-SEL-030")
    fun testConcurrentCallersShareActivationAttempt() =
        runTest {
            val repository = FakeSettingsRepository()
            val fakeBle = FakeTransport("BLE", connectDelayMs = 100L)

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { fakeBle },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )

            try {
                controller.startSnapshot()
                advanceUntilIdle()

                val call1 = async { controller.connect() }
                val call2 = async { controller.connect() }
                val call3 = async { controller.connect() }

                advanceTimeBy(100L)
                advanceUntilIdle()

                val results = listOf(call1.await(), call2.await(), call3.await())
                assertTrue(results.all { it.isSuccess })
                // connect on delegate was invoked exactly once
                assertEquals(1, fakeBle.connectCallCount.get())
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-010", "FR-BT-SEL-040", "TR-BT-SEL-030")
    fun testZeroUnselectedFakeCalls() =
        runTest {
            // BLE selected
            val repositoryBle =
                FakeSettingsRepository(
                    preferenceResult =
                        TransportPreferenceResult.Success(
                            TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L),
                        ),
                )
            var classicFactoryCalled = false
            var bleFactoryCalled = false

            val controllerBle =
                ProcessTransportSelection(
                    settingsRepository = repositoryBle,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = {
                        bleFactoryCalled = true
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = {
                        classicFactoryCalled = true
                        FakeTransport("Classic")
                    },
                    coroutineScope = this,
                )

            try {
                controllerBle.startSnapshot()
                advanceUntilIdle()
                controllerBle.connect()
                advanceUntilIdle()

                assertTrue(bleFactoryCalled)
                assertEquals(false, classicFactoryCalled)
            } finally {
                controllerBle.close()
            }

            // Classic selected
            val repositoryClassic =
                FakeSettingsRepository(
                    preferenceResult =
                        TransportPreferenceResult.Success(
                            TransportPreference(BluetoothTransportMode.CLASSIC_HID, 0L),
                        ),
                )
            classicFactoryCalled = false
            bleFactoryCalled = false

            val controllerClassic =
                ProcessTransportSelection(
                    settingsRepository = repositoryClassic,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = {
                        bleFactoryCalled = true
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = {
                        classicFactoryCalled = true
                        FakeTransport("Classic")
                    },
                    coroutineScope = this,
                )

            try {
                controllerClassic.startSnapshot()
                advanceUntilIdle()
                controllerClassic.connect()
                advanceUntilIdle()

                assertTrue(classicFactoryCalled)
                assertEquals(false, bleFactoryCalled)
            } finally {
                controllerClassic.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-030", "FR-BT-SEL-040", "TR-BT-SEL-020")
    fun reconnectKeepsTheFrozenModeAndARecreatedProcessRestoresTheSavedMode() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    preferenceResult =
                        TransportPreferenceResult.Success(
                            TransportPreference(BluetoothTransportMode.CLASSIC_HID, 1L),
                        ),
                )
            val classic = FakeTransport("Classic")
            var oldBleCreations = 0
            var oldClassicCreations = 0
            val firstProcess =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = {
                        oldBleCreations++
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = {
                        oldClassicCreations++
                        classic
                    },
                    coroutineScope = this,
                )
            try {
                firstProcess.startSnapshot()
                advanceUntilIdle()
                assertTrue(firstProcess.connect().isSuccess)
                assertEquals(TransportAvailability.Ready, firstProcess.state.value.availability)

                repository.preferenceResult =
                    TransportPreferenceResult.Success(
                        TransportPreference(BluetoothTransportMode.FIDO_BLE, 2L),
                    )

                assertTrue(firstProcess.disconnect().isSuccess)
                assertEquals(BluetoothTransportMode.CLASSIC_HID, firstProcess.state.value.processChoice)
                assertEquals(TransportAvailability.SelectedNotStarted, firstProcess.state.value.availability)
                assertTrue(firstProcess.connect().isSuccess)
                assertEquals(BluetoothTransportMode.CLASSIC_HID, firstProcess.state.value.processChoice)
                assertEquals(0, oldBleCreations)
                assertEquals(2, oldClassicCreations)
            } finally {
                firstProcess.close()
            }

            var restoredBleCreations = 0
            var restoredClassicCreations = 0
            val restoredBle = FakeTransport("restored BLE")
            val recreatedProcess =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = {
                        restoredBleCreations++
                        restoredBle
                    },
                    classicTransportFactory = {
                        restoredClassicCreations++
                        FakeTransport("Classic")
                    },
                    coroutineScope = this,
                )
            try {
                recreatedProcess.startSnapshot()
                advanceUntilIdle()
                assertEquals(BluetoothTransportMode.FIDO_BLE, recreatedProcess.state.value.processChoice)
                assertEquals(TransportAvailability.SelectedNotStarted, recreatedProcess.state.value.availability)
                assertTrue(recreatedProcess.connect().isSuccess)
                assertEquals(1, restoredBleCreations)
                assertEquals(0, restoredClassicCreations)
                assertEquals(TransportAvailability.Ready, recreatedProcess.state.value.availability)
            } finally {
                recreatedProcess.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun testFreshBoundedRetryAfterCorrectedCapability() =
        runTest {
            val repository = FakeSettingsRepository()
            val capabilityChecker = FakeCapabilityChecker(CapabilityCheckResult.BluetoothOff)
            val fakeBle = FakeTransport("BLE")

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = capabilityChecker,
                    bleTransportFactory = { fakeBle },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )

            try {
                controller.startSnapshot()
                advanceUntilIdle()
                assertEquals(
                    TransportAvailability.Unavailable(TransportUnavailableReason.BluetoothOff),
                    controller.state.value.availability,
                )

                // User turns on Bluetooth -> capabilityChecker now returns Supported
                capabilityChecker.result = CapabilityCheckResult.Supported

                // Retry activation
                controller.retryActivation()
                assertEquals(TransportAvailability.SelectedNotStarted, controller.state.value.availability)

                // Connect now succeeds under fresh deadline
                val connectResult = controller.connect()
                advanceUntilIdle()
                assertTrue(connectResult.isSuccess)
                assertEquals(TransportAvailability.Ready, controller.state.value.availability)
            } finally {
                controller.close()
            }
        }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun testInvalidPreferenceYieldsUnavailableInvalidPreferenceWithoutFallback() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    preferenceResult = TransportPreferenceResult.InvalidPreference("unknown_custom_code"),
                )
            var bleCalled = false
            var classicCalled = false

            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = {
                        bleCalled = true
                        FakeTransport("BLE")
                    },
                    classicTransportFactory = {
                        classicCalled = true
                        FakeTransport("Classic")
                    },
                    coroutineScope = this,
                )

            controller.startSnapshot()
            advanceUntilIdle()

            assertNull(controller.state.value.processChoice)
            assertEquals(
                TransportAvailability.Unavailable(TransportUnavailableReason.InvalidPreference),
                controller.state.value.availability,
            )

            // Must NOT fall back to BLE or Classic!
            assertEquals(false, bleCalled)
            assertEquals(false, classicCalled)

            val connectResult = controller.connect()
            assertTrue(connectResult.isFailure)
            val outboundResult = controller.connectDevice("AA:BB:CC:DD:EE:FF")
            assertTrue(outboundResult.isFailure)
            assertEquals(false, bleCalled)
            assertEquals(false, classicCalled)
        }

    @Test
    @Req("FR-BLE-080", "FR-BT-SEL-040")
    fun testBleOutboundConnectionRejection() =
        runTest {
            val repository = FakeSettingsRepository()
            val controller =
                ProcessTransportSelection(
                    settingsRepository = repository,
                    capabilityChecker = FakeCapabilityChecker(),
                    bleTransportFactory = { FakeTransport("BLE") },
                    classicTransportFactory = { FakeTransport("Classic") },
                    coroutineScope = this,
                )

            controller.startSnapshot()
            advanceUntilIdle()

            val connectDeviceResult = controller.connectDevice("AA:BB:CC:DD:EE:FF")
            assertTrue(connectDeviceResult.isFailure)
            assertIs<UnsupportedOutboundConnectionException>(connectDeviceResult.exceptionOrNull())
        }
}
