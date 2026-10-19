package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns bounded outgoing HID packet queues and serializes driver access across session replacement. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class HidReportSender(
    private val scope: CoroutineScope,
    private val dispatcher: () -> CoroutineDispatcher,
    private val sessionGeneration: () -> Long,
    private val sendReport: suspend (ByteArray, Long) -> Boolean,
    private val reportAccepted: (ByteArray, Boolean) -> Unit,
    private val reportPaceDelayMillis: Long,
) {
    private data class QueuedPacket(
        val payload: ByteArray,
        val sent: CompletableDeferred<Boolean>? = null,
        val generation: Long,
        val ownerJob: Job? = null,
    )

    private data class Session(
        val generation: Long,
        val channel: Channel<QueuedPacket>,
    )

    private val lock = Any()
    private val driverLock = Mutex()
    private var session: Session? = null
    private var senderJob: Job? = null

    /** Replaces the queue with a new host-session owner; previous cleanup remains queue-local. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun start(generation: Long = sessionGeneration()) {
        val (next, previous, previousJob) =
            synchronized(lock) {
                if (session?.generation == generation) return
                val next =
                    Session(
                        generation = generation,
                        channel = Channel(capacity = QUEUE_CAPACITY, onUndeliveredElement = ::dispose),
                    )
                val oldSession = session
                val oldJob = senderJob
                session = next
                senderJob = null
                Triple(next, oldSession, oldJob)
            }
        previousJob?.cancel()
        previous?.channel?.cancel()

        val job =
            scope.launch(dispatcher()) {
                driverLock.withLock {
                    try {
                        for (packet in next.channel) sendOne(packet)
                    } finally {
                        discardQueued(next)
                    }
                }
            }
        synchronized(lock) {
            if (session === next) {
                senderJob = job
            } else {
                job.cancel()
            }
        }
    }

    /** Detaches the current queue before cancellation, so future sessions cannot inherit it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun stop(expectedGeneration: Long? = null) {
        val (oldSession, oldJob) =
            synchronized(lock) {
                if (expectedGeneration != null && session?.generation != expectedGeneration) return
                val oldSession = session
                val oldJob = senderJob
                session = null
                senderJob = null
                oldSession to oldJob
            }
        oldJob?.cancel()
        oldSession?.channel?.cancel()
    }

    /** Copies and consumes each caller packet, admitting only into the current bounded queue. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun enqueue(
        packets: List<ByteArray>,
        expectedGeneration: Long = sessionGeneration(),
    ) {
        for (packet in packets) {
            val owned = packet.copyOf()
            val current = synchronized(lock) { session }
            val result =
                if (current == null ||
                    current.generation != expectedGeneration ||
                    sessionGeneration() != expectedGeneration
                ) {
                    null
                } else {
                    current.channel.trySend(QueuedPacket(owned, generation = expectedGeneration))
                }
            packet.fill(0)
            if (result == null || result.isFailure) {
                reportAccepted(owned, false)
                Logger.w { "No active HID sender or its bounded queue is full; dropping packet" }
                owned.fill(0)
            }
        }
    }

    /** Transfers an isolated packet copy and waits for driver handoff within the request session. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun enqueueAndAwait(
        packets: List<ByteArray>,
        expectedGeneration: Long,
    ): Boolean {
        var allReportsAccepted = true
        val current =
            synchronized(lock) {
                session?.takeIf { it.generation == expectedGeneration && sessionGeneration() == expectedGeneration }
            } ?: return false
        for (packet in packets) {
            currentCoroutineContext().ensureActive()
            val sent = CompletableDeferred<Boolean>()
            val owned = packet.copyOf()
            var transferred = false
            try {
                current.channel.send(
                    QueuedPacket(
                        payload = owned,
                        sent = sent,
                        generation = expectedGeneration,
                        ownerJob = currentCoroutineContext()[Job],
                    ),
                )
                transferred = true
                if (!sent.await()) allReportsAccepted = false
            } finally {
                if (!transferred) owned.fill(0)
            }
        }
        return allReportsAccepted
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendOne(packet: QueuedPacket) {
        try {
            if (packet.generation != sessionGeneration() || packet.ownerJob?.isActive == false) {
                packet.sent?.complete(false)
                return
            }
            val accepted = sendReport(packet.payload, packet.generation)
            reportAccepted(packet.payload, accepted)
            if (!accepted) Logger.w { "sendReport returned false — host may have disconnected" }
            packet.sent?.complete(accepted)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            packet.sent?.complete(false)
            throw cancellation
        } catch (failure: Exception) {
            reportAccepted(packet.payload, false)
            packet.sent?.complete(false)
            Logger.e(failure) { "Failed to send HID report" }
        } finally {
            packet.payload.fill(0)
        }
        delay(reportPaceDelayMillis)
    }

    private fun discardQueued(owner: Session) {
        while (true) {
            val packet = owner.channel.tryReceive().getOrNull() ?: break
            dispose(packet)
        }
    }

    private fun dispose(packet: QueuedPacket) {
        packet.payload.fill(0)
        packet.sent?.complete(false)
    }

    private companion object {
        const val QUEUE_CAPACITY = 256
    }
}
