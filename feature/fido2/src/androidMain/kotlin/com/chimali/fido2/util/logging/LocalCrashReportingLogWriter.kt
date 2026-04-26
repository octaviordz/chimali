package com.chimali.fido2.util.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * Local-only crash reporting mechanism (no cloud sync).
 * Writes logs to a rotating file in the app's internal storage.
 * Implements privacy-safe logging via [PrivacyLogScrubber].
 */
@Suppress("TooGenericExceptionCaught")
class LocalCrashReportingLogWriter(
    private val directoryProvider: LogDirectoryProvider,
    // 5MB limit default
    private val maxFileSize: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
) : LogWriter() {
    private val fileSystem = FileSystem.SYSTEM
    private val logDir: Path
        get() = directoryProvider.getLogDirectory()

    private val currentLogFile: Path
        get() = logDir.resolve("fido2_crash_log.txt")

    private val platformLock = PlatformLock()

    init {
        try {
            if (!fileSystem.exists(logDir)) {
                fileSystem.createDirectories(logDir)
            }
        } catch (e: Exception) {
            // Silently fail or print to stderr if directory creation fails
            println("CrashReportingWriter: Failed to create log directory: ${e.message}")
        }
    }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val scrubbedMessage = PrivacyLogScrubber.scrub(message)

        // This writer writes EVERYTHING >= INFO to the local file for post-crash analysis
        if (severity < Severity.Info) {
            return
        }

        // KMP has no Thread.currentThread().name standardly available in commonMain
        val currentInstant = Clock.System.now()
        val time = currentInstant.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        val priorityStr =
            when (severity) {
                Severity.Info -> "I"
                Severity.Warn -> "W"
                Severity.Error -> "E"
                Severity.Assert -> "WTF"
                else -> "V"
            }

        val formattedLog =
            buildString {
                append("$time $priorityStr/$tag: $scrubbedMessage\n")
                throwable?.let {
                    val scrubbedTrace = PrivacyLogScrubber.scrub(it.stackTraceToString())
                    append("$scrubbedTrace\n")
                }
            }

        writeToFile(formattedLog)
    }

    private fun writeToFile(logEntry: String) {
        platformLock.withLock {
            try {
                // Check size and rotate if needed
                if (fileSystem.exists(currentLogFile)) {
                    val metadata = fileSystem.metadataOrNull(currentLogFile)
                    val size = metadata?.size ?: 0L
                    if (size > maxFileSize) {
                        rotateLogs()
                    }
                }

                // Append to file
                val sink = fileSystem.appendingSink(currentLogFile).buffer()
                try {
                    sink.writeUtf8(logEntry)
                    sink.flush()
                } catch (e: Exception) {
                    println("CrashReportingWriter: Write error: ${e.message}")
                } finally {
                    try {
                        sink.close()
                    } catch (ignored: Exception) {
                    }
                }
            } catch (e: Exception) {
                // Fallback for debugging writer issues
                println("CrashReportingWriter: Failed to write local log: ${e.message}")
            }
        }
    }

    private fun rotateLogs() {
        val backupFile = logDir.resolve("fido2_crash_log.bak")
        try {
            if (fileSystem.exists(backupFile)) {
                fileSystem.delete(backupFile)
            }
            if (fileSystem.exists(currentLogFile)) {
                fileSystem.atomicMove(currentLogFile, backupFile)
            }
        } catch (e: Exception) {
            println("CrashReportingWriter: Failed to rotate logs: ${e.message}")
        }
    }

    companion object {
        private const val DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024
    }
}
