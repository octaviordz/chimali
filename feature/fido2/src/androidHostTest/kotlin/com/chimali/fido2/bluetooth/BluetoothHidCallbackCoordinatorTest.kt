@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.exception.Fido2Exception
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** Verifies host admission and callback ownership using virtual time and Android platform doubles. */
@Req("FR-HID-010", "FR-HID-020")
class BluetoothHidCallbackCoordinatorTest {
    /** A delayed request rechecks both bond state and lifecycle at execution time. */
    @ParameterizedTest
    @CsvSource("true,true,true", "true,false,false", "false,true,false", "false,false,false")
    @Req("FR-HID-010", "FR-HID-020")
    fun `delayed connect requires advertising and a completed bond`(
        advertising: Boolean,
        bonded: Boolean,
        expectedConnect: Boolean,
    ) = runTest {
        val owner = RecordingOwner(this)
        val coordinator = BluetoothHidCallbackCoordinator(owner)
        val host = host()
        coordinator.scheduleProactiveConnect(host, CONNECT_DELAY, "synthetic host")
        runCurrent()
        advanceTimeBy(CONNECT_DELAY.inWholeMilliseconds - 1)
        runCurrent()
        assertTrue(owner.connectAttempts.isEmpty(), "No connect before the settling delay")

        owner.connectionState.value = if (advertising) HidConnectionState.Advertising else HidConnectionState.Idle
        every { host.bondState } returns if (bonded) BluetoothDevice.BOND_BONDED else BluetoothDevice.BOND_NONE
        advanceTimeBy(1)
        runCurrent()

        assertEquals(if (expectedConnect) listOf(host) else emptyList(), owner.connectAttempts)
        assertTrue(owner.acceptedHosts.isEmpty(), "Initiating a connection does not admit a session")
    }

    /** A newer reconnect request owns the timer; direct connection cancels the remaining attempt. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `replacement schedule and direct connection cancel obsolete reconnects`() =
        runTest {
            val owner = RecordingOwner(this)
            val coordinator = BluetoothHidCallbackCoordinator(owner)
            val predecessor = host()
            val successor = host(OTHER_ADDRESS)
            coordinator.scheduleProactiveConnect(predecessor, CONNECT_DELAY, "predecessor")
            runCurrent()
            val predecessorJob = requireNotNull(owner.proactiveConnectJob)
            coordinator.scheduleProactiveConnect(successor, CONNECT_DELAY, "successor")
            runCurrent()
            assertTrue(predecessorJob.isCancelled)

            coordinator.hidCallback.onConnectionStateChanged(successor, BluetoothProfile.STATE_CONNECTED)
            advanceTimeBy(CONNECT_DELAY)
            runCurrent()

            assertNull(owner.proactiveConnectJob)
            assertEquals(listOf(successor), owner.acceptedHosts)
            assertTrue(owner.connectAttempts.isEmpty(), "A direct connection must not trigger another delayed connect")
        }

    /** ACL events must be both eligible hosts and bonded while the app is advertising. */
    @ParameterizedTest
    @CsvSource(
        "computer,bonded,true,true",
        "unknown,bonded,true,true",
        "audio,bonded,true,false",
        "wearable,bonded,true,false",
        "computer,none,true,false",
        "computer,bonding,true,false",
        "computer,bonded,false,false",
    )
    @Req("FR-HID-010", "FR-HID-020")
    fun `ACL connection filters host category bond and lifecycle`(
        category: String,
        bond: String,
        advertising: Boolean,
        expectedConnect: Boolean,
    ) = runTest {
        val owner = RecordingOwner(this)
        owner.connectionState.value = if (advertising) HidConnectionState.Advertising else HidConnectionState.Idle
        val coordinator = BluetoothHidCallbackCoordinator(owner)
        val host = host()
        every { host.bluetoothClass } returns
            when (category) {
                "unknown" -> null
                else ->
                    mockk<BluetoothClass> {
                        every { majorDeviceClass } returns
                            when (category) {
                                "audio" -> BluetoothClass.Device.Major.AUDIO_VIDEO
                                "wearable" -> BluetoothClass.Device.Major.WEARABLE
                                else -> BluetoothClass.Device.Major.COMPUTER
                            }
                    }
            }
        every { host.bondState } returns
            when (bond) {
                "bonded" -> BluetoothDevice.BOND_BONDED
                "bonding" -> BluetoothDevice.BOND_BONDING
                else -> BluetoothDevice.BOND_NONE
            }

        coordinator.bluetoothStateReceiver.onReceive(context, intent(BluetoothDevice.ACTION_ACL_CONNECTED, host))
        runCurrent()
        assertTrue(owner.connectAttempts.isEmpty(), "ACL discovery still requires the settling delay")
        advanceTimeBy(BluetoothHidConfigProvider.config.aclConnectDelay)
        runCurrent()

        assertEquals(if (expectedConnect) listOf(host) else emptyList(), owner.connectAttempts)
        assertTrue(owner.acceptedHosts.isEmpty())
    }

    /** Missing device extras and unrelated actions cannot schedule or admit a host. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `broadcasts without a device or recognized action preserve session ownership`() =
        runTest {
            val owner = RecordingOwner(this)
            val coordinator = BluetoothHidCallbackCoordinator(owner)
            val actions =
                listOf(
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                    null,
                )
            for (action in actions) {
                coordinator.bluetoothStateReceiver.onReceive(context, intent(action, null))
            }
            runCurrent()
            assertNull(owner.proactiveConnectJob)
            assertTrue(owner.connectAttempts.isEmpty())
            assertTrue(owner.acceptedHosts.isEmpty())
            assertEquals(0, owner.generation)
        }

    /** Bonding and unrelated bond completions cannot authorize the pending host. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `only matching completed bond admits a deferred host`() =
        runTest {
            val owner = RecordingOwner(this)
            val coordinator = BluetoothHidCallbackCoordinator(owner)
            val pending = host()
            val unrelated = host(OTHER_ADDRESS)
            owner.pendingBondDevice = pending
            coordinator.bluetoothStateReceiver.onReceive(context, bondIntent(unrelated, BluetoothDevice.BOND_BONDED))
            coordinator.bluetoothStateReceiver.onReceive(context, bondIntent(pending, BluetoothDevice.BOND_BONDING))
            assertTrue(owner.acceptedHosts.isEmpty())
            assertSame(pending, owner.pendingBondDevice)
            assertNull(owner.proactiveConnectJob)

            coordinator.bluetoothStateReceiver.onReceive(context, bondIntent(pending, BluetoothDevice.BOND_BONDED))

            assertEquals(listOf(pending), owner.acceptedHosts)
            assertNull(owner.pendingBondDevice)
            assertTrue(owner.connectAttempts.isEmpty())
        }

    /** Newly bonded devices initiate a delayed request only if advertising with no pending host. */
    @ParameterizedTest
    @CsvSource("true,12,true", "false,12,false", "true,10,false", "true,11,false")
    @Req("FR-HID-010", "FR-HID-020")
    fun `bond broadcast without pending host schedules only eligible reconnect`(
        advertising: Boolean,
        bondState: Int,
        expectedConnect: Boolean,
    ) = runTest {
        val owner = RecordingOwner(this)
        owner.connectionState.value = if (advertising) HidConnectionState.Advertising else HidConnectionState.Idle
        val coordinator = BluetoothHidCallbackCoordinator(owner)
        val host = host()
        coordinator.bluetoothStateReceiver.onReceive(context, bondIntent(host, bondState))
        runCurrent()
        assertTrue(owner.connectAttempts.isEmpty())
        advanceTimeBy(BluetoothHidConfigProvider.config.bondConnectDelay)
        runCurrent()
        assertEquals(if (expectedConnect) listOf(host) else emptyList(), owner.connectAttempts)
        assertTrue(owner.acceptedHosts.isEmpty())
    }

    /** Permission failures must never turn a host with unreadable trust state into an admitted session. */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Req("FR-HID-010", "FR-HID-020")
    fun `unreadable bond rejects admission even when platform disconnect fails`(disconnectFails: Boolean) =
        runTest {
            val owner = RecordingOwner(this)
            val hid = mockk<BluetoothHidDevice>(relaxed = true)
            owner.hidDevice = hid
            val host = host()
            every { host.bondState } throws SecurityException("synthetic permission loss")
            if (disconnectFails) every { hid.disconnect(host) } throws SecurityException("synthetic disconnect denial")

            BluetoothHidCallbackCoordinator(owner).hidCallback.onConnectionStateChanged(
                host,
                BluetoothProfile.STATE_CONNECTED,
            )

            verify(exactly = 1) { hid.disconnect(host) }
            assertTrue(owner.acceptedHosts.isEmpty())
            assertNull(owner.connectedDevice)
            assertSame(HidConnectionState.Advertising, owner.connectionState.value)
        }

    /** An unrelated profile cannot finish the active HID proxy initialization. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `only HID proxy acquisition completes and clears initialization`() =
        runTest {
            val owner = RecordingOwner(this)
            val coordinator = BluetoothHidCallbackCoordinator(owner)
            val hid = mockk<BluetoothHidDevice>(relaxed = true)
            val completion =
                async(start = CoroutineStart.UNDISPATCHED) {
                    suspendCancellableCoroutine { coordinator.initContinuation = it }
                }

            coordinator.serviceListener.onServiceConnected(BluetoothProfile.HEADSET, hid)
            assertFalse(completion.isCompleted)
            assertNull(owner.hidDevice)
            coordinator.serviceListener.onServiceConnected(BluetoothProfile.HID_DEVICE, hid)

            assertTrue(completion.await().isSuccess)
            assertSame(hid, owner.hidDevice)
            assertNull(coordinator.initContinuation)
            coordinator.serviceListener.onServiceConnected(BluetoothProfile.HID_DEVICE, hid)
            assertNull(coordinator.initContinuation, "Repeated proxy callback must not resume a completed attempt")
        }

    /** Late Android callbacks may clear a canceled attempt but cannot resume it. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `late proxy callback clears canceled initialization without resuming it`() =
        runTest {
            val owner = RecordingOwner(this)
            val coordinator = BluetoothHidCallbackCoordinator(owner)
            val completion =
                async(start = CoroutineStart.UNDISPATCHED) {
                    suspendCancellableCoroutine { coordinator.initContinuation = it }
                }
            completion.cancel()
            coordinator.serviceListener.onServiceConnected(
                BluetoothProfile.HID_DEVICE,
                mockk<BluetoothHidDevice>(relaxed = true),
            )

            assertTrue(completion.isCancelled)
            assertNull(coordinator.initContinuation)
        }

    /** Registration reports one typed outcome and tolerates repeated or canceled callbacks. */
    @ParameterizedTest
    @CsvSource("true,false", "false,false", "true,true", "false,true")
    @Req("FR-HID-010", "FR-HID-020")
    fun `registration callback respects confirmation failure and canceled ownership`(
        registered: Boolean,
        canceled: Boolean,
    ) = runTest {
        val owner = RecordingOwner(this)
        val coordinator = BluetoothHidCallbackCoordinator(owner)
        lateinit var callback: BluetoothHidDevice.Callback
        val completion =
            async(start = CoroutineStart.UNDISPATCHED) {
                suspendCancellableCoroutine<Result<Unit>> { callback = coordinator.createRegistrationCallback(it) }
            }
        if (canceled) completion.cancel()
        callback.onAppStatusChanged(null, registered)
        callback.onAppStatusChanged(null, registered)

        if (canceled) {
            assertTrue(completion.isCancelled)
        } else if (registered) {
            assertTrue(completion.await().isSuccess)
        } else {
            assertIs<Fido2Exception.BluetoothException>(completion.await().exceptionOrNull())
        }
        assertSame(
            if (registered) HidConnectionState.Advertising else HidConnectionState.Idle,
            owner.connectionState.value,
        )
        assertTrue(owner.acceptedHosts.isEmpty())
    }

    /** Previously bonded peripherals and hosts with no confirmed ACL link must not initiate HID sessions. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `advertising discovery filters peripherals and unconfirmed host links`() =
        runTest {
            val owner = RecordingOwner(this)
            val audio = host()
            val wearable = host(OTHER_ADDRESS)
            val computer = host("11:22:33:44:55:66")
            val devices = listOf(audio, wearable, computer)
            val categories =
                listOf(
                    BluetoothClass.Device.Major.AUDIO_VIDEO,
                    BluetoothClass.Device.Major.WEARABLE,
                    BluetoothClass.Device.Major.COMPUTER,
                )
            devices.zip(categories).forEach { (device, category) ->
                every { device.bluetoothClass } returns
                    mockk {
                        every { majorDeviceClass } returns category
                    }
            }
            val adapter =
                mockk<BluetoothAdapter> {
                    every { bondedDevices } returns devices.toSet()
                }
            owner.bluetoothAdapter = adapter

            BluetoothHidCallbackCoordinator(owner).checkExistingConnectionsWhileAdvertising()
            runCurrent()

            verify(exactly = 1) { adapter.bondedDevices }
            assertNull(owner.proactiveConnectJob)
            assertTrue(owner.connectAttempts.isEmpty())
            assertTrue(owner.acceptedHosts.isEmpty())
        }

    /** Discovery outside advertising cannot inspect bonded hosts or replace a live session. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `existing connection scan is suppressed outside advertising`() =
        runTest {
            val owner = RecordingOwner(this)
            val adapter = mockk<BluetoothAdapter>()
            owner.bluetoothAdapter = adapter
            owner.connectionState.value = HidConnectionState.Connected(host())

            BluetoothHidCallbackCoordinator(owner).checkExistingConnectionsWhileAdvertising()

            verify(exactly = 0) { adapter.bondedDevices }
            assertTrue(owner.connectAttempts.isEmpty())
            assertNull(owner.proactiveConnectJob)
        }

    /** A stale plugged-host hint must be disconnected without being admitted as the active host. */
    @ParameterizedTest
    @CsvSource("true,false", "true,true", "false,false")
    @Req("FR-HID-010", "FR-HID-020")
    fun `registration plugged host is not admitted even if stale disconnect fails`(
        proxyAvailable: Boolean,
        disconnectDenied: Boolean,
    ) = runTest {
        assertTrue(BluetoothHidConfigProvider.config.isPhantomDisconnectRequired)
        val owner = RecordingOwner(this)
        val hid = mockk<BluetoothHidDevice>(relaxed = true)
        val plugged = host()
        if (proxyAvailable) owner.hidDevice = hid
        if (disconnectDenied) every { hid.disconnect(plugged) } throws SecurityException("synthetic permission loss")

        BluetoothHidCallbackCoordinator(owner).hidCallback.onAppStatusChanged(plugged, true)

        verify(exactly = if (proxyAvailable) 1 else 0) { hid.disconnect(plugged) }
        assertSame(HidConnectionState.Advertising, owner.connectionState.value)
        assertNull(owner.connectedDevice)
        assertTrue(owner.acceptedHosts.isEmpty())
    }

    /** GET_REPORT returns the fixed wire report and tolerates a missing proxy or permission loss. */
    @ParameterizedTest
    @ValueSource(strings = ["available", "missing", "denied"])
    @Req("FR-HID-010", "FR-HID-020")
    fun `report readback preserves the session on missing or denied driver`(mode: String) =
        runTest {
            val owner = RecordingOwner(this)
            val host = host()
            val hid = mockk<BluetoothHidDevice>()
            val connected = HidConnectionState.Connected(host)
            owner.connectedDevice = host
            owner.connectionState.value = connected
            if (mode != "missing") owner.hidDevice = hid
            every { hid.replyReport(host, BluetoothHidDevice.REPORT_TYPE_INPUT, 0, any()) } answers {
                assertContentEquals(ByteArray(REPORT_SIZE), arg<ByteArray>(3))
                if (mode == "denied") throw SecurityException("synthetic permission loss")
                true
            }

            BluetoothHidCallbackCoordinator(owner).hidCallback.onGetReport(
                host,
                BluetoothHidDevice.REPORT_TYPE_INPUT,
                0,
                REPORT_SIZE,
            )

            verify(exactly = if (mode == "missing") 0 else 1) {
                hid.replyReport(host, BluetoothHidDevice.REPORT_TYPE_INPUT, 0, any())
            }
            assertSame(host, owner.connectedDevice)
            assertSame(connected, owner.connectionState.value)
            assertEquals(0, owner.generation)
        }

    private fun host(address: String = HOST_ADDRESS): BluetoothDevice =
        mockk(relaxed = true) {
            every { this@mockk.address } returns address
            every { bondState } returns BluetoothDevice.BOND_BONDED
        }

    private fun intent(
        action: String?,
        device: BluetoothDevice?,
    ): Intent =
        mockk {
            every { this@mockk.action } returns action
            @Suppress("DEPRECATION")
            every { getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns device
        }

    private fun bondIntent(
        device: BluetoothDevice,
        bondState: Int,
    ): Intent =
        intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED, device).also {
            every { it.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE) } returns bondState
            every { it.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE) } returns
                BluetoothDevice.BOND_BONDING
        }

    private class RecordingOwner(
        override val scope: CoroutineScope,
    ) : BluetoothHidCallbackOwner {
        override var hidDevice: BluetoothHidDevice? = null
        override var connectedDevice: BluetoothDevice? = null
        override var pendingBondDevice: BluetoothDevice? = null
        override var proactiveConnectJob: Job? = null
        override var bluetoothAdapter: BluetoothAdapter? = null
        override val sessionLock = Any()
        override val connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Advertising)
        val connectAttempts = mutableListOf<BluetoothDevice>()
        val acceptedHosts = mutableListOf<BluetoothDevice>()
        var generation = 0

        override fun advanceSessionGeneration() {
            generation++
        }

        override fun discardQueuedIncomingReports() = Unit

        override fun publishSessionState(state: HidConnectionState) {
            connectionState.value = state
        }

        override fun acceptConnectedDevice(device: BluetoothDevice) {
            acceptedHosts += device
        }

        override fun connect(device: BluetoothDevice): Boolean {
            connectAttempts += device
            return true
        }

        override fun enqueueIncomingReportFromDevice(
            device: BluetoothDevice,
            data: ByteArray,
        ) = Unit
    }

    private companion object {
        const val HOST_ADDRESS = "00:11:22:33:44:55"
        const val OTHER_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val REPORT_SIZE = 62
        val CONNECT_DELAY = 2.seconds
        val context: Context = mockk()
    }
}
