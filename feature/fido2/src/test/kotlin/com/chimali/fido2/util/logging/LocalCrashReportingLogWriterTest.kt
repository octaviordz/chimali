package com.chimali.fido2.util.logging

import android.content.Context
import co.touchlab.kermit.Severity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * T150 & T152: Verifies that LocalCrashReportingLogWriter correctly writes logs
 * to the internal storage and rotates files when they exceed the limit.
 */
class LocalCrashReportingLogWriterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `log writer writes to file and rotates at limit`() {
        val mockContext = mockk<Context>()
        every { mockContext.filesDir } returns tempDir

        // Use a very small 500 byte limit for testing
        val maxFileSize = 500L
        val writer = LocalCrashReportingLogWriter(mockContext, maxFileSize = maxFileSize)
        
        val logDir = File(tempDir, "logs")
        val logFile = File(logDir, "fido2_crash_log.txt")
        val bakFile = File(logDir, "fido2_crash_log.bak")

        // 1. Initial write
        writer.log(Severity.Info, "Initial log", "Tag", null)
        assertTrue(logFile.exists(), "Log file should be created")

        // 2. Cross the 500 byte limit
        // Each log entry is ~100 bytes. 20 logs = ~2000 bytes.
        val message = "This is a very safe test log message with some extra padding to ensure size."
        repeat(20) {
            writer.log(Severity.Info, message, "Tag", null)
        }
        
        // Re-instantiate File to avoid caching of metadata (length) on some filesystems
        val sizeBeforeRotation = File(logFile.absolutePath).length()
        assertTrue(sizeBeforeRotation > maxFileSize, "File should have grown beyond 500 bytes (Actual size: $sizeBeforeRotation)")

        // 3. Trigger rotation check
        writer.log(Severity.Info, "Triggering rotation", "Tag", null)

        // 4. Verify rotation
        val bakFileAfter = File(bakFile.absolutePath)
        val logFileAfter = File(logFile.absolutePath)
        
        assertTrue(bakFileAfter.exists(), "Backup file should exist after rotation")
        assertTrue(logFileAfter.exists(), "New log file should exist")
        assertTrue(logFileAfter.length() < 300, "New log file should be reset and small. Actual: ${logFileAfter.length()}")
        assertTrue(bakFileAfter.length() >= sizeBeforeRotation, "Backup should contain the previous large log")
    }
}
