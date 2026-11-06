package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bounded FIFO notification ownership for FIDO BLE.
 *
 * Enforces:
 * - One in-flight notification at a time with completion via [onNotificationSent].
 * - Strict FIFO packet ordering.
 * - Keepalive coalescing (replaces previous pending keepalive).
 * - Keepalive suppression during terminal response transmission.
 * - Bounded queue capacity (max 2 * maxMsgSize + 1024 bytes).
 * - 5-second completion deadline: triggers [onSessionStalled] and clears if a notification stalls.
 */
@Req("FR-BLE-050", "FR-BLE-060", "TR-BLE-020", "TR-BLE-040")
class BleNotificationWriter(
    private val scope: CoroutineScope,
    private val maxMsgSize: Int = 1200,
    private val sendGattNotification: suspend (Long, ByteArray) -> Boolean,
    private val isSessionCurrent: (Long) -> Boolean = { true },
    private val onSessionStalled: (Long) -> Unit,
) {
    companion object {
        const val NOTIFICATION_TIMEOUT_MILLIS = 5000L
        private const val RESPONSE_QUEUE_PACKET_ALLOWANCE = 2
        private const val QUEUE_OVERHEAD_BYTES = 1024
    }

    private val maxCapacityBytes = RESPONSE_QUEUE_PACKET_ALLOWANCE * maxMsgSize + QUEUE_OVERHEAD_BYTES

    private data class QueuedNotification(
        var data: ByteArray,
        val isKeepalive: Boolean,
        val sessionGeneration: Long,
        val onDeliveryResult: ((Boolean) -> Unit)?,
    )

    private val lock = Any()
    private val queue = ArrayDeque<QueuedNotification>()
    private var totalQueuedBytes = 0
    private var inFlight = false
    private var inFlightNotification: QueuedNotification? = null
    private var inFlightTimeoutJob: Job? = null
    private var inFlightSendJob: Job? = null
    private var responseTransmissionStarted = false
    private var activeSessionGeneration: Long? = null
    private var operationGeneration = 0L

    /**
     * Enqueues one notification packet for FIFO delivery.
     *
     * @param packet The notification byte array to transmit.
     * @param isKeepalive True if this is a progress keepalive frame.
     * @return true if enqueued or coalesced, false if rejected due to capacity bounds or response phase.
     */
    @Req("FR-BLE-050", "FR-BLE-060", "TR-BLE-020", "TR-BLE-040")
    fun enqueue(
        packet: ByteArray,
        isKeepalive: Boolean,
        sessionGeneration: Long,
        onDeliveryResult: ((Boolean) -> Unit)? = null,
    ): Boolean {
        var queueOverflowed = false
        val accepted =
            synchronized(lock) {
                if (!activateSessionLocked(sessionGeneration)) return false
                if (responseTransmissionStarted && isKeepalive) {
                    Logger.d { "BleNotificationWriter: Keepalive suppressed during response delivery" }
                    return false
                }

                if (isKeepalive) {
                    val existingIndex = queue.indexOfFirst { it.isKeepalive }
                    if (existingIndex >= 0) {
                        val old = queue[existingIndex]
                        totalQueuedBytes -= old.data.size
                        old.data.fill(0)
                        old.onDeliveryResult?.let { notifyDeliveryResult(it, false) }
                        old.data = packet.copyOf()
                        totalQueuedBytes += packet.size
                        Logger.d { "BleNotificationWriter: Coalesced keepalive in queue" }
                        return true
                    }
                }

                if (totalQueuedBytes + packet.size > maxCapacityBytes) {
                    Logger.w {
                        "BleNotificationWriter: Queue capacity exceeded " +
                            "($totalQueuedBytes + ${packet.size} > $maxCapacityBytes); aborting session"
                    }
                    queueOverflowed = true
                    return@synchronized false
                }

                queue.addLast(QueuedNotification(packet.copyOf(), isKeepalive, sessionGeneration, onDeliveryResult))
                totalQueuedBytes += packet.size

                if (!inFlight) {
                    scheduleNextLocked()
                }
                true
            }
        if (queueOverflowed) abortOverflowedSession(sessionGeneration)
        return accepted
    }

    private fun abortOverflowedSession(sessionGeneration: Long) {
        val aborted =
            synchronized(lock) {
                if (activeSessionGeneration != sessionGeneration || !isSessionCurrent(sessionGeneration)) {
                    false
                } else {
                    clearLocked()
                    true
                }
            }
        if (aborted && isSessionCurrent(sessionGeneration)) onSessionStalled(sessionGeneration)
    }

    /**
     * Called by the GATT callback when a notification transmission completes.
     *
     * @param success true if transmitted successfully, false if failed.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun onNotificationSent(success: Boolean) {
        synchronized(lock) {
            val sessionGeneration = activeSessionGeneration ?: return
            inFlightTimeoutJob?.cancel()
            inFlightTimeoutJob = null
            inFlight = false
            val completedNotification = inFlightNotification
            inFlightNotification = null
            inFlightSendJob = null
            if (completedNotification != null) totalQueuedBytes -= completedNotification.data.size

            if (!success) {
                Logger.e { "BleNotificationWriter: GATT notification send failed — stalling session" }
                completedNotification?.onDeliveryResult?.let { notifyDeliveryResult(it, false) }
                completedNotification?.data?.fill(0)
                clearLocked()
                if (isSessionCurrent(sessionGeneration)) onSessionStalled(sessionGeneration)
                return
            }

            completedNotification?.onDeliveryResult?.let { notifyDeliveryResult(it, true) }
            completedNotification?.data?.fill(0)
            scheduleNextLocked()
        }
    }

    /**
     * Signals that terminal response transmission has begun. Drops any queued keepalives.
     */
    @Req("FR-BLE-060", "TR-BLE-040")
    fun markResponseTransmissionStarted(sessionGeneration: Long) {
        synchronized(lock) {
            if (!activateSessionLocked(sessionGeneration)) return@synchronized
            responseTransmissionStarted = true
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.isKeepalive) {
                    totalQueuedBytes -= item.data.size
                    item.data.fill(0)
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Resets the response transmission phase at the end of a transaction.
     */
    @Req("FR-BLE-060", "TR-BLE-040")
    fun resetResponseTransmission(sessionGeneration: Long) =
        synchronized(lock) {
            if (activeSessionGeneration == sessionGeneration && isSessionCurrent(sessionGeneration)) {
                responseTransmissionStarted = false
            }
        }

    /**
     * Clears all pending notifications and cancels in-flight timeout.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun clear() =
        synchronized(lock) {
            clearLocked()
        }

    /**
     * Aborts a stalled session: cancels in-flight work and invokes stalled callback.
     */
    @Req("FR-BLE-050", "TR-BLE-020")
    fun abortStalledSession() =
        synchronized(lock) {
            val sessionGeneration = activeSessionGeneration
            clearLocked()
            if (sessionGeneration != null && isSessionCurrent(sessionGeneration)) {
                onSessionStalled(sessionGeneration)
            }
        }

    private fun clearLocked() {
        inFlightTimeoutJob?.cancel()
        inFlightTimeoutJob = null
        inFlightSendJob?.cancel()
        inFlightSendJob = null
        inFlight = false
        inFlightNotification?.onDeliveryResult?.let { notifyDeliveryResult(it, false) }
        inFlightNotification = null
        responseTransmissionStarted = false
        activeSessionGeneration = null
        operationGeneration++
        queue.forEach {
            it.onDeliveryResult?.let { callback -> notifyDeliveryResult(callback, false) }
            it.data.fill(0)
        }
        queue.clear()
        totalQueuedBytes = 0
    }

    private fun activateSessionLocked(sessionGeneration: Long): Boolean {
        if (!isSessionCurrent(sessionGeneration)) return false
        if (activeSessionGeneration != sessionGeneration) {
            clearLocked()
            activeSessionGeneration = sessionGeneration
        }
        return true
    }

    private fun scheduleNextLocked() {
        if (queue.isEmpty()) {
            inFlight = false
            return
        }

        val next = queue.removeFirst()
        inFlight = true
        inFlightNotification = next
        val scheduledOperationGeneration = operationGeneration
        val sessionGeneration = next.sessionGeneration

        inFlightTimeoutJob =
            scope.launch {
                delay(NOTIFICATION_TIMEOUT_MILLIS)
                val shouldStall =
                    synchronized(lock) {
                        if (scheduledOperationGeneration != operationGeneration ||
                            activeSessionGeneration != sessionGeneration ||
                            !inFlight
                        ) {
                            false
                        } else {
                            Logger.e {
                                "BleNotificationWriter: Notification timed out after ${NOTIFICATION_TIMEOUT_MILLIS}ms"
                            }
                            clearLocked()
                            true
                        }
                    }
                if (shouldStall && isSessionCurrent(sessionGeneration)) onSessionStalled(sessionGeneration)
            }

        val packetToSend = next.data
        val sendJob =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val maySend =
                        isSessionCurrent(sessionGeneration) &&
                            synchronized(lock) {
                                scheduledOperationGeneration == operationGeneration &&
                                    activeSessionGeneration == sessionGeneration &&
                                    inFlight
                            }
                    val initiated = if (maySend) sendGattNotification(sessionGeneration, packetToSend) else false
                    if (!initiated) {
                        val shouldStall =
                            synchronized(lock) {
                                if (scheduledOperationGeneration != operationGeneration ||
                                    activeSessionGeneration != sessionGeneration ||
                                    !isSessionCurrent(sessionGeneration)
                                ) {
                                    false
                                } else {
                                    Logger.e { "BleNotificationWriter: Failed to initiate GATT notification" }
                                    clearLocked()
                                    true
                                }
                            }
                        if (shouldStall) onSessionStalled(sessionGeneration)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SecurityException) {
                    handleInitiationFailure(sessionGeneration, scheduledOperationGeneration, failure)
                } catch (failure: IllegalArgumentException) {
                    handleInitiationFailure(sessionGeneration, scheduledOperationGeneration, failure)
                } catch (failure: IllegalStateException) {
                    handleInitiationFailure(sessionGeneration, scheduledOperationGeneration, failure)
                } finally {
                    // The send call owns this buffer until it returns, even if session cleanup already detached it.
                    packetToSend.fill(0)
                }
            }
        inFlightSendJob = sendJob
        sendJob.start()
    }

    private fun handleInitiationFailure(
        sessionGeneration: Long,
        scheduledGeneration: Long,
        failure: RuntimeException,
    ) {
        Logger.e(failure) { "BleNotificationWriter: GATT notification initiation failed" }
        val shouldStall =
            synchronized(lock) {
                if (operationGeneration != scheduledGeneration ||
                    activeSessionGeneration != sessionGeneration ||
                    !isSessionCurrent(sessionGeneration)
                ) {
                    false
                } else {
                    clearLocked()
                    true
                }
            }
        if (shouldStall) onSessionStalled(sessionGeneration)
    }

    private fun notifyDeliveryResult(
        callback: (Boolean) -> Unit,
        success: Boolean,
    ) {
        runCatching { callback(success) }
            .onFailure { failure -> Logger.e(failure) { "BleNotificationWriter: Delivery callback failed" } }
    }
}
