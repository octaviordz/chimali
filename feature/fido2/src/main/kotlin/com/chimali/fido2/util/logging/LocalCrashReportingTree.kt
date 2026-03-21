package com.chimali.fido2.util.logging

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T150: Local-only crash reporting mechanism (no cloud sync).
 * Writes logs to a rotating file in the app's internal storage.
 * Implements privacy-safe logging via [PrivacyLogScrubber].
 */
class LocalCrashReportingTree(context: Context) : Timber.Tree() {

    private val logDir = File(context.filesDir, "logs")
    private val currentLogFile = File(logDir, "fido2_crash_log.txt")
    private val maxFileSize = 5L * 1024 * 1024 // 5MB limit
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val scrubbedMessage = PrivacyLogScrubber.scrub(message)
        
        // Log to Logcat if debug, but we'll let a separate DebugTree handle simple Logcatting.
        // This tree writes EVERYTHING >= INFO to the local file for post-crash analysis,
        // but especially focuses on ERRORs.
        
        if (priority < Log.INFO) {
            return
        }

        val threadName = Thread.currentThread().name
        val time = dateFormat.format(Date())
        val priorityStr = when (priority) {
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "WTF"
            else -> "V"
        }

        val formattedLog = buildString {
            append("$time [$threadName] $priorityStr/$tag: $scrubbedMessage\n")
            t?.let {
                val scrubbedTrace = PrivacyLogScrubber.scrub(Log.getStackTraceString(it))
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

            FileWriter(currentLogFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            // Cannot log this to Timber without infinite recursion. Let standard Logcat catch it.
            Timber.e(e, "Failed to write local log")
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
