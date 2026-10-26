package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

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
    ): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)

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
        val expectedReport = ByteArray(REPORT_SIZE) { 0x5A }
    }
}
