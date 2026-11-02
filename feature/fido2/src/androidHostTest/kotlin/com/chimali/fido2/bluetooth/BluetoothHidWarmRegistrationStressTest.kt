package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Captures an (executor, callback) registration lease allocated by [BluetoothHidDeviceWrapper.registerApp].
 *
 * Test-scoped helper: @Req annotation is not required for androidHostTest constructs per Constitution Principle V.
 */
data class CapturedRegistrationPair(
    val executor: ExecutorService,
    val callback: BluetoothHidDevice.Callback,
    val cycleIndex: Int,
    val attemptIndex: Int,
)

/**
 * Host-side behavioral harness executing N >= 10 consecutive warm registration cycles against
 * [BluetoothHidDeviceWrapper] to observe and characterize executor retention and thread accumulation.
 *
 * Implements FR-001, FR-002, and Contract §1.
 */
class BluetoothHidWarmRegistrationStressTest {
    private val capturedPairs = mutableListOf<CapturedRegistrationPair>()
    private val mockBluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
    private val mockBluetoothManager = mockk<BluetoothManager>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockHid = mockk<BluetoothHidDevice>(relaxed = true)

    private var targetRetryCycle = -1
    private var cycleAttemptCounter = 0
    private var activeCycleIndex = 0

    @BeforeTest
    fun setUp() {
        capturedPairs.clear()
        targetRetryCycle = -1
        cycleAttemptCounter = 0
        activeCycleIndex = 0

        every { mockBluetoothAdapter.state } returns BluetoothAdapter.STATE_ON
        every { mockBluetoothManager.adapter } returns mockBluetoothAdapter
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager

        every { mockHid.unregisterApp() } returns true

        // Slot/answers double capturing every (executor, callback) pair and routing events through the real executor
        every {
            mockHid.registerApp(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } answers {
            val executor = arg<Executor>(3) as ExecutorService
            val callback = arg<BluetoothHidDevice.Callback>(4)
            cycleAttemptCounter++
            val currentAttempt = cycleAttemptCounter
            val currentCycle = activeCycleIndex

            val pair =
                CapturedRegistrationPair(
                    executor = executor,
                    callback = callback,
                    cycleIndex = currentCycle,
                    attemptIndex = currentAttempt,
                )
            capturedPairs.add(pair)

            // Controlled retry: if this is the target retry cycle and attempt 1, fail framework registration
            val shouldFailAttempt = (currentCycle == targetRetryCycle && currentAttempt == 1)

            executor.submit {
                if (shouldFailAttempt) {
                    callback.onAppStatusChanged(null, false)
                } else {
                    callback.onAppStatusChanged(null, true)
                }
            }

            true
        }
    }

    @AfterTest
    fun tearDown() {
        // T009: Post-observation teardown - shut down each captured executor with a bounded timeout
        capturedPairs.forEach { pair ->
            try {
                pair.executor.shutdown()
                val terminated = pair.executor.awaitTermination(1000, TimeUnit.MILLISECONDS)
                if (!terminated) {
                    println("CLEANUP_TIMEOUT for executor in cycle ${pair.cycleIndex} attempt ${pair.attemptIndex}")
                }
            } catch (e: InterruptedException) {
                println("CLEANUP_TIMEOUT (interrupted) for executor in cycle ${pair.cycleIndex}")
                Thread.currentThread().interrupt()
            }
        }
        capturedPairs.clear()
    }

    @Test
    fun warmRegistrationStress_tenCycles_recordsExecutorTerminationState() =
        runBlocking {
            val wrapper = BluetoothHidDeviceWrapper(mockContext)
            setPrivateField(wrapper, "hidDevice", mockHid)

            val nCycles = 10
            targetRetryCycle = 3 // Designate cycle 3 to experience a controlled retry (T007)
            val rObservations = mutableListOf<Int>()

            for (c in 1..nCycles) {
                activeCycleIndex = c
                cycleAttemptCounter = 0

                // 1. Initiate wrapper cycle c (executes unmodified production registerApp)
                val registerResult = wrapper.registerApp()
                assertTrue(
                    registerResult.isSuccess,
                    "Cycle $c registerApp() must succeed (eventually via retry if retried)",
                )

                // 2. Obtain the active callback pair for this cycle
                val activePair = capturedPairs.last()
                assertEquals(c, activePair.cycleIndex)

                // 3. Initiate unregisterApp()
                wrapper.unregisterApp()

                // 4. Deliver onAppStatusChanged(registered=false) through the active executor
                val unregisterLatch = CountDownLatch(1)
                activePair.executor.submit {
                    try {
                        activePair.callback.onAppStatusChanged(null, false)
                    } finally {
                        unregisterLatch.countDown()
                    }
                }

                // 5. Await bounded callback completion synchronization (timeout = 2000 ms per cycle)
                val unregisterCompleted = unregisterLatch.await(2000, TimeUnit.MILLISECONDS)
                assertTrue(unregisterCompleted, "Cycle $c deregistration callback completion timed out")

                // 6. Record pre-cleanup intermediate observation for cycle c:
                // R_c = count of all executors captured so far that are non-terminated
                val rC = capturedPairs.count { !it.executor.isTerminated }
                rObservations.add(rC)
            }

            // T007 Assertion: verify retry cycle produced M > N allocations
            val totalM = capturedPairs.size
            assertTrue(
                totalM > nCycles,
                "Total allocations M ($totalM) must exceed cycle count N ($nCycles) " +
                    "due to retry in cycle $targetRetryCycle",
            )
            val cycle3Allocations = capturedPairs.filter { it.cycleIndex == targetRetryCycle }
            assertEquals(
                2,
                cycle3Allocations.size,
                "Cycle $targetRetryCycle must have 2 captured allocations (initial failure + retry)",
            )

            // T008: Fresh pre-cleanup R_final observation across ALL M captured executors
            val vFinal = capturedPairs.map { it.executor.isTerminated }
            val rFinal = capturedPairs.count { !it.executor.isTerminated }

            println("Pre-cleanup intermediate R_c series: $rObservations")
            println("Pre-cleanup V_final termination states: $vFinal")
            println("Pre-cleanup R_final: $rFinal of M=$totalM")

            // T010: Defect characterization verdict logging per FR-002 and Contract §1
            if (rFinal > 0) {
                println("Defect Verdict: Executor retention observed: R_final=$rFinal of M=$totalM non-terminated")
            }
            val isMonotonic = rObservations.zipWithNext().all { (prev, next) -> next >= prev }
            if (rFinal == totalM && isMonotonic) {
                println(
                    "Defect Verdict: Linear executor accumulation confirmed under harness: " +
                        "R_final=M ($rFinal == $totalM)",
                )
            }
            if (rFinal == 0) {
                println("Defect Verdict: Healthy resource bound observed: R_final=0 (target post-remediation state)")
            }

            // In uncorrected production code, Executors.newSingleThreadExecutor() is never shut down
            // Thus R_final > 0 characterizes executor retention
            assertTrue(rFinal > 0, "Expected executor retention in uncorrected production code (R_final > 0)")
            assertEquals(
                totalM,
                rFinal,
                "All allocated executors are expected to remain non-terminated prior to cleanup",
            )
            assertTrue(isMonotonic, "R_c series must be monotonically non-decreasing as unmanaged executors accumulate")
        }

    private fun setPrivateField(
        instance: Any,
        name: String,
        value: Any?,
    ) {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(instance, value)
    }
}
