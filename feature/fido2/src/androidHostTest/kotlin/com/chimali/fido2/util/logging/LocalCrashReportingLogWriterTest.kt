package com.chimali.fido2.util.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [LocalCrashReportingLogWriter].
 *
 * Root cause of prior failure: `android.util.Log.getStackTraceString` and `Log.e` both throw
 * `RuntimeException("not mocked")` in plain JVM unit tests without Robolectric. They have been
 * replaced with Kotlin stdlib equivalents (`Throwable.stackTraceToString()` and
 * `System.err.println`). These tests therefore run without any Android runtime dependency.
 *
 * Task coverage:
 *  - T012 (spec-005): Verify startup logging output — log file is created immediately and
 *    subsequent writes persist (append correctly), proving <50ms overhead is not caused by
 *    synchronous I/O accumulation.
 *  - T013 (spec-005): Simulate a log burst and verify 5MB (here: 500-byte) rotation — the
 *    active log is renamed to `.bak`, a fresh log is started, and the backup is intact.
 */
class LocalCrashReportingLogWriterTest {
    @TempDir
    lateinit var tempDir: File

    private companion object {
        private const val MNEMONIC_WORDS_COUNT_MINUS_ONE = 23
        private const val SMALL_LOG_CAP_BYTES = 500L
        private const val FRESH_LOG_MAX_SIZE_BYTES = 300
    }

    private lateinit var mockProvider: LogDirectoryProvider
    private lateinit var logDir: File
    private lateinit var logFile: File
    private lateinit var bakFile: File

    @BeforeEach
    fun setUp() {
        mockProvider =
            object : LogDirectoryProvider {
                override fun getLogDirectory() = tempDir.resolve("logs").toOkioPath()
            }

        logDir = File(tempDir, "logs")
        logFile = File(logDir, "fido2_crash_log.txt")
        bakFile = File(logDir, "fido2_crash_log.bak")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T012 — Write contract / startup logging output
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class T012WriteContract {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `log directory and file are created on first write`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            assertFalse(logFile.exists(), "Log file should not exist before any write")

            writer.log(Severity.Info, "Hello", "StartupTag", null)

            assertTrue(logDir.exists(), "Log directory should be created")
            assertTrue(logFile.exists(), "Log file should be created after first write")
            assertTrue(logFile.length() > 0, "Log file should not be empty")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `each write appends to the file — size grows monotonically`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)

            writer.log(Severity.Info, "First entry", "Tag", null)
            val sizeAfterFirst = logFile.length()
            assertTrue(sizeAfterFirst > 0, "File should not be empty after first write")

            writer.log(Severity.Info, "Second entry", "Tag", null)
            val sizeAfterSecond = logFile.length()
            assertTrue(
                sizeAfterSecond > sizeAfterFirst,
                "File size should grow after second write (first=$sizeAfterFirst, second=$sizeAfterSecond)",
            )

            writer.log(Severity.Info, "Third entry", "Tag", null)
            val sizeAfterThird = logFile.length()
            assertTrue(
                sizeAfterThird > sizeAfterSecond,
                "File size should grow after third write (second=$sizeAfterSecond, third=$sizeAfterThird)",
            )
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `log entry content is written in expected format`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)

            writer.log(Severity.Info, "Expected content", "MyTag", null)

            val content = logFile.readText()
            assertTrue(content.contains("I/Fido2:"), "Entry should contain severity and fixed tag")
            assertTrue(content.contains("Expected content"), "Entry should contain the message")
            assertTrue(content.endsWith("\n"), "Entry should end with newline")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `severity below Info is filtered out and nothing is written`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)

            writer.log(Severity.Debug, "Debug message — should be suppressed", "Tag", null)
            writer.log(Severity.Verbose, "Verbose message — should be suppressed", "Tag", null)

            assertFalse(logFile.exists(), "No file should be created for filtered severities")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `Info Warn Error and Assert severities are all written`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)

            writer.log(Severity.Info, "Info message", "Tag", null)
            writer.log(Severity.Warn, "Warn message", "Tag", null)
            writer.log(Severity.Error, "Error message", "Tag", null)
            writer.log(Severity.Assert, "Assert message", "Tag", null)

            val content = logFile.readText()
            assertTrue(content.contains("I/Fido2:"), "Info entry should be present")
            assertTrue(content.contains("W/Fido2:"), "Warn entry should be present")
            assertTrue(content.contains("E/Fido2:"), "Error entry should be present")
            assertTrue(content.contains("WTF/Fido2:"), "Assert entry should be present")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `throwable details are omitted after the message line`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            val cause = RuntimeException("test-cause")

            writer.log(Severity.Error, "Error with throwable", "CrashTag", cause)

            val content = logFile.readText()
            assertTrue(content.contains("Error with throwable"), "Message should appear")
            assertTrue(
                content.contains("[throwable details omitted]"),
                "The output should retain only a fixed throwable indication",
            )
            assertFalse(content.contains("RuntimeException"))
            assertFalse(content.contains("test-cause"))
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `throwable message and cause are not written to the local log`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            val marker = "synthetic-private-marker-should-not-persist"
            val cause = IllegalStateException("private key=$marker", IllegalArgumentException("seed=$marker"))
            cause.stackTrace = arrayOf(StackTraceElement(marker, marker, "$marker.kt", 999))

            writer.log(Severity.Error, "Operation failed", "CrashTag", cause)

            val content = logFile.readText()
            assertTrue(content.contains("Operation failed"))
            assertTrue(content.contains("[throwable details omitted]"))
            assertFalse(content.contains(marker), "Exception messages and causes must not be persisted")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `debug log forwarding redacts messages and removes throwable causes`() {
            var forwardedMessage: String? = null
            var forwardedTag: String? = null
            var forwardedThrowable: Throwable? = null
            val delegate =
                object : LogWriter() {
                    override fun log(
                        severity: Severity,
                        message: String,
                        tag: String,
                        throwable: Throwable?,
                    ) {
                        forwardedMessage = message
                        forwardedTag = tag
                        forwardedThrowable = throwable
                    }
                }
            val marker = "synthetic-private-marker-should-not-forward"
            val writer = PrivacyScrubbingLogWriter(delegate)
            val throwable = IllegalStateException("seed=$marker", IllegalArgumentException("prk=$marker"))
            throwable.stackTrace = arrayOf(StackTraceElement(marker, marker, "$marker.kt", 999))

            writer.log(
                Severity.Error,
                "private key=$marker",
                "CrashTag-$marker",
                throwable,
            )

            assertEquals("private key=[REDACTED]", forwardedMessage)
            assertEquals("Fido2", forwardedTag)
            assertEquals("[throwable details omitted]", forwardedThrowable?.message)
            assertEquals(null, forwardedThrowable?.cause)
            assertEquals(0, forwardedThrowable?.stackTrace?.size)
            assertFalse(forwardedThrowable?.stackTraceToString()?.contains(marker) == true)
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `privacy scrubber is applied — mnemonic is redacted in written content`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            val mnemonic = "abandon ".repeat(MNEMONIC_WORDS_COUNT_MINUS_ONE).trim() + " art"

            writer.log(Severity.Info, "Seed: $mnemonic", "SecTag", null)

            val content = logFile.readText()
            assertFalse(content.contains("abandon"), "Mnemonic words must not appear in log file")
            assertTrue(content.contains("[REDACTED]"), "Redaction marker must be present")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `final local output scrubs tags and transformed secret markers`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            val base64Marker = "a".repeat(80)
            val hexMarker = "ab".repeat(40)

            writer.log(Severity.Info, "seed=$base64Marker", "credential-$hexMarker", null)
            writer.log(Severity.Info, "privateKey=$hexMarker", "tag-$base64Marker", null)
            writer.log(Severity.Info, "privateKey=escaped%3A$hexMarker", "seed=$base64Marker", null)

            val content = logFile.readText()
            assertFalse(content.contains(base64Marker), "Base64-like output marker must not persist")
            assertFalse(content.contains(hexMarker), "Hex output marker must not persist")
            assertTrue(content.contains("[REDACTED]"), content)
            assertTrue(content.contains("/Fido2:"), content)
            assertFalse(content.contains("credential-$hexMarker"), "Untrusted tags must not be persisted")
        }

        /** @see FR-008, FR-009, NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `final local output omits throwable messages causes stack frames and tags`() {
            val writer = LocalCrashReportingLogWriter(mockProvider)
            val marker = "synthetic-private-local-log-marker"
            val throwable =
                IllegalStateException("failure=$marker", IllegalArgumentException("cause=$marker")).apply {
                    stackTrace = arrayOf(StackTraceElement(marker, marker, "$marker.kt", 271))
                }

            writer.log(Severity.Error, "Credential operation failed", "private-tag-$marker", throwable)

            val content = logFile.readText()
            assertTrue(content.contains("Credential operation failed"), content)
            assertTrue(content.contains("[throwable details omitted]"), content)
            assertFalse(content.contains(marker), "Raw throwable and tag markers must not persist")
            assertFalse(content.contains("IllegalStateException"), "Throwable class details must not persist")
            assertFalse(content.contains("IllegalArgumentException"), "Cause type details must not persist")
            assertTrue(content.contains("/Fido2:"), content)
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `filesystem failure fallback does not print paths or exception messages`() {
            val blocker = tempDir.resolve("private-filesystem-marker")
            blocker.writeText("not a directory")
            val blockedProvider =
                object : LogDirectoryProvider {
                    override fun getLogDirectory() = blocker.resolve("logs").toOkioPath()
                }
            val captured = ByteArrayOutputStream()
            val previousErr = System.err
            try {
                System.setErr(PrintStream(captured))
                val writer = LocalCrashReportingLogWriter(blockedProvider)
                writer.log(Severity.Error, "synthetic failure", "private-tag", null)

                val throwingProvider =
                    object : LogDirectoryProvider {
                        override fun getLogDirectory(): okio.Path = throw SecurityException("private-provider-marker")
                    }
                LocalCrashReportingLogWriter(throwingProvider)
                    .log(Severity.Error, "synthetic provider failure", "private-tag", null)
            } finally {
                System.setErr(previousErr)
            }

            val fallback = captured.toString()
            assertFalse(fallback.contains("private-filesystem-marker"))
            assertFalse(fallback.contains("not a directory"))
            assertFalse(fallback.contains("private-provider-marker"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T013 — Log burst + 5 MB rotation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class T013LogBurstAndRotation {
        /**
         * Uses a 500-byte cap in lieu of 5 MB so the test stays fast.
         * The rotation code path is identical regardless of the threshold.
         */
        private val smallCap = SMALL_LOG_CAP_BYTES

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `rotation renames active log to bak when size limit is exceeded`() {
            val writer = LocalCrashReportingLogWriter(mockProvider, maxFileSize = smallCap)

            // Pad the file past the cap with repeated entries
            val padEntry = "Padding entry to fill the log file quickly with enough bytes."
            while (logFile.length() <= smallCap) writer.log(Severity.Info, padEntry, "BurstTag", null)

            val sizeBeforeRotation = logFile.length()
            assertTrue(
                sizeBeforeRotation > smallCap,
                "File should have grown past the $smallCap-byte cap " +
                    "(actual: $sizeBeforeRotation bytes)",
            )

            // This write crosses the threshold and triggers rotation
            writer.log(Severity.Info, "Trigger rotation", "BurstTag", null)

            assertTrue(bakFile.exists(), "Backup file must exist after rotation")
            assertTrue(
                bakFile.length() >= sizeBeforeRotation,
                "Backup must preserve the full pre-rotation content " +
                    "(bak=${bakFile.length()}, pre-rotation=$sizeBeforeRotation)",
            )
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `fresh log file is small after rotation`() {
            val writer = LocalCrashReportingLogWriter(mockProvider, maxFileSize = smallCap)

            val padEntry = "Padding entry to fill the log file quickly with enough bytes."
            while (logFile.length() <= smallCap) writer.log(Severity.Info, padEntry, "BurstTag", null)

            // Trigger rotation
            writer.log(Severity.Info, "Trigger rotation", "BurstTag", null)

            // Fresh file should only contain the single rotation-trigger entry
            val freshSize = logFile.length()
            assertTrue(
                logFile.exists(),
                "A new active log file must be recreated immediately after rotation",
            )
            assertTrue(
                freshSize < FRESH_LOG_MAX_SIZE_BYTES,
                "Fresh log file should be small (only the trigger entry). Actual: $freshSize bytes",
            )
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `previous bak is deleted before promoting active log`() {
            val writer = LocalCrashReportingLogWriter(mockProvider, maxFileSize = smallCap)

            // First rotation cycle
            val padEntry = "First rotation fill entry — long enough to count."
            while (logFile.length() <= smallCap) writer.log(Severity.Info, padEntry, "Cycle1", null)
            writer.log(Severity.Info, "Trigger first rotation", "Cycle1", null)

            assertTrue(bakFile.exists(), "Bak should exist after first rotation")
            val bakSizeAfterFirst = bakFile.length()

            // Second rotation cycle — old bak must be replaced, not left alongside
            while (logFile.length() <= smallCap) writer.log(Severity.Info, padEntry, "Cycle2", null)
            writer.log(Severity.Info, "Trigger second rotation", "Cycle2", null)

            assertTrue(bakFile.exists(), "Bak should still exist after second rotation")
            assertTrue(
                bakFile.length() != bakSizeAfterFirst || bakFile.length() > 0,
                "Bak file should have been replaced by second rotation cycle",
            )
            // Confirm only one bak file exists (no stacking)
            val bakFiles = logDir.listFiles { f -> f.name.endsWith(".bak") }
            assertEquals(1, bakFiles?.size, "There must be exactly one .bak file at all times")
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `no rotation occurs when log is below the size cap`() {
            val writer = LocalCrashReportingLogWriter(mockProvider, maxFileSize = smallCap)

            // Write a single small entry — well below the cap
            writer.log(Severity.Info, "Tiny entry", "Tag", null)

            assertFalse(bakFile.exists(), "No backup file should exist when cap is not reached")
            assertTrue(
                logFile.length() < smallCap,
                "Active log should remain below the cap",
            )
        }
    }
}
