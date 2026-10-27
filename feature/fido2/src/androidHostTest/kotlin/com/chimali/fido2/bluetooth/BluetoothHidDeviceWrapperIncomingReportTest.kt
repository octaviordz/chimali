package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** @see NFR-SEC-041 and TR-AUTH-031 */
class BluetoothHidDeviceWrapperIncomingReportTest {
    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `callback admission copies borrowed bytes bounds queue and erases undelivered owners`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val ownedReports = mutableListOf<ByteArray>()
        wrapper.onIncomingReportOwnedForTest = { ownedReports += it }

        repeat(INCOMING_REPORT_CAPACITY + 1) { value ->
            val expectedByte = (value + 1).toByte()
            val borrowed = ByteArray(REPORT_SIZE) { expectedByte }
            wrapper.enqueueIncomingReport(borrowed)
            assertTrue(borrowed.all { it == expectedByte }, "Platform callback data remains borrowed")
            assertFalse(ownedReports.last() === borrowed, "The queue receives an owned copy")
        }

        val queuedReports =
            List(INCOMING_REPORT_CAPACITY) {
                wrapper.incomingReports
                    .tryReceive()
                    .getOrThrow()
                    .bytes
            }
        assertEquals(INCOMING_REPORT_CAPACITY, queuedReports.size)
        assertTrue(ownedReports.last().all { it == 0.toByte() }, "Rejected queue copy is erased")
        assertTrue(queuedReports.all { it.any { byte -> byte != 0.toByte() } })
        queuedReports.forEach { it.fill(0) }

        wrapper.enqueueIncomingReport(ByteArray(REPORT_SIZE) { 0x22 })
        val queuedForDiscard = ownedReports.last()
        wrapper.discardQueuedIncomingReports()
        assertTrue(queuedForDiscard.all { it == 0.toByte() }, "Session discard erases queued owners")

        val borrowedSuccessor = ByteArray(REPORT_SIZE) { 0x5A }
        wrapper.enqueueIncomingReport(borrowedSuccessor)
        val queuedSuccessor =
            wrapper.incomingReports
                .tryReceive()
                .getOrThrow()
                .bytes
        assertContentEquals(borrowedSuccessor, queuedSuccessor)
        assertFalse(queuedSuccessor === borrowedSuccessor)

        val pendingAtClose = ByteArray(REPORT_SIZE) { 0x33 }
        wrapper.enqueueIncomingReport(pendingAtClose)
        val pendingOwner = ownedReports.last()
        wrapper.incomingReports.cancel()
        assertTrue(pendingOwner.all { it == 0.toByte() }, "Closing the receiver erases buffered owners")
        queuedSuccessor.fill(0)
    }

    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `truncated and oversized callback reports are rejected without fabricated padding`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val admitted = mutableListOf<ByteArray>()
        wrapper.onIncomingReportOwnedForTest = { admitted += it }
        wrapper.enqueueIncomingReport(ByteArray(REPORT_SIZE - 1) { 0x31 })
        wrapper.enqueueIncomingReport(ByteArray(REPORT_SIZE + 1) { 0x32 })

        assertTrue(admitted.isEmpty())
        assertTrue(wrapper.incomingReports.tryReceive().isFailure)
    }

    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `incoming report admission rejects another host and tags active session generation`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val activeDevice = mockk<BluetoothDevice>()
        val staleDevice = mockk<BluetoothDevice>()
        every { activeDevice.address } returns "00:11:22:33:44:55"
        every { staleDevice.address } returns "AA:BB:CC:DD:EE:FF"
        setPrivateField(wrapper, "connectedDevice", activeDevice)
        setConnectionState(wrapper, HidConnectionState.Connected(activeDevice))

        val staleBytes = ByteArray(REPORT_SIZE) { 0x12 }
        wrapper.enqueueIncomingReportFromDevice(staleDevice, staleBytes)
        assertTrue(wrapper.incomingReports.tryReceive().isFailure)

        val currentBytes = ByteArray(REPORT_SIZE) { 0x34 }
        wrapper.enqueueIncomingReportFromDevice(activeDevice, currentBytes)
        val queued = wrapper.incomingReports.tryReceive().getOrThrow()
        assertContentEquals(currentBytes, queued.bytes)
        assertTrue(currentBytes.all { it == 0x34.toByte() })
        wrapper.advanceSessionGenerationForTest()
        assertNotEquals(wrapper.sessionGeneration(), queued.sessionGeneration)
        queued.bytes.fill(0)
    }

    /** @see NFR-SEC-041 and FR-HID-020 */
    @Test
    @Req("NFR-SEC-041", "FR-HID-020")
    fun `never-bonded host is disconnected without becoming the active session`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val hidDevice = mockk<BluetoothHidDevice>(relaxed = true)
        val host = mockk<BluetoothDevice>()
        every { host.address } returns "00:11:22:33:44:55"
        every { host.bondState } returns BluetoothDevice.BOND_NONE
        setPrivateField(wrapper, "hidDevice", hidDevice)
        setConnectionState(wrapper, HidConnectionState.Advertising)
        val callback = getPrivateField(wrapper, "hidCallback") as BluetoothHidDevice.Callback

        callback.onConnectionStateChanged(host, BluetoothProfile.STATE_CONNECTED)

        verify(exactly = 1) { hidDevice.disconnect(host) }
        assertTrue(wrapper.connectionState.value is HidConnectionState.Advertising)
        assertTrue(getPrivateField(wrapper, "connectedDevice") == null)
        assertTrue(getPrivateField(wrapper, "pendingBondDevice") == null)
    }

    /** @see NFR-SEC-041 and FR-HID-020 */
    @Test
    @Req("NFR-SEC-041", "FR-HID-020")
    fun `host connecting during bond is deferred and cleared if it disconnects`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val host = mockk<BluetoothDevice>()
        every { host.address } returns "00:11:22:33:44:55"
        every { host.bondState } returns BluetoothDevice.BOND_BONDING
        setConnectionState(wrapper, HidConnectionState.Advertising)
        val callback = getPrivateField(wrapper, "hidCallback") as BluetoothHidDevice.Callback

        callback.onConnectionStateChanged(host, BluetoothProfile.STATE_CONNECTED)

        assertTrue(wrapper.connectionState.value is HidConnectionState.Advertising)
        assertTrue(getPrivateField(wrapper, "connectedDevice") == null)
        assertTrue(getPrivateField(wrapper, "pendingBondDevice") === host)

        callback.onConnectionStateChanged(host, BluetoothProfile.STATE_DISCONNECTED)

        assertTrue(wrapper.connectionState.value is HidConnectionState.Advertising)
        assertTrue(getPrivateField(wrapper, "connectedDevice") == null)
        assertTrue(getPrivateField(wrapper, "pendingBondDevice") == null)
    }

    /** @see NFR-SEC-041 and FR-HID-020 */
    @Test
    @Req("NFR-SEC-041", "FR-HID-020")
    fun `active host disconnect advances generation and erases queued reports`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val activeHost = mockk<BluetoothDevice>()
        val otherHost = mockk<BluetoothDevice>()
        every { activeHost.address } returns "00:11:22:33:44:55"
        every { otherHost.address } returns "AA:BB:CC:DD:EE:FF"
        setPrivateField(wrapper, "connectedDevice", activeHost)
        setConnectionState(wrapper, HidConnectionState.Connected(activeHost))
        val ownedReports = mutableListOf<ByteArray>()
        wrapper.onIncomingReportOwnedForTest = { ownedReports += it }
        val report = ByteArray(REPORT_SIZE) { 0x39 }
        wrapper.enqueueIncomingReport(report)
        val queuedOwner = ownedReports.single()
        val generation = wrapper.sessionGeneration()
        val callback = getPrivateField(wrapper, "hidCallback") as BluetoothHidDevice.Callback

        callback.onConnectionStateChanged(otherHost, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(generation, wrapper.sessionGeneration())
        assertTrue(wrapper.connectionState.value is HidConnectionState.Connected)

        callback.onConnectionStateChanged(activeHost, BluetoothProfile.STATE_DISCONNECTED)

        assertEquals(generation + 1, wrapper.sessionGeneration())
        assertTrue(wrapper.connectionState.value is HidConnectionState.Advertising)
        assertTrue(getPrivateField(wrapper, "connectedDevice") == null)
        assertTrue(queuedOwner.all { it == 0.toByte() })
        assertTrue(wrapper.incomingReports.tryReceive().isFailure)
        assertTrue(report.all { it == 0x39.toByte() })
    }

    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `outgoing platform report copy is erased after success rejection and driver failure`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val hidDevice = mockk<BluetoothHidDevice>()
        val connectedDevice = mockk<BluetoothDevice>()
        setPrivateField(wrapper, "hidDevice", hidDevice)
        setPrivateField(wrapper, "connectedDevice", connectedDevice)

        val driverReports = mutableListOf<ByteArray>()
        val reportSlot = slot<ByteArray>()
        var outcome = "success"
        io.mockk.every {
            hidDevice.sendReport(connectedDevice, 0, capture(reportSlot))
        } answers {
            val report = reportSlot.captured
            driverReports += report
            assertContentEquals(expectedReport, report)
            when (outcome) {
                "success" -> true
                "rejected" -> false
                else -> throw SecurityException("synthetic Bluetooth permission failure")
            }
        }

        for (currentOutcome in listOf("success", "rejected", "failure")) {
            outcome = currentOutcome
            val callerOwned = ByteArray(REPORT_SIZE) { 0x5A }
            assertEquals(currentOutcome == "success", wrapper.sendReport(callerOwned))
            assertContentEquals(ByteArray(REPORT_SIZE) { 0x5A }, callerOwned)
            assertTrue(
                driverReports.last().all { it == 0.toByte() },
                "The report copy must be erased after the platform call: $currentOutcome",
            )
        }
    }

    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `outgoing platform report copy is erased when Bluetooth send throws`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val hidDevice = mockk<BluetoothHidDevice>()
        setPrivateField(wrapper, "hidDevice", hidDevice)
        setPrivateField(wrapper, "connectedDevice", mockk<BluetoothDevice>())
        val reportSlot = slot<ByteArray>()
        io.mockk.every { hidDevice.sendReport(any(), any(), capture(reportSlot)) } throws
            IllegalStateException("synthetic Bluetooth stack failure")

        val callerOwned = ByteArray(REPORT_SIZE) { 0x33 }
        assertFalse(wrapper.sendReport(callerOwned))
        assertTrue(reportSlot.captured.all { it == 0.toByte() })
        assertTrue(callerOwned.all { it == 0x33.toByte() })
    }

    /**
     * @see FR-HID-010
     * @see FR-HID-020
     */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `outgoing stale session report is rejected before reaching Bluetooth`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val hidDevice = mockk<BluetoothHidDevice>()
        setPrivateField(wrapper, "hidDevice", hidDevice)
        setPrivateField(wrapper, "connectedDevice", mockk<BluetoothDevice>())

        assertFalse(wrapper.sendReport(ByteArray(REPORT_SIZE), expectedSessionGeneration = 1L))
        verify(exactly = 0) { hidDevice.sendReport(any(), any(), any()) }
    }

    /** Real wrapper session transitions erase abandoned queue owners before notifying the transport. */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "adapter-off", "profile-lost", "registration-lost", "registration-renewed", "unplug",
            "unregister", "close", "error",
        ],
    )
    @Req("FR-HID-010", "FR-HID-020")
    fun `callback session reset fences reports before notifying transport`(event: String) {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val host = bondedHost()
        setPrivateField(wrapper, "connectedDevice", host)
        setConnectionState(wrapper, HidConnectionState.Connected(host))
        val borrowed = ByteArray(REPORT_SIZE) { 0x41 }
        lateinit var owned: ByteArray
        wrapper.onIncomingReportOwnedForTest = { owned = it }
        wrapper.enqueueIncomingReportFromDevice(host, borrowed)
        val originalGeneration = wrapper.sessionGeneration()
        var notifications = 0
        wrapper.onSessionTransition = { generation, _ ->
            notifications++
            assertEquals(originalGeneration + 1, generation)
            assertTrue(owned.all { it == 0.toByte() }, "Erase before synchronous transport notification")
            assertNull(getPrivateField(wrapper, "connectedDevice"))
        }

        resetCallbackSession(wrapper, host, event)

        assertEquals(1, notifications)
        assertEquals(originalGeneration + 1, wrapper.sessionGeneration())
        assertEquals(
            when (event) {
                "unplug", "registration-renewed" -> HidConnectionState.Advertising
                "error" -> HidConnectionState.Error(SYNTHETIC_ERROR)
                else -> HidConnectionState.Idle
            },
            wrapper.connectionState.value,
        )
        assertTrue(wrapper.incomingReports.tryReceive().isFailure)
        val rejectedBorrowed = ByteArray(REPORT_SIZE) { 0x42 }
        wrapper.enqueueIncomingReportFromDevice(host, rejectedBorrowed)
        assertTrue(wrapper.incomingReports.tryReceive().isFailure, "Reset host cannot enqueue into a later session")
        assertContentEquals(ByteArray(REPORT_SIZE) { 0x41 }, borrowed)
        assertContentEquals(ByteArray(REPORT_SIZE) { 0x42 }, rejectedBorrowed)
    }

    /** A canceled pending bond must not resurrect a session after its platform lifetime ended. */
    @ParameterizedTest
    @ValueSource(strings = ["adapter-off", "profile-lost", "registration-lost", "unregister", "close", "error"])
    @Req("FR-HID-010", "FR-HID-020")
    fun `late bond completion after callback session loss cannot admit a host`(event: String) {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val host = bondedHost()
        every { host.bondState } returns BluetoothDevice.BOND_BONDING
        setConnectionState(wrapper, HidConnectionState.Advertising)
        val coordinator = getPrivateField(wrapper, "callbackCoordinator") as BluetoothHidCallbackCoordinator
        coordinator.hidCallback.onConnectionStateChanged(host, BluetoothProfile.STATE_CONNECTED)
        assertSame(host, getPrivateField(wrapper, "pendingBondDevice"))

        resetCallbackSession(wrapper, host, event)
        val endedGeneration = wrapper.sessionGeneration()
        val endedState = wrapper.connectionState.value
        every { host.bondState } returns BluetoothDevice.BOND_BONDED
        val completedBond =
            mockk<Intent> {
                every { action } returns BluetoothDevice.ACTION_BOND_STATE_CHANGED
                @Suppress("DEPRECATION")
                every { getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns host
                every { getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE) } returns
                    BluetoothDevice.BOND_BONDED
                every { getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE) } returns
                    BluetoothDevice.BOND_BONDING
            }
        coordinator.bluetoothStateReceiver.onReceive(mockk(), completedBond)

        assertSame(endedState, wrapper.connectionState.value)
        assertNull(getPrivateField(wrapper, "pendingBondDevice"))
        assertNull(getPrivateField(wrapper, "connectedDevice"))
        assertEquals(endedGeneration, wrapper.sessionGeneration(), "Late bond cannot open a new generation")
    }

    /** Virtual cable events from another host preserve the admitted host and its queued report. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `unplug from another host preserves active report ownership`() {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val active = bondedHost()
        val other = bondedHost("AA:BB:CC:DD:EE:FF")
        setPrivateField(wrapper, "connectedDevice", active)
        setConnectionState(wrapper, HidConnectionState.Connected(active))
        val borrowed = ByteArray(REPORT_SIZE) { 0x43 }
        wrapper.enqueueIncomingReportFromDevice(active, borrowed)
        val generation = wrapper.sessionGeneration()

        val callback = getPrivateField(wrapper, "hidCallback") as BluetoothHidDevice.Callback
        callback.onVirtualCableUnplug(other)

        assertEquals(generation, wrapper.sessionGeneration())
        assertSame(active, getPrivateField(wrapper, "connectedDevice"))
        val queued = wrapper.incomingReports.tryReceive().getOrThrow()
        assertContentEquals(borrowed, queued.bytes)
        assertEquals(generation, queued.sessionGeneration)
        queued.bytes.fill(0)
    }

    /** Android report callbacks must use the same active-host admission and copy boundary. */
    @ParameterizedTest
    @ValueSource(strings = ["set", "interrupt"])
    @Req("FR-HID-010", "FR-HID-020")
    fun `platform report callback admits only active host and borrows input`(kind: String) {
        val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
        val active = bondedHost()
        val other = bondedHost("AA:BB:CC:DD:EE:FF")
        setPrivateField(wrapper, "connectedDevice", active)
        setConnectionState(wrapper, HidConnectionState.Connected(active))
        val callback = getPrivateField(wrapper, "hidCallback") as BluetoothHidDevice.Callback
        val borrowed = ByteArray(REPORT_SIZE) { 0x44 }
        for (host in listOf(other, active)) {
            if (kind == "set") {
                callback.onSetReport(host, BluetoothHidDevice.REPORT_TYPE_OUTPUT, 0, borrowed)
            } else {
                callback.onInterruptData(host, 0, borrowed)
            }
            if (host === other) assertTrue(wrapper.incomingReports.tryReceive().isFailure)
        }
        val queued = wrapper.incomingReports.tryReceive().getOrThrow()
        assertFalse(queued.bytes === borrowed)
        assertContentEquals(ByteArray(REPORT_SIZE) { 0x44 }, queued.bytes)
        assertEquals(wrapper.sessionGeneration(), queued.sessionGeneration)
        queued.bytes.fill(0)
        assertContentEquals(ByteArray(REPORT_SIZE) { 0x44 }, borrowed)
    }

    /** The callback actually registered with Android must preserve wrapper admission and queue ownership. */
    @Test
    @Req("FR-HID-010", "FR-HID-020")
    fun `registration callback forwards connection reports and unplug through the real wrapper`() =
        runTest {
            val wrapper = BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
            val coordinator = getPrivateField(wrapper, "callbackCoordinator") as BluetoothHidCallbackCoordinator
            val hid = mockk<BluetoothHidDevice>(relaxed = true)
            val host = bondedHost()
            val other = bondedHost("AA:BB:CC:DD:EE:FF")
            setPrivateField(wrapper, "hidDevice", hid)
            lateinit var callback: BluetoothHidDevice.Callback
            val confirmation =
                async(start = CoroutineStart.UNDISPATCHED) {
                    suspendCancellableCoroutine<Result<Unit>> { callback = coordinator.createRegistrationCallback(it) }
                }
            callback.onAppStatusChanged(null, true)
            assertTrue(confirmation.await().isSuccess)

            callback.onConnectionStateChanged(host, BluetoothProfile.STATE_CONNECTING)
            assertEquals(HidConnectionState.Connecting(host), wrapper.connectionState.value)
            assertNull(getPrivateField(wrapper, "connectedDevice"))
            callback.onConnectionStateChanged(host, BluetoothProfile.STATE_CONNECTED)
            val generation = wrapper.sessionGeneration()
            assertSame(host, getPrivateField(wrapper, "connectedDevice"))
            coordinator.serviceListener.onServiceDisconnected(BluetoothProfile.HEADSET)
            assertEquals(generation, wrapper.sessionGeneration(), "An unrelated profile cannot end the HID session")
            assertSame(host, getPrivateField(wrapper, "connectedDevice"))

            val ownedReports = mutableListOf<ByteArray>()
            wrapper.onIncomingReportOwnedForTest = { ownedReports += it }
            val borrowed = ByteArray(REPORT_SIZE) { 0x45 }
            callback.onSetReport(host, BluetoothHidDevice.REPORT_TYPE_OUTPUT, 0, borrowed)
            callback.onInterruptData(host, 0, borrowed)
            callback.onInterruptData(other, 0, borrowed)
            assertEquals(2, ownedReports.size)
            ownedReports.forEach {
                assertFalse(it === borrowed)
                assertContentEquals(ByteArray(REPORT_SIZE) { 0x45 }, it)
            }
            callback.onGetReport(host, BluetoothHidDevice.REPORT_TYPE_INPUT, 0, REPORT_SIZE)
            verify(exactly = 1) {
                hid.replyReport(
                    host,
                    BluetoothHidDevice.REPORT_TYPE_INPUT,
                    0,
                    match { it.contentEquals(ByteArray(REPORT_SIZE)) },
                )
            }

            callback.onVirtualCableUnplug(other)
            assertEquals(generation, wrapper.sessionGeneration())
            callback.onVirtualCableUnplug(host)
            assertEquals(generation + 1, wrapper.sessionGeneration())
            assertNull(getPrivateField(wrapper, "connectedDevice"))
            assertTrue(ownedReports.all { report -> report.all { it == 0.toByte() } })
            assertTrue(wrapper.incomingReports.tryReceive().isFailure)
            callback.onVirtualCableUnplug(host)
            callback.onConnectionStateChanged(host, BluetoothProfile.STATE_DISCONNECTING)
            callback.onConnectionStateChanged(host, BluetoothProfile.STATE_DISCONNECTED)
            assertSame(HidConnectionState.Advertising, wrapper.connectionState.value)
            assertEquals(generation + 1, wrapper.sessionGeneration())
            assertContentEquals(ByteArray(REPORT_SIZE) { 0x45 }, borrowed)
        }

    private fun bondedHost(address: String = "00:11:22:33:44:55"): BluetoothDevice =
        mockk(relaxed = true) {
            every { this@mockk.address } returns address
            every { bondState } returns BluetoothDevice.BOND_BONDED
        }

    private fun resetCallbackSession(
        wrapper: BluetoothHidDeviceWrapper,
        host: BluetoothDevice,
        event: String,
    ) {
        val coordinator = getPrivateField(wrapper, "callbackCoordinator") as BluetoothHidCallbackCoordinator
        when (event) {
            "adapter-off" -> {
                val intent =
                    mockk<Intent> {
                        every { action } returns BluetoothAdapter.ACTION_STATE_CHANGED
                        every { getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) } returns
                            BluetoothAdapter.STATE_OFF
                    }
                coordinator.bluetoothStateReceiver.onReceive(mockk(), intent)
            }
            "profile-lost" -> coordinator.serviceListener.onServiceDisconnected(BluetoothProfile.HID_DEVICE)
            "registration-lost" -> coordinator.hidCallback.onAppStatusChanged(null, false)
            "registration-renewed" -> coordinator.hidCallback.onAppStatusChanged(null, true)
            "unplug" -> coordinator.hidCallback.onVirtualCableUnplug(host)
            "unregister" -> wrapper.unregisterApp()
            "close" -> wrapper.close()
            "error" -> wrapper.reportError(SYNTHETIC_ERROR)
            else -> error("Unknown callback event: $event")
        }
    }

    private fun setPrivateField(
        instance: Any,
        name: String,
        value: Any,
    ) {
        instance.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(instance, value)
        }
    }

    private fun getPrivateField(
        instance: Any,
        name: String,
    ): Any? =
        instance.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(instance)

    @Suppress("UNCHECKED_CAST")
    private fun setConnectionState(
        wrapper: BluetoothHidDeviceWrapper,
        state: HidConnectionState,
    ) {
        (getPrivateField(wrapper, "_connectionState") as MutableStateFlow<HidConnectionState>).value = state
    }

    private companion object {
        const val INCOMING_REPORT_CAPACITY = 64
        const val REPORT_SIZE = 62
        const val SYNTHETIC_ERROR = "synthetic transport failure"
        val expectedReport = ByteArray(REPORT_SIZE) { 0x5A }
    }
}
