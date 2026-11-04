package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.concurrent.atomic.AtomicLong

/**
 * Reassembled BLE command frame.
 *
 * @property command The command byte (e.g. 0x81 for PING, 0x83 for MSG, 0xBE for CANCEL).
 * @property data The complete payload.
 */
@Req("FR-BLE-050", "TR-BLE-020")
data class FidoBleMessage(
    val command: Byte,
    val data: ByteArray,
    val sessionGeneration: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FidoBleMessage) return false
        if (command != other.command) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = command.toInt()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Owns run, session, connection generation, and multi-packet assembly state for FIDO BLE.
 *
 * Enforces:
 * - Single-active host exclusivity: only one connected BLE central at a time.
 * - Fencing: stale generation callbacks are ignored.
 * - Revision: only FIDO2 revision 0x20 can be selected.
 * - Readiness: requires both selected revision 0x20 and enabled CCCD notifications.
 * - Capacities: dynamic write/notify limits based on negotiated ATT MTU.
 * - 3-second receive deadline: incomplete multi-packet requests expire 3000ms after last fragment.
 */
@Req("FR-BLE-040", "FR-BLE-050", "TR-BLE-020")
class BleSessionOwner(
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val maxMsgSize: Int = 1200,
    private val supportedAndroidWriteCapacity: Int = MAX_WRITE_CAPACITY,
    private val supportedAndroidNotificationCapacity: Int = Int.MAX_VALUE,
) {
    companion object {
        const val REVISION_FIDO2_0x20: Byte = 0x20
        const val DEFAULT_ATT_MTU = 23
        const val MIN_CAPACITY = 20
        const val MAX_WRITE_CAPACITY = 512
        const val RECEIVE_TIMEOUT_NANOS = 3_000_000_000L // 3 seconds
        private const val ATT_HEADER_OVERHEAD = 3
        private const val INIT_FRAME_HEADER_SIZE = 3
        private const val MAX_WIRE_LENGTH = 65535
        private const val CMD_FLAG_MASK = 0x80
        private const val SEQ_MODULO = 128
        private const val SEQUENCE_MASK = 0x7F
        private const val BYTE_MASK = 0xFF
        private const val SHIFT_8 = 8
        private const val HEX_RADIX = 16
    }

    private val lock = Any()

    private val sessionGenerationCounter = AtomicLong(1L)
    val currentSessionGeneration: Long
        get() = sessionGenerationCounter.get()

    var activeDeviceAddress: String? = null
        private set

    var selectedRevision: Byte? = null
        private set

    var isNotificationsEnabled: Boolean = false
        private set

    var attMtu: Int = DEFAULT_ATT_MTU
        private set

    val writeCapacity: Int
        get() =
            synchronized(lock) {
                maxOf(
                    MIN_CAPACITY,
                    minOf(MAX_WRITE_CAPACITY, attMtu - ATT_HEADER_OVERHEAD, supportedAndroidWriteCapacity),
                )
            }

    val notifyCapacity: Int
        get() =
            synchronized(lock) {
                maxOf(MIN_CAPACITY, minOf(attMtu - ATT_HEADER_OVERHEAD, supportedAndroidNotificationCapacity))
            }

    val isCommandReady: Boolean
        get() =
            synchronized(lock) {
                activeDeviceAddress != null &&
                    selectedRevision == REVISION_FIDO2_0x20 &&
                    isNotificationsEnabled
            }

    // Assembly state
    private var assemblyCommand: Byte = 0
    private var declaredLength: Int = 0
    private var receivedBytes: Int = 0
    private var nextExpectedSequence: Int = 0
    private var assemblyBuffer: ByteArray? = null
    private var lastActivityNanos: Long = 0L

    /**
     * Connects a new host if no other host is active.
     *
     * @return true if accepted, false if another host is already connected.
     */
    @Req("FR-BLE-040", "TR-BLE-020")
    fun connectHost(address: String): Boolean =
        synchronized(lock) {
            if (activeDeviceAddress != null && activeDeviceAddress != address) {
                Logger.w { "BleSessionOwner: Rejected second host $address (active=$activeDeviceAddress)" }
                return false
            }
            activeDeviceAddress = address
            selectedRevision = null
            isNotificationsEnabled = false
            attMtu = DEFAULT_ATT_MTU
            resetAssembly()
            true
        }

    /**
     * Disconnects the given host, fences the session generation, and clears transient state.
     */
    @Req("FR-BLE-040", "TR-BLE-020")
    fun disconnectHost(address: String?) =
        synchronized(lock) {
            if (address == null || activeDeviceAddress == address) {
                activeDeviceAddress = null
                selectedRevision = null
                isNotificationsEnabled = false
                attMtu = DEFAULT_ATT_MTU
                resetAssembly()
                sessionGenerationCounter.incrementAndGet()
                Logger.d {
                    "BleSessionOwner: Disconnected, session generation incremented to $currentSessionGeneration"
                }
            }
        }

    /**
     * Updates the negotiated ATT MTU and recalculates per-session capacities.
     */
    @Req("FR-BLE-020", "TR-BLE-020")
    fun updateMtu(mtu: Int) =
        synchronized(lock) {
            attMtu = maxOf(DEFAULT_ATT_MTU, mtu)
            Logger.d { "BleSessionOwner: MTU updated to $attMtu (writeCap=$writeCapacity, notifyCap=$notifyCapacity)" }
        }

    /**
     * Selects the FIDO revision. Only [REVISION_FIDO2_0x20] is permitted.
     */
    @Req("FR-BLE-020", "TR-BLE-010")
    fun selectRevision(revision: Byte): Boolean =
        synchronized(lock) {
            if (revision == REVISION_FIDO2_0x20) {
                selectedRevision = revision
                Logger.i { "BleSessionOwner: FIDO2 revision 0x20 selected" }
                true
            } else {
                Logger.w { "BleSessionOwner: Rejected invalid revision 0x${revision.toString(HEX_RADIX)}" }
                false
            }
        }

    /**
     * Updates CCCD notification subscription state.
     */
    @Req("FR-BLE-020", "TR-BLE-020")
    fun setNotificationsEnabled(enabled: Boolean) =
        synchronized(lock) {
            isNotificationsEnabled = enabled
            Logger.d { "BleSessionOwner: Notifications enabled=$enabled" }
        }

    /**
     * Checks if a session generation is still current.
     */
    @Req("FR-BLE-040", "TR-BLE-020")
    fun isSessionCurrent(generation: Long): Boolean = sessionGenerationCounter.get() == generation

    /** Runs a synchronous side effect against the address only while its session generation is current. */
    @Req("FR-BLE-040", "TR-BLE-020")
    fun runForCurrentSession(
        generation: Long,
        action: (String) -> Unit,
    ): Boolean =
        synchronized(lock) {
            val address = activeDeviceAddress
            if (sessionGenerationCounter.get() != generation || address == null) {
                return false
            }
            action(address)
            true
        }

    /**
     * Processes an incoming write packet for multi-packet reassembly.
     *
     * @return [FidoBleMessage] if reassembly is complete, or null if awaiting more fragments / timed out / invalid.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun processIncomingWrite(packet: ByteArray): FidoBleMessage? =
        synchronized(lock) {
            if (!isCommandReady) {
                Logger.w { "BleSessionOwner: Command rejected; revision 0x20 and notifications must be active first" }
                return null
            }
            if (packet.isEmpty()) return null

            val now = monotonicNanos()
            val firstByte = packet[0].toInt() and BYTE_MASK

            // Check if initialization packet (bit 7 set)
            if (firstByte and CMD_FLAG_MASK != 0) {
                if (packet.size < INIT_FRAME_HEADER_SIZE) {
                    Logger.w { "BleSessionOwner: Initial packet too short (${packet.size} bytes)" }
                    resetAssembly()
                    return null
                }

                val cmd = packet[0]
                val bcntH = packet[1].toInt() and BYTE_MASK
                val bcntL = packet[2].toInt() and BYTE_MASK
                val totalLength = (bcntH shl SHIFT_8) or bcntL

                if (totalLength > maxMsgSize || totalLength > MAX_WIRE_LENGTH) {
                    Logger.w { "BleSessionOwner: Declared length $totalLength exceeds maxMsgSize $maxMsgSize" }
                    resetAssembly()
                    return null
                }

                assemblyCommand = cmd
                declaredLength = totalLength
                assemblyBuffer = ByteArray(totalLength)
                receivedBytes = 0
                nextExpectedSequence = 0
                lastActivityNanos = now

                val dataOffset = INIT_FRAME_HEADER_SIZE
                val dataInInit = minOf(packet.size - dataOffset, totalLength)
                if (dataInInit > 0) {
                    System.arraycopy(packet, dataOffset, assemblyBuffer!!, 0, dataInInit)
                    receivedBytes += dataInInit
                }

                if (receivedBytes >= declaredLength) {
                    val complete = FidoBleMessage(assemblyCommand, assemblyBuffer!!, currentSessionGeneration)
                    assemblyBuffer = null
                    resetAssembly()
                    return complete
                }

                return null
            } else {
                // Continuation packet (bit 7 clear)
                if (assemblyBuffer == null) {
                    Logger.w { "BleSessionOwner: Unexpected continuation packet without prior initialization" }
                    return null
                }

                // Check 3-second receive timeout
                if (now - lastActivityNanos > RECEIVE_TIMEOUT_NANOS) {
                    Logger.w { "BleSessionOwner: Multi-packet assembly timed out after 3 seconds" }
                    resetAssembly()
                    return null
                }

                val seq = packet[0].toInt() and SEQUENCE_MASK
                if (seq != nextExpectedSequence) {
                    Logger.w { "BleSessionOwner: Sequence mismatch (expected $nextExpectedSequence, got $seq)" }
                    resetAssembly()
                    return null
                }

                lastActivityNanos = now
                nextExpectedSequence = (nextExpectedSequence + 1) % SEQ_MODULO

                val dataOffset = 1
                val remainingExpected = declaredLength - receivedBytes
                val dataInCont = minOf(packet.size - dataOffset, remainingExpected)

                if (dataInCont > 0) {
                    System.arraycopy(packet, dataOffset, assemblyBuffer!!, receivedBytes, dataInCont)
                    receivedBytes += dataInCont
                }

                if (receivedBytes >= declaredLength) {
                    val complete = FidoBleMessage(assemblyCommand, assemblyBuffer!!, currentSessionGeneration)
                    assemblyBuffer = null
                    resetAssembly()
                    return complete
                }

                return null
            }
        }

    private fun resetAssembly() {
        assemblyBuffer?.fill(0)
        assemblyBuffer = null
        assemblyCommand = 0
        declaredLength = 0
        receivedBytes = 0
        nextExpectedSequence = 0
        lastActivityNanos = 0L
    }
}
