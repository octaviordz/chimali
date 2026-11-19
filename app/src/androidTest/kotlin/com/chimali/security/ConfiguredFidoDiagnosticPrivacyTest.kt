package com.chimali.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chimali.fido2.util.logging.AndroidLogDirectoryProvider
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the writers installed by ChimaliApplication in the actual target process. */
@RunWith(AndroidJUnit4::class)
class ConfiguredFidoDiagnosticPrivacyTest {
    @Test
    fun configuredWritersRedactSyntheticValuesAndOmitThrowableDetails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val marker = requireNotNull(InstrumentationRegistry.getArguments().getString("syntheticMarker"))
        val runId = requireNotNull(InstrumentationRegistry.getArguments().getString("syntheticRunId"))
        val expectedDebug =
            requireNotNull(
                InstrumentationRegistry.getArguments().getString("expectedDebug"),
            ).toBooleanStrict()
        require(marker.matches(Regex("SEC065_[A-Z0-9_]{12,64}")))
        require(runId.matches(Regex("SEC065_RUN_[A-Z0-9_]{12,64}")))
        val targetDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        assertEquals("The installed target must have the expected build flag", expectedDebug, targetDebuggable)

        val logFile = context.fidoCrashLogFile()
        val startingLength = if (logFile.exists()) logFile.length() else 0L
        Logger.log(
            Severity.Info,
            "Fido2",
            null,
            "SEC065 run=$runId password=$marker,comma-suffix=$marker; semicolon-suffix=$marker\nembedded-suffix=$marker",
        )
        Logger.log(
            Severity.Warn,
            "Fido2",
            null,
            "SEC065 run=$runId seed=$marker; adjacent-password=$marker\nmultiline-suffix=$marker",
        )
        val syntheticThrowable =
            IllegalStateException("password=$marker", IllegalArgumentException("seed=$marker")).apply {
                stackTrace = arrayOf(StackTraceElement(marker, marker, "$marker.kt", 999))
            }
        Logger.log(
            Severity.Error,
            "SEC065_TAG_$marker",
            syntheticThrowable,
            "SEC065 run=$runId password=$marker,throwable-suffix=$marker\nerror-suffix=$marker",
        )
        if (targetDebuggable) {
            val appended = logFile.readFrom(if (logFile.length() < startingLength) 0 else startingLength)
            assertTrue(
                "INFO diagnostics must reach the configured local file",
                appended.contains("run=$runId password=[REDACTED]"),
            )
            assertTrue(
                "WARN diagnostics must reach the configured local file",
                appended.contains("run=$runId seed=[REDACTED]"),
            )
            assertTrue(
                "Throwable details must be replaced with fixed wording",
                appended.contains("[throwable details omitted]"),
            )
            assertFalse(
                "Sensitive synthetic values must not reach the configured local file",
                appended.contains(marker),
            )
            val logcatOutput = instrumentation.readFilteredLogcat(runId)
            assertTrue(
                "INFO diagnostics must reach Logcat in redacted form",
                logcatOutput.contains("run=$runId password=[REDACTED]"),
            )
            assertTrue(
                "WARN diagnostics must reach Logcat in redacted form",
                logcatOutput.contains("run=$runId seed=[REDACTED]"),
            )
            assertFalse("Logcat must not contain the synthetic marker", logcatOutput.contains(marker))
        } else {
            val startupDeadline = System.currentTimeMillis() + LOCAL_LOG_STARTUP_TIMEOUT_MILLIS
            var appLog = logFile.readFrom(0)
            while (!appLog.contains(RELEASE_STARTUP_MARKER) && System.currentTimeMillis() < startupDeadline) {
                Thread.sleep(STARTUP_POLL_INTERVAL_MILLIS)
                appLog = logFile.readFrom(0)
            }
            assertTrue(
                "Release FIDO diagnostics must reach app-private storage",
                appLog.contains(RELEASE_STARTUP_MARKER),
            )
            val appended = logFile.readFrom(if (logFile.length() < startingLength) 0 else startingLength)
            assertTrue(
                "Release INFO diagnostics must reach the configured local file in redacted form",
                appended.contains("run=$runId password=[REDACTED]"),
            )
            assertTrue(
                "Release WARN diagnostics must reach the configured local file in redacted form",
                appended.contains("run=$runId seed=[REDACTED]"),
            )
            assertTrue(
                "Release throwable details must be replaced with fixed wording",
                appended.contains("[throwable details omitted]"),
            )
            assertFalse("Release app-private output must not contain the synthetic marker", appended.contains(marker))
            val logcatOutput = instrumentation.readFilteredLogcat(runId)
            assertFalse("Release FIDO diagnostics must not reach Logcat", logcatOutput.contains(runId))
            assertFalse("Release Logcat must not contain the synthetic marker", logcatOutput.contains(marker))
        }
    }

    @Test
    fun configuredFileWriterRotationAndFallbackOmitSyntheticValuesAndFailures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = requireNotNull(InstrumentationRegistry.getArguments().getString("syntheticMarker"))
        val runId = requireNotNull(InstrumentationRegistry.getArguments().getString("syntheticRunId"))
        require(marker.matches(Regex("SEC065_[A-Z0-9_]{12,64}")))
        require(runId.matches(Regex("SEC065_RUN_[A-Z0-9_]{12,64}")))

        val runDirectory = context.filesDir.resolve("security-audit-$runId")
        assertTrue("The test directory must be unique", runDirectory.mkdir())
        try {
            val logs = runDirectory.resolve("logs")
            val isolatedContext =
                object : ContextWrapper(context) {
                    override fun getFilesDir(): File = runDirectory
                }
            val writer =
                LocalCrashReportingLogWriter(
                    AndroidLogDirectoryProvider(isolatedContext),
                    // The scrubber shortens the synthetic long-padding value, so keep the
                    // threshold below the fixed timestamp/throwable overhead to force rotation.
                    maxFileSize = 64L,
                )
            val throwable = IllegalStateException("private path=$marker")
            val message = "SEC065 run=$runId password=$marker padding=${"x".repeat(192)}"
            writer.log(Severity.Error, message, "SEC065_TAG_$marker", throwable)
            writer.log(Severity.Error, message, "SEC065_TAG_$marker", throwable)

            val active = logs.resolve("fido2_crash_log.txt").readText()
            val rotated = logs.resolve("fido2_crash_log.bak").readText()
            for (content in listOf(active, rotated)) {
                assertTrue(
                    "Both active and rotated files must contain the redacted record",
                    content.contains("password=[REDACTED]"),
                )
                assertTrue("Throwable output must use fixed wording", content.contains("[throwable details omitted]"))
                assertFalse("No synthetic marker may appear in a local log", content.contains(marker))
            }

            val blocker = runDirectory.resolve("private-filesystem-marker")
            blocker.writeText("not a directory")
            assertTrue("The fallback blocker must be created", blocker.isFile)
            val failingContext =
                object : ContextWrapper(context) {
                    override fun getFilesDir(): File = blocker
                }
            val captured = ByteArrayOutputStream()
            val previousErr = System.err
            try {
                System.setErr(PrintStream(captured))
                val failingWriter =
                    LocalCrashReportingLogWriter(
                        AndroidLogDirectoryProvider(failingContext),
                    )
                failingWriter.log(Severity.Error, "password=$marker", "SEC065_TAG_$marker", throwable)
            } finally {
                System.setErr(previousErr)
            }
            val fallback = captured.toString()
            assertTrue(
                "Filesystem fallback must be fixed",
                fallback.contains("CrashReportingWriter: log directory unavailable"),
            )
            assertFalse(
                "Fallback output must omit synthetic message/tag/throwable values",
                fallback.contains(marker),
            )
            assertFalse(
                "Fallback output must omit private paths",
                fallback.contains("private-filesystem-marker"),
            )
            assertFalse("Fallback output must omit exception details", fallback.contains("not a directory"))
        } finally {
            runDirectory.deleteRecursively()
        }
    }

    private fun Context.fidoCrashLogFile() = filesDir.resolve("logs").resolve("fido2_crash_log.txt")

    private fun android.app.Instrumentation.readFilteredLogcat(runId: String): String {
        val descriptor = uiAutomation.executeShellCommand("logcat -d -t 500 -v brief")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .useLines { lines -> lines.filter { it.contains(runId) }.joinToString(System.lineSeparator()) }
    }

    private companion object {
        const val RELEASE_STARTUP_MARKER = "Registering bundled BouncyCastle provider"
        const val LOCAL_LOG_STARTUP_TIMEOUT_MILLIS = 5_000L
        const val STARTUP_POLL_INTERVAL_MILLIS = 100L
    }

    private fun java.io.File.readFrom(offset: Long): String {
        if (!exists() || length() <= offset) return ""
        return RandomAccessFile(this, "r").use { file ->
            file.seek(offset)
            ByteArray((file.length() - offset).toInt()).also(file::readFully).decodeToString()
        }
    }
}
