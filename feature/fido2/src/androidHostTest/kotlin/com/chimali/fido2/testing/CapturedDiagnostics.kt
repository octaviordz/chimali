package com.chimali.fido2.testing

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlin.test.assertFalse

/** Captures unsanitized source diagnostics and restores global logging after each test. */
class CapturedDiagnostics : AutoCloseable {
    private val originalWriters = Logger.config.logWriterList
    private val originalSeverity = Logger.config.minSeverity
    private val records = mutableListOf<String>()

    init {
        Logger.setMinSeverity(Severity.Verbose)
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    records.add(message)
                    throwable?.let { records.add(it.stackTraceToString()) }
                }
            },
        )
    }

    /** Number of actual source log/throwable records, for non-vacuity assertions. */
    val size: Int get() = records.size

    /** Verifies synthetic input markers never reached the logger, including throwable chains. */
    fun assertNoSensitiveValues(vararg markers: String) {
        markers.forEach { marker ->
            assertFalse(records.any { marker in it }, "Synthetic sensitive input reached a diagnostic sink")
        }
    }

    override fun close() {
        Logger.setLogWriters(originalWriters)
        Logger.setMinSeverity(originalSeverity)
        records.clear()
    }
}
