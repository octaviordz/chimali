package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `unrelated disconnect preserves active host while addressless disconnect resets it`() {
        sessionOwner.connectHost(DEVICE_A)
        val initialGeneration = sessionOwner.currentSessionGeneration

        sessionOwner.disconnectHost(DEVICE_B)

        assertEquals(DEVICE_A, sessionOwner.activeDeviceAddress)
        assertEquals(initialGeneration, sessionOwner.currentSessionGeneration)

        sessionOwner.disconnectHost(null)

        assertNull(sessionOwner.activeDeviceAddress)
        assertTrue(sessionOwner.currentSessionGeneration > initialGeneration)
    }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `side effect runs only for the active session generation`() {
        sessionOwner.connectHost(DEVICE_A)
        val generation = sessionOwner.currentSessionGeneration
        val addresses = mutableListOf<String>()

        assertTrue(sessionOwner.runForCurrentSession(generation) { addresses += it })
        assertFalse(sessionOwner.runForCurrentSession(generation - 1) { addresses += it })
        assertEquals(listOf(DEVICE_A), addresses)

        sessionOwner.disconnectHost(DEVICE_A)
        assertFalse(sessionOwner.runForCurrentSession(generation) { addresses += it })
        assertEquals(listOf(DEVICE_A), addresses)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `message value identity includes CTAP payload and session generation`() {
        val message = FidoBleMessage(0x83.toByte(), byteArrayOf(0x01, 0x02), sessionGeneration = 3L)
        val equalMessage = FidoBleMessage(0x83.toByte(), byteArrayOf(0x01, 0x02), sessionGeneration = 3L)

        assertEquals(message, message)
        assertEquals(message, equalMessage)
        assertEquals(message.hashCode(), equalMessage.hashCode())
        assertFalse(message == FidoBleMessage(0x82.toByte(), byteArrayOf(0x01, 0x02), 3L))
        assertFalse(message == FidoBleMessage(0x83.toByte(), byteArrayOf(0x01), 3L))
        assertFalse(message == FidoBleMessage(0x83.toByte(), byteArrayOf(0x01, 0x02), 4L))
        assertFalse(message.equals("not a FIDO BLE message"))
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

        val notificationLimitedOwner = BleSessionOwner(supportedAndroidNotificationCapacity = 10)
        notificationLimitedOwner.updateMtu(517)
        assertEquals(10, notificationLimitedOwner.notifyCapacity)

        assertFailsWith<IllegalArgumentException> {
            BleSessionOwner(supportedAndroidWriteCapacity = BleSessionOwner.MIN_CAPACITY - 1)
        }
        assertFailsWith<IllegalArgumentException> {
            BleSessionOwner(supportedAndroidNotificationCapacity = 3)
        }
        assertFailsWith<IllegalArgumentException> {
            BleSessionOwner(maxMsgSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BleSessionOwner(maxMsgSize = 65_536)
        }
    }

    @Test
    @Req("FR-BLE-020", "FR-BLE-050", "TR-BLE-020")
    fun `commands are rejected when revision or notifications are not ready`() {
        sessionOwner.connectHost(DEVICE_A)
        val packet = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x42)

        // Case 1: Neither revision nor notifications set
        assertEquals(BleIncomingWriteResult.NotReady, sessionOwner.processIncomingWrite(packet))

        // Case 2: Only revision set
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        assertEquals(BleIncomingWriteResult.NotReady, sessionOwner.processIncomingWrite(packet))

        // Case 3: Both set -> ready
        sessionOwner.setNotificationsEnabled(true)
        val result = sessionOwner.processIncomingWrite(packet)
        val message = (result as BleIncomingWriteResult.Complete).message
        assertEquals(0x81.toByte(), message.command)
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
        assertIs<BleIncomingWriteResult.InProgress>(assembly1, "Message incomplete — waiting for continuation")

        // Advance 2 seconds (within 3s limit)
        virtualTimeNanos += 2 * NANOS_PER_SECOND

        // Continuation packet: SEQ 0 (0x00), data 2 bytes
        val contPacket = byteArrayOf(0x00, 0x04, 0x05)
        val completeMessage = sessionOwner.processIncomingWrite(contPacket)

        val message = (completeMessage as BleIncomingWriteResult.Complete).message
        assertEquals(0x83.toByte(), message.command)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), message.data)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `assembleRequest times out after 3 seconds without continuation`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        val initPacket = byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01, 0x02, 0x03)
        sessionOwner.processIncomingWrite(initPacket)

        // Advance exactly 3 seconds to the receive deadline
        virtualTimeNanos += 3 * NANOS_PER_SECOND

        // Next continuation arrives after timeout
        val contPacket = byteArrayOf(0x00, 0x04, 0x05)
        val result = sessionOwner.processIncomingWrite(contPacket)
        assertEquals(FidoBleFraming.ERR_REQ_TIMEOUT, (result as BleIncomingWriteResult.Error).errorCode)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `assembly expiry ignores stale generation and token then expires the matching request`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)
        val progress =
            sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01)) as
                BleIncomingWriteResult.InProgress

        assertNull(sessionOwner.expireIncompleteRequest(progress.sessionGeneration - 1, progress.timeoutToken))
        assertNull(sessionOwner.expireIncompleteRequest(progress.sessionGeneration, progress.timeoutToken + 1))

        virtualTimeNanos += BleSessionOwner.RECEIVE_TIMEOUT_NANOS
        val expired = sessionOwner.expireIncompleteRequest(progress.sessionGeneration, progress.timeoutToken)

        assertIs<BleIncomingWriteResult.Error>(expired)
        assertEquals(FidoBleFraming.ERR_REQ_TIMEOUT, expired.errorCode)
        assertNull(sessionOwner.expireIncompleteRequest(progress.sessionGeneration, progress.timeoutToken))
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
        assertEquals(FidoBleFraming.ERR_INVALID_SEQ, (result as BleIncomingWriteResult.Error).errorCode)
    }

    @Test
    @Req("FR-BLE-050", "TR-BLE-020")
    fun `malformed initialization length and oversized continuation return typed errors`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        val emptyFrame = sessionOwner.processIncomingWrite(byteArrayOf())
        assertEquals(FidoBleFraming.ERR_INVALID_PAR, (emptyFrame as BleIncomingWriteResult.Error).errorCode)

        val shortHeader = sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x00))
        assertEquals(FidoBleFraming.ERR_INVALID_LEN, (shortHeader as BleIncomingWriteResult.Error).errorCode)

        val oversizeDeclaration = sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x04, 0xB1.toByte()))
        assertEquals(FidoBleFraming.ERR_INVALID_LEN, (oversizeDeclaration as BleIncomingWriteResult.Error).errorCode)

        val extraInitPayload = sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x00, 0x01, 0x11, 0x22))
        assertEquals(FidoBleFraming.ERR_INVALID_LEN, (extraInitPayload as BleIncomingWriteResult.Error).errorCode)

        sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x00, 0x12) + ByteArray(17))
        val extraContinuation = sessionOwner.processIncomingWrite(byteArrayOf(0x00) + ByteArray(2))
        assertEquals(FidoBleFraming.ERR_INVALID_LEN, (extraContinuation as BleIncomingWriteResult.Error).errorCode)
    }

    @Test
    @Req("FR-BLE-020", "FR-BLE-050", "TR-BLE-020")
    fun `disabling notifications erases partial request assembly`() {
        sessionOwner.connectHost(DEVICE_A)
        sessionOwner.selectRevision(REVISION_FIDO2_0x20)
        sessionOwner.setNotificationsEnabled(true)

        val partial = sessionOwner.processIncomingWrite(byteArrayOf(0x83.toByte(), 0x00, 0x05, 0x01))
        assertIs<BleIncomingWriteResult.InProgress>(partial)

        sessionOwner.setNotificationsEnabled(false)
        sessionOwner.setNotificationsEnabled(true)

        val staleContinuation = sessionOwner.processIncomingWrite(byteArrayOf(0x00, 0x02, 0x03))
        assertEquals(FidoBleFraming.ERR_INVALID_SEQ, (staleContinuation as BleIncomingWriteResult.Error).errorCode)
    }
}
