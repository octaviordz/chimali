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

/** Outcome of admitting one Control Point packet into the current FIDO BLE request. */
sealed interface BleIncomingWriteResult {
    data class Complete(
        val message: FidoBleMessage,
    ) : BleIncomingWriteResult

    data class InProgress(
        val sessionGeneration: Long,
        val timeoutToken: Long,
    ) : BleIncomingWriteResult

    data object NotReady : BleIncomingWriteResult

    data class Error(
        val errorCode: Byte,
        val sessionGeneration: Long,
    ) : BleIncomingWriteResult
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
        private const val BLE_ERROR_FRAME_SIZE = 4
        private const val MAX_WIRE_LENGTH = 65535
        private const val CMD_FLAG_MASK = 0x80
        private const val BYTE_MASK = 0xFF
        private const val HEX_RADIX = 16
    }

    init {
        require(maxMsgSize in 1..MAX_WIRE_LENGTH) {
            "maxMsgSize must be positive and <= $MAX_WIRE_LENGTH, but was $maxMsgSize"
        }
        require(supportedAndroidWriteCapacity >= MIN_CAPACITY) {
            "Supported Android write capacity must be at least $MIN_CAPACITY, but was $supportedAndroidWriteCapacity"
        }
        require(supportedAndroidNotificationCapacity >= BLE_ERROR_FRAME_SIZE) {
            "Supported Android notification capacity must fit a BLE error response"
        }
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
                minOf(MAX_WRITE_CAPACITY, attMtu - ATT_HEADER_OVERHEAD, supportedAndroidWriteCapacity)
            }

    val notifyCapacity: Int
        get() =
            synchronized(lock) {
                minOf(attMtu - ATT_HEADER_OVERHEAD, supportedAndroidNotificationCapacity)
            }

    val isCommandReady: Boolean
        get() =
            synchronized(lock) {
                activeDeviceAddress != null &&
                    selectedRevision == REVISION_FIDO2_0x20 &&
                    isNotificationsEnabled
            }

    // Reuse the bounded framer; this owner adds the per-session receive deadline.
    private val requestAssembler = FidoBleFraming.RequestAssembler(maxMsgSize)
    private var lastActivityNanos: Long? = null
    private var assemblyTimeoutToken = 0L
    private var activeAssemblyTimeoutToken: Long? = null

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
            if (!enabled) resetAssembly()
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
     * Invalid frames return a typed error so the transport can send the required CMD_ERROR response.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun processIncomingWrite(packet: ByteArray): BleIncomingWriteResult =
        synchronized(lock) {
            if (!isCommandReady) {
                Logger.w { "BleSessionOwner: Command rejected; revision 0x20 and notifications must be active first" }
                BleIncomingWriteResult.NotReady
            } else if (packet.isEmpty()) {
                resetAssembly()
                incomingWriteError(FidoBleFraming.ERR_INVALID_PAR)
            } else {
                val now = monotonicNanos()
                val firstByte = packet[0].toInt() and BYTE_MASK
                val isInitialization = firstByte and CMD_FLAG_MASK != 0
                if (isInitialization) {
                    resetAssembly()
                } else if (requestAssembler.isAssembling && isReceiveTimedOut(now)) {
                    Logger.w { "BleSessionOwner: Multi-packet assembly timed out after 3 seconds" }
                    resetAssembly()
                    return incomingWriteError(FidoBleFraming.ERR_REQ_TIMEOUT)
                }

                when (val result = requestAssembler.feed(packet)) {
                    is FidoBleFraming.FeedResult.Complete -> {
                        lastActivityNanos = null
                        activeAssemblyTimeoutToken = null
                        assemblyTimeoutToken++
                        BleIncomingWriteResult.Complete(
                            FidoBleMessage(result.command, result.payload, currentSessionGeneration),
                        )
                    }
                    is FidoBleFraming.FeedResult.InProgress -> {
                        lastActivityNanos = now
                        assemblyTimeoutToken++
                        activeAssemblyTimeoutToken = assemblyTimeoutToken
                        BleIncomingWriteResult.InProgress(currentSessionGeneration, assemblyTimeoutToken)
                    }
                    is FidoBleFraming.FeedResult.Error -> {
                        resetAssembly()
                        incomingWriteError(result.errorCode)
                    }
                }
            }
        }

    /** Expires only the matching incomplete request; stale timer callbacks are harmless. */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun expireIncompleteRequest(
        generation: Long,
        timeoutToken: Long,
    ): BleIncomingWriteResult.Error? =
        synchronized(lock) {
            if (generation != currentSessionGeneration ||
                activeAssemblyTimeoutToken != timeoutToken ||
                !requestAssembler.isAssembling
            ) {
                return null
            }
            resetAssembly()
            incomingWriteError(FidoBleFraming.ERR_REQ_TIMEOUT)
        }

    private fun isReceiveTimedOut(nowNanos: Long): Boolean =
        lastActivityNanos?.let { nowNanos - it >= RECEIVE_TIMEOUT_NANOS } ?: false

    private fun incomingWriteError(errorCode: Byte): BleIncomingWriteResult.Error =
        BleIncomingWriteResult.Error(errorCode, currentSessionGeneration)

    private fun resetAssembly() {
        requestAssembler.reset()
        lastActivityNanos = null
        activeAssemblyTimeoutToken = null
        assemblyTimeoutToken++
    }
}
