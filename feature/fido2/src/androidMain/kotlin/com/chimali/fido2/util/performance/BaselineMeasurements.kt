package com.chimali.fido2.util.performance

import android.os.Build
import android.os.Trace
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * T003 measurement-branch instrumentation. Emits operation names, durations and API acceptance only.
 *
 * Starts after CTAPHID message reassembly and ends when the final response report's sendReport
 * returns. This measures local API handoff; it cannot establish remote receipt. Existing transport
 * scheduling, packet bytes, report pacing, crypto and verification behavior are preserved.
 */
object BaselineMeasurements {
    private const val TAG = "ChimaliT003"
    private val measuredOperations = setOf("MakeCredential", "GetAssertion", "GetInfo")
    private val sequence = AtomicInteger()
    private val operations = ConcurrentHashMap<String, Operation>()
    private val reports = ConcurrentHashMap<ByteArray, Response>()

    private class Operation(
        val name: String,
    ) {
        val startedNs = System.nanoTime()
        val cookie = sequence.incrementAndGet()
        val userStartedNs = AtomicLong()
        val userNs = AtomicLong()
        var processingNs = 0L
        private val traceClosed = AtomicBoolean()
        val traced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && traced) {
                Trace.beginAsyncSection("T003.$name", cookie)
            }
        }

        fun closeTrace() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && traced && traceClosed.compareAndSet(false, true)) {
                Trace.endAsyncSection("T003.$name", cookie)
            }
        }
    }

    private class Response(
        val operation: Operation,
        count: Int,
    ) {
        val remaining = AtomicInteger(count)
        val accepted = AtomicBoolean(true)
    }

    fun start(name: String) {
        if (name !in measuredOperations) return
        operations.put(name, Operation(name))?.closeTrace()
    }

    fun startUserInteraction(name: String) {
        operations[name]?.userStartedNs?.set(System.nanoTime())
    }

    fun endUserInteraction(name: String) {
        val operation = operations[name] ?: return
        val startedNs = operation.userStartedNs.getAndSet(0L)
        if (startedNs != 0L) operation.userNs.addAndGet(System.nanoTime() - startedNs)
    }

    fun processingComplete(name: String) {
        operations[name]?.let { it.processingNs = System.nanoTime() - it.startedNs }
    }

    /** Associate responses by array identity without changing the existing FIFO queue. */
    fun trackResponse(
        name: String,
        packets: List<ByteArray>,
    ) {
        val operation = operations.remove(name) ?: return
        if (packets.isEmpty()) {
            operation.closeTrace()
            return
        }
        val response = Response(operation, packets.size)
        packets.forEach { reports[it] = response }
    }

    /** Call immediately after sendReport returns, or with false when enqueueing fails. */
    fun reportAccepted(
        packet: ByteArray,
        accepted: Boolean,
    ) {
        val completedNs = System.nanoTime()
        val response = reports.remove(packet) ?: return
        if (!accepted) response.accepted.set(false)
        if (response.remaining.decrementAndGet() != 0) return
        val operation = response.operation
        val totalNs = completedNs - operation.startedNs
        val userNs = operation.userNs.get()
        val userWindowOpen = operation.userStartedNs.get() != 0L
        operation.closeTrace()
        // println is deliberately distinct from the stripped v/d/i/w/e logging methods.
        Log.println(
            Log.INFO,
            TAG,
            "operation=${operation.name} total_ns=$totalNs user_ns=$userNs " +
                "processing_ns=${operation.processingNs} system_ns=${maxOf(0L, totalNs - userNs)} " +
                "reports_accepted=${response.accepted.get()} user_window_open=$userWindowOpen",
        )
    }

    /** Mark interrupted measurements incomplete and release references on disconnect. */
    fun disconnect() {
        val incomplete = operations.values.toList() + reports.values.map { it.operation }
        operations.clear()
        reports.clear()
        incomplete.distinct().forEach { it.closeTrace() }
        if (incomplete.isNotEmpty()) Log.println(Log.INFO, TAG, "incomplete=true reason=disconnect")
    }
}
