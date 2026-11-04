package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T012 / T017 / T032: BLE session owner unit tests.
 *
 * Verifies single-active host exclusivity, session generation fencing, revision selection (0x20 only),
 * CCCD notification state, dynamic MTU capacity derivation, 3-second receive assembly timeout,
 * and ceremony lifecycle management.
 */
@Req("FR-BLE-040", "FR-BLE-050", "TR-BLE-020")
class BleSessionOwnerTest {
    private lateinit var sessionOwner: BleSessionOwner
    private var virtualTimeNanos = 0L

    companion object {
        private const val DEVICE_A = "AA:BB:CC:DD:EE:01"
        private const val DEVICE_B = "AA:BB:CC:DD:EE:02"
        private const val REVISION_FIDO2_0x20: Byte = 0x20
        private const val REVISION_INVALID_0x10: Byte = 0x10
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }

    @BeforeTest
    fun setUp() {
        virtualTimeNanos = 0L
        sessionOwner =
            BleSessionOwner(
                monotonicNanos = { virtualTimeNanos },
                maxMsgSize = 1200,
            )
    }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `single active host connects and second host is rejected`() {
        assertTrue(sessionOwner.connectHost(DEVICE_A))
        assertEquals(DEVICE_A, sessionOwner.activeDeviceAddress)
        assertFalse(sessionOwner.connectHost(DEVICE_B), "Second concurrent host must be rejected")
    }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `disconnect fences session generation and allows reconnection`() {
        assertTrue(sessionOwner.connectHost(DEVICE_A))
        val gen1 = sessionOwner.currentSessionGeneration
        sessionOwner.disconnectHost(DEVICE_A)

        assertNull(sessionOwner.activeDeviceAddress)
        val gen2 = sessionOwner.currentSessionGeneration
        assertTrue(gen2 > gen1, "Session generation must increment on disconnect")
        assertFalse(sessionOwner.isSessionCurrent(gen1), "Old session generation must be fenced")

        // Reconnect allowed
        assertTrue(sessionOwner.connectHost(DEVICE_B))
        assertEquals(DEVICE_B, sessionOwner.activeDeviceAddress)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-010")
    fun `only revision 0x20 is selectable`() {
        sessionOwner.connectHost(DEVICE_A)

        assertFalse(sessionOwner.selectRevision(REVISION_INVALID_0x10))
        assertNull(sessionOwner.selectedRevision)

        assertTrue(sessionOwner.selectRevision(REVISION_FIDO2_0x20))
        assertEquals(REVISION_FIDO2_0x20, sessionOwner.selectedRevision)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-020")
    fun `CCCD notification subscription and revision are required for command readiness`() {
        sessionOwner.connectHost(DEVICE_A)
        assertFalse(sessionOwner.isCommandReady)

        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        assertFalse(sessionOwner.isCommandReady, "Needs CCCD notifications subscribed")

        sessionOwner.setNotificationsEnabled(true)
        assertTrue(sessionOwner.isCommandReady)

        sessionOwner.setNotificationsEnabled(false)
        assertFalse(sessionOwner.isCommandReady)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-020")
    fun `MTU update recalculates write and notify capacities correctly`() {
        sessionOwner.connectHost(DEVICE_A)
        // Default ATT MTU 23: 23 - 3 = 20
        assertEquals(20, sessionOwner.writeCapacity)
        assertEquals(20, sessionOwner.notifyCapacity)

        // MTU 256: write capacity = min(512, 256-3) = 253, notify = 253
        sessionOwner.updateMtu(256)
        assertEquals(253, sessionOwner.writeCapacity)
        assertEquals(253, sessionOwner.notifyCapacity)

        // MTU 517: write capacity = min(512, 517-3) = 512, notify = 514
        sessionOwner.updateMtu(517)
        assertEquals(512, sessionOwner.writeCapacity)
        assertEquals(514, sessionOwner.notifyCapacity)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-020")
    fun `write and notify capacities honor platform maximum limits`() {
        val customOwner =
            BleSessionOwner(
                supportedAndroidWriteCapacity = 300,
                supportedAndroidNotificationCapacity = 200,
            )
        customOwner.connectHost(DEVICE_A)
        customOwner.updateMtu(517)

        // min(512, 517-3, 300) = 300
        assertEquals(300, customOwner.writeCapacity)
        // min(517-3, 200) = 200
        assertEquals(200, customOwner.notifyCapacity)
    }

    @Test
    @Req("FR-BLE-020", "FR-BLE-050", "TR-BLE-020")
    fun `commands are rejected when revision or notifications are not ready`() {
        sessionOwner.connectHost(DEVICE_A)
        val packet = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x42)

        // Case 1: Neither revision nor notifications set
        assertNull(sessionOwner.processIncomingWrite(packet))

        // Case 2: Only revision set
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        assertNull(sessionOwner.processIncomingWrite(packet))

        // Case 3: Both set -> ready
        sessionOwner.setNotificationsEnabled(true)
        val result = sessionOwner.processIncomingWrite(packet)
        assertNotNull(result)
        assertEquals(0x81.toByte(), result.command)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `assembleRequest reassembles multi-packet message within 3s deadline`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        // Init packet: CMD_MSG (0x83), length 5 (0x00, 0x05), data 3 bytes
        val initPacket = byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01, 0x02, 0x03)
        val assembly1 = sessionOwner.processIncomingWrite(initPacket)
        assertNull(assembly1, "Message incomplete — waiting for continuation")

        // Advance 2 seconds (within 3s limit)
        virtualTimeNanos += 2 * NANOS_PER_SECOND

        // Continuation packet: SEQ 0 (0x00), data 2 bytes
        val contPacket = byteArrayOf(0x00, 0x04, 0x05)
        val completeMessage = sessionOwner.processIncomingWrite(contPacket)

        assertNotNull(completeMessage)
        assertEquals(0x83.toByte(), completeMessage.command)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), completeMessage.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `assembleRequest times out after 3 seconds without continuation`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        val initPacket = byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01, 0x02, 0x03)
        sessionOwner.processIncomingWrite(initPacket)

        // Advance exactly 3.001 seconds
        virtualTimeNanos += 3 * NANOS_PER_SECOND + 1_000_000L

        // Next continuation arrives after timeout
        val contPacket = byteArrayOf(0x00, 0x04, 0x05)
        val result = sessionOwner.processIncomingWrite(contPacket)
        assertNull(result, "Expired assembly must be discarded")
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `out-of-sequence continuation discards assembly`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        val initPacket = byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01, 0x02, 0x03)
        sessionOwner.processIncomingWrite(initPacket)

        // Expected SEQ 0, but sending SEQ 1
        val invalidSeqPacket = byteArrayOf(0x01, 0x04, 0x05)
        val result = sessionOwner.processIncomingWrite(invalidSeqPacket)
        assertNull(result, "Out of order sequence must discard assembly")
    }
}
