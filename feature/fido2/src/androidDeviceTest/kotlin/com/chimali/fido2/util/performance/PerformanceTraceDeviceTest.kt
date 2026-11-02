package com.chimali.fido2.util.performance

import android.os.Build
import android.os.Trace
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Validates Android trace emission using synthetic operation identifiers and no RP traffic. */
@RunWith(AndroidJUnit4::class)
class PerformanceTraceDeviceTest {
    @Test
    fun syntheticLookupEmitsAndPairsAndroidTraceLifecycleWhenTracingIsEnabled() =
        runBlocking {
            assumeTrue(
                "Async app trace sections require Android Q or newer",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            )
            assumeTrue("Start Perfetto with this test app in atrace_apps", Trace.isEnabled())

            val operation =
                Fido2OperationTraceContext(
                    "synthetic-device-probe",
                    SYNTHETIC_OPERATION_COOKIE,
                )
            withContext(operation) {
                PerformanceTrace.section(LOOKUP_SECTION) {
                    delay(SUSPENSION_DELAY_MILLIS)
                    withContext(Dispatchers.IO) { Thread.yield() }
                }
            }

            assertTrue(operation.correlation.hasLookupMapping())
            assertTrue(operation.correlation.isComplete())
        }

    private companion object {
        const val LOOKUP_SECTION = "Fido2.Registration.SettingsLookup"
        const val SYNTHETIC_OPERATION_COOKIE = 840062
        const val SUSPENSION_DELAY_MILLIS = 5L
    }
}
