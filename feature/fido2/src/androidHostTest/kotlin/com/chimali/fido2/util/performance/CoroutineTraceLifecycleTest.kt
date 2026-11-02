package com.chimali.fido2.util.performance

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoroutineTraceLifecycleTest {
    @Test
    fun `correlation rejects missing duplicate unpaired and active terminal events`() {
        val missing = LookupTraceCorrelation()
        assertFalse(missing.startSegment(1))
        assertEquals(LookupTraceCorrelation.TerminalResult.REJECTED, missing.recordTerminal())

        val correlation = LookupTraceCorrelation()
        assertTrue(correlation.recordLookupCookie(10))
        assertFalse(correlation.recordLookupCookie(11))
        assertFalse(correlation.endSegment(12))
        assertTrue(correlation.startSegment(20))
        // Active segment present, no callback: terminal flag set, returns DEFERRED.
        assertEquals(LookupTraceCorrelation.TerminalResult.DEFERRED, correlation.recordTerminal())
        assertTrue(correlation.endSegment(20))
        assertFalse(correlation.endSegment(20))
        // Terminal already set by the DEFERRED call above: duplicate terminal is REJECTED.
        assertEquals(LookupTraceCorrelation.TerminalResult.REJECTED, correlation.recordTerminal())
        assertTrue(correlation.isComplete())
    }

    @Test
    fun `terminal is deferred when active segments remain at recordTerminal call`() {
        val correlation = LookupTraceCorrelation()
        assertTrue(correlation.recordLookupCookie(10))
        assertTrue(correlation.startSegment(20))
        var callbackInvoked = false
        val result = correlation.recordTerminal { callbackInvoked = true }
        assertEquals(LookupTraceCorrelation.TerminalResult.DEFERRED, result)
        assertFalse(callbackInvoked)
        assertFalse(correlation.isComplete())
        // Closing the last segment must invoke the deferred terminal callback.
        assertTrue(correlation.endSegment(20))
        assertTrue(callbackInvoked)
        assertTrue(correlation.isComplete())
    }

    @Test
    fun `deferred callback is invoked immediately when segments drain before callback is stored`() {
        // Simulate the race: endSegment drains activeSegments before recordTerminal stores the callback.
        val correlation = LookupTraceCorrelation()
        assertTrue(correlation.recordLookupCookie(10))
        assertTrue(correlation.startSegment(20))
        // Drain segment before recordTerminal runs: activeSegments already empty.
        assertTrue(correlation.endSegment(20))
        var callbackInvoked = false
        val result = correlation.recordTerminal { callbackInvoked = true }
        // No active segments at the time of the call: IMMEDIATE, callback not used by endSegment.
        assertEquals(LookupTraceCorrelation.TerminalResult.IMMEDIATE, result)
        assertFalse(callbackInvoked) // Caller handles emission on IMMEDIATE path itself.
        assertTrue(correlation.isComplete())
    }

    @Test
    fun `deferred callback fires on last of multiple active segments closing`() {
        val correlation = LookupTraceCorrelation()
        assertTrue(correlation.recordLookupCookie(10))
        assertTrue(correlation.startSegment(20))
        assertTrue(correlation.startSegment(21))
        var callbackInvoked = false
        val result = correlation.recordTerminal { callbackInvoked = true }
        assertEquals(LookupTraceCorrelation.TerminalResult.DEFERRED, result)
        // First segment closes: callback must NOT fire yet.
        assertTrue(correlation.endSegment(20))
        assertFalse(callbackInvoked)
        // Last segment closes: callback fires now.
        assertTrue(correlation.endSegment(21))
        assertTrue(callbackInvoked)
        assertTrue(correlation.isComplete())
    }

    @Test
    fun `terminal follows the final suspend marker when recorded during restore`() {
        val operation = Fido2OperationTraceContext("synthetic-restore-race", 9)
        assertTrue(operation.correlation.recordLookupCookie(11))
        val events = mutableListOf<String>()
        val sink =
            object : CoroutineTraceEventSink {
                override fun resumed(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    events.add("resume")
                }

                override fun suspended(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    // Model recordTerminal racing after the segment leaves active state but
                    // before the suspend marker has finished being emitted.
                    assertEquals(
                        LookupTraceCorrelation.TerminalResult.DEFERRED,
                        operation.correlation.recordTerminal { events.add("terminal") },
                    )
                    events.add("suspend")
                }
            }
        val lifecycle =
            CoroutineTraceLifecycle(
                operation,
                AtomicInteger(300),
                sink,
                threadIdProvider = { 42L },
            )
        val state = lifecycle.updateThreadContext(kotlin.coroutines.EmptyCoroutineContext)

        lifecycle.restoreThreadContext(kotlin.coroutines.EmptyCoroutineContext, state)

        assertEquals(listOf("resume", "suspend", "terminal"), events)
        assertTrue(operation.correlation.isComplete())
    }

    @Test
    fun `lifecycle pairs every resume across dispatcher changes`() {
        runBlocking { lifecyclePairsAcrossDispatchers() }
    }

    private suspend fun lifecyclePairsAcrossDispatchers() {
        val operation = Fido2OperationTraceContext("synthetic-process", 7)
        assertTrue(operation.correlation.recordLookupCookie(9))
        val resumed = ConcurrentLinkedQueue<Pair<Int, Long>>()
        val suspended = ConcurrentLinkedQueue<Pair<Int, Long>>()
        val sink =
            object : CoroutineTraceEventSink {
                override fun resumed(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    assertTrue(paired)
                    resumed.add(traceCookie to threadId)
                }

                override fun suspended(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    assertTrue(paired)
                    suspended.add(traceCookie to threadId)
                }
            }
        val firstExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "trace-test-a") }
        val secondExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "trace-test-b") }
        val first = firstExecutor.asCoroutineDispatcher()
        val second = secondExecutor.asCoroutineDispatcher()
        try {
            val lifecycle =
                CoroutineTraceLifecycle(
                    operation,
                    AtomicInteger(100),
                    sink,
                    threadIdProvider = { System.identityHashCode(Thread.currentThread()).toLong() },
                )
            withContext(lifecycle + first) {
                withContext(second) { }
                withContext(first) { }
            }
        } finally {
            first.close()
            second.close()
        }

        assertEquals(resumed.map { it.first }.toSet(), suspended.map { it.first }.toSet())
        assertTrue(resumed.size >= 3)
        assertTrue(resumed.map { it.second }.toSet().size >= 2)
        assertFalse(operation.correlation.isComplete())
        assertEquals(LookupTraceCorrelation.TerminalResult.IMMEDIATE, operation.correlation.recordTerminal())
        assertTrue(operation.correlation.isComplete())
    }

    @Test
    fun `cancellation closes the active synthetic lifecycle segment`() {
        runBlocking { cancellationClosesLifecycleSegment() }
    }

    private suspend fun cancellationClosesLifecycleSegment() {
        val operation = Fido2OperationTraceContext("synthetic-cancel-process", 8)
        assertTrue(operation.correlation.recordLookupCookie(10))
        val events = ConcurrentLinkedQueue<String>()
        val sink =
            object : CoroutineTraceEventSink {
                override fun resumed(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    assertTrue(paired)
                    events.add("resume:$traceCookie")
                }

                override fun suspended(
                    context: Fido2OperationTraceContext,
                    traceCookie: Int,
                    threadId: Long,
                    paired: Boolean,
                ) {
                    assertTrue(paired)
                    events.add("suspend:$traceCookie")
                }
            }
        try {
            withContext(
                CoroutineTraceLifecycle(
                    operation,
                    AtomicInteger(200),
                    sink,
                    threadIdProvider = { 42L },
                ),
            ) {
                throw CancellationException("synthetic cancellation")
            }
        } catch (_: CancellationException) {
            // Expected synthetic cancellation path.
        }

        assertEquals(2, events.size)
        assertEquals(LookupTraceCorrelation.TerminalResult.IMMEDIATE, operation.correlation.recordTerminal())
        assertTrue(operation.correlation.isComplete())
    }
}
