@file:Suppress("ForbiddenImport")

package com.chimali.security

import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.logDeniedStartupPermissions
import java.io.FileInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the permission warning at Android's direct Logcat sink. */
@RunWith(AndroidJUnit4::class)
class MainActivityPermissionDiagnosticInstrumentedTest {
    @Test
    fun deniedPermissionWarningOmitsSyntheticKeyFromLogcat() {
        val marker = "SEC065_PERMISSION_${System.currentTimeMillis()}"
        val begin = "SEC065_PERMISSION_BEGIN_${System.currentTimeMillis()}"
        val end = "SEC065_PERMISSION_END_${System.currentTimeMillis()}"
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        Log.i(TEST_TAG, begin)
        logDeniedStartupPermissions(mapOf(marker to false)) { tag, message -> Log.w(tag, message) }
        Log.i(TEST_TAG, end)

        val logOutput =
            instrumentation.uiAutomation
                .executeShellCommand("logcat -d -t 100 --pid ${Process.myPid()} -v raw")
                .use { descriptor -> FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() } }
        val beginIndex = logOutput.lastIndexOf(begin)
        val endIndex = logOutput.indexOf(end, beginIndex + begin.length)

        assertTrue("The scoped Logcat capture must contain the test start marker", beginIndex >= 0)
        assertTrue("The scoped Logcat capture must contain the test end marker", endIndex > beginIndex)
        val emittedSegment = logOutput.substring(beginIndex + begin.length, endIndex)
        assertTrue(
            "The fixed startup warning must reach Android Logcat",
            emittedSegment.contains(EXPECTED_WARNING),
        )
        assertFalse(
            "The synthetic permission name must not reach Android Logcat",
            emittedSegment.contains(marker),
        )
    }

    private companion object {
        const val TEST_TAG = "SEC065-PermissionAudit"
        const val EXPECTED_WARNING = "Some startup permissions were denied at startup."
    }
}
