package com.chimali.fido2.util.logging

import android.content.Context
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T150: Local-only crash reporting mechanism (no cloud sync).
 * Writes logs to a rotating file in the app's internal storage.
 * Implements privacy-safe logging via [PrivacyLogScrubber].
 */
class LocalCrashReportingLogWriter(
    context: Context,
    private val maxFileSize: Long = 5L * 1024 * 1024, // 5MB limit default
) : LogWriter() {
    private val logDir = File(context.filesDir, "logs")
    private val currentLogFile = File(logDir, "fido2_crash_log.txt")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        val scrubbedMessage = PrivacyLogScrubber.scrub(message)

        // This writer writes EVERYTHING >= INFO to the local file for post-crash analysis
        if (severity < Severity.Info) {
            return
        }

        val threadName = Thread.currentThread().name
        val time = dateFormat.format(Date())
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
                append("$time [$threadName] $priorityStr/$tag: $scrubbedMessage\n")
                throwable?.let {
                    // stackTraceToString() is Kotlin stdlib — no Android dependency, safe in JVM unit tests.
                    val scrubbedTrace = PrivacyLogScrubber.scrub(it.stackTraceToString())
                    append("$scrubbedTrace\n")
                }
            }

        writeToFile(formattedLog)
    }

    @Synchronized
    private fun writeToFile(logEntry: String) {
        try {
            // Check size and rotate if needed
            if (currentLogFile.exists() && currentLogFile.length() > maxFileSize) {
                rotateLogs()
            }

            FileOutputStream(currentLogFile, true).bufferedWriter().use { writer ->
                writer.write(logEntry)
            }
        } catch (e: Exception) {
            // Fallback for debugging writer issues
            System.err.println("CrashReportingWriter: Failed to write local log: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun rotateLogs() {
        val backupFile = File(logDir, "fido2_crash_log.bak")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        currentLogFile.renameTo(backupFile)
    }
}
