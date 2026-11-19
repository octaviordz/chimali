package com.chimali.security

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host-controller-driven preflight for observer separation and metadata-only controls. */
class ClipboardObserverIsolationTest {
    @Test
    fun observerControlsRunOutsideTargetProcessAndExposeOnlyClassification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testPackage = instrumentation.context.packageName
        val targetPackage = instrumentation.targetContext.packageName
        val runId = "control-${UUID.randomUUID()}"
        val expectedFile = "files/clipboard-observer.expected"
        val resultFile = "files/clipboard-observer-result-$runId.json"

        assertNotEquals("Observer must be installed as a separate test package", targetPackage, testPackage)
        assumeTrue(
            "The serialized host controller must provision the observer's private token file",
            shell("run-as $testPackage test -r $expectedFile && echo ready").contains("ready"),
        )

        val match = launchAndRead(testPackage, runId, resultFile, ClipboardObserverActivity.MODE_CONTROL_MATCH)
        assertEquals("marker-match", match.getString("classification"))
        assertEquals("marker-match", match.getString("capabilityControl"))
        assertNotEquals("Observer must use its declared isolated process", Process.myPid(), match.getInt("observerPid"))
        assertMetadataOnly(match)

        val mismatch = launchAndRead(testPackage, runId, resultFile, ClipboardObserverActivity.MODE_CONTROL_MISMATCH)
        assertEquals("replaced", mismatch.getString("classification"))
        assertEquals("replaced", mismatch.getString("capabilityControl"))
        assertNotEquals(
            "Observer must remain outside the target process",
            Process.myPid(),
            mismatch.getInt("observerPid"),
        )
        assertMetadataOnly(mismatch)
    }

    private fun launchAndRead(
        testPackage: String,
        runId: String,
        resultFile: String,
        mode: String,
    ): JSONObject {
        val component = "$testPackage/${ClipboardObserverActivity::class.java.name}"
        val command =
            listOf(
                "am start -W -n",
                component,
                "--es ${ClipboardObserverActivity.EXTRA_RUN_ID}",
                runId,
                "--es ${ClipboardObserverActivity.EXTRA_MODE}",
                mode,
            ).joinToString(" ")
        val start =
            shell(command)
        assertTrue("Observer activity launch failed", start.contains("Status: ok"))

        val deadline = SystemClockForTest.elapsedRealtime() + 10_000L
        var lastOutput = ""
        while (SystemClockForTest.elapsedRealtime() < deadline) {
            lastOutput = shell("run-as $testPackage cat $resultFile")
            runCatching { return JSONObject(lastOutput) }
            Thread.sleep(100L)
        }
        throw AssertionError("Observer did not emit a result record: $lastOutput")
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        } finally {
            descriptor.close()
        }
    }

    private fun assertMetadataOnly(record: JSONObject) {
        assertEquals(
            setOf(
                "runId",
                "elapsedRealtimeMs",
                "observerPid",
                "classification",
                "windowFocused",
                "deviceLocked",
                "capabilityControl",
                "cleanupOutcome",
            ),
            record.keys().asSequence().toSet(),
        )
        assertEquals("not-requested", record.getString("cleanupOutcome"))
        assertTrue(record.getString("classification") in setOf("marker-match", "replaced", "absent", "inaccessible"))
    }

    private object SystemClockForTest {
        fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
    }
}
