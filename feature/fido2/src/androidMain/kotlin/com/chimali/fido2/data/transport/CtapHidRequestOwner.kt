package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.bluetooth.CtapHidMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns admitted CTAPHID request payloads until the active handler reaches a terminal state. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class CtapHidRequestOwner(
    private val scope: CoroutineScope,
    private val dispatcher: () -> CoroutineDispatcher,
    private val monotonicNanos: () -> Long,
    private val isSessionCurrent: (Long) -> Boolean,
    private val handleRequest: suspend (CtapHidMessage, Long, Long) -> Unit,
    private val reportChannelBusy: (ByteArray, Long) -> Unit,
    private val onPayloadForTest: (ByteArray) -> Unit = {},
) {
    internal companion object {
        /** Maximum simultaneously admitted CTAPHID requests owned by this transport. */
        const val MAX_ACTIVE_REQUESTS = 8
    }

    private val lock = Any()

    private data class RequestKey(
        val sessionGeneration: Long,
        val channelId: String,
    )

    private val jobs = mutableMapOf<RequestKey, Job>()

    /**
     * Admits one owned request per CID. A running handler borrows the payload until it exits;
     * completion erases it even if a lazy job is cancelled before its body starts. Rejected
     * channel-busy requests are erased immediately without reaching a handler.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Suppress("TooGenericExceptionCaught")
    suspend fun dispatch(
        message: CtapHidMessage,
        sessionGeneration: Long,
    ) {
        val key = RequestKey(sessionGeneration, message.channelId.toHex())
        val admittedNanos = monotonicNanos()
        var staleGeneration = false
        val operation =
            synchronized(lock) {
                if (!isSessionCurrent(sessionGeneration)) {
                    staleGeneration = true
                    null
                } else {
                    val existing = jobs[key]
                    if ((existing != null && !existing.isCompleted) ||
                        (existing == null && jobs.size >= MAX_ACTIVE_REQUESTS)
                    ) {
                        null
                    } else {
                        val admittedOperation =
                            scope.launch(dispatcher(), start = CoroutineStart.LAZY) {
                                try {
                                    handleRequest(message, admittedNanos, sessionGeneration)
                                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                                    currentCoroutineContext().ensureActive()
                                    Logger.i(cancellation) { "Discarded request after its wallet owner was fenced" }
                                } catch (failure: Exception) {
                                    // This per-request child of the transport SupervisorJob is an error boundary;
                                    // log the failure and keep unrelated CIDs alive. invokeOnCompletion erases input.
                                    Logger.e(failure) { "CTAPHID request handler failed" }
                                }
                            }
                        jobs[key] = admittedOperation
                        admittedOperation
                    }
                }
            }

        if (operation == null) {
            message.payload.fill(0)
            if (!staleGeneration) reportChannelBusy(message.channelId, sessionGeneration)
            return
        }

        operation.invokeOnCompletion {
            message.payload.fill(0)
            removeCompleted(key, operation)
        }
        onPayloadForTest(message.payload)
        operation.start()
    }

    /** Cancels the active request for one channel without erasing data still used by its handler. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun cancel(
        channelId: ByteArray,
        sessionGeneration: Long,
    ) {
        synchronized(lock) { jobs[RequestKey(sessionGeneration, channelId.toHex())] }?.cancel()
    }

    /** Cancels only requests admitted by the ended host generation without joining callbacks. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun cancelGeneration(sessionGeneration: Long) {
        val active =
            synchronized(lock) {
                jobs.filterKeys { it.sessionGeneration == sessionGeneration }.values.toList()
            }
        active.forEach { it.cancel() }
    }

    /** Cancels and joins every active request before transport/session teardown completes. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun cancelAll() {
        val active = synchronized(lock) { jobs.values.toList().also { jobs.clear() } }
        active.forEach { it.cancel() }
        active.forEach { it.cancelAndJoin() }
    }

    private fun removeCompleted(
        key: RequestKey,
        operation: Job,
    ) {
        synchronized(lock) {
            if (jobs[key] === operation) jobs.remove(key)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
