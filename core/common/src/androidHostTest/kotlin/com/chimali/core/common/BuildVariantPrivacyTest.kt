package com.chimali.core.common

import android.app.Application
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildVariantPrivacyTest {
    class InvocationFailureActivityThread {
        private constructor()

        companion object {
            @JvmStatic
            fun currentApplication(): Application? = error("synthetic-invocation-marker")
        }
    }

    private class InaccessibleActivityThread {
        private constructor()

        companion object {
            @JvmStatic
            @Suppress("FunctionOnlyReturningConstant")
            fun currentApplication(): Application? = null
        }
    }

    class MissingMethodActivityThread

    @Test
    @Req("NFR-SEC-030", "FR-008", "FR-009")
    fun `reflection failure diagnostic omits throwable details`() {
        val marker = "synthetic-private-activity-thread-reflection-detail"
        val priorWriters = Logger.config.logWriterList
        val priorSeverity = Logger.config.minSeverity
        val records = mutableListOf<Triple<String, String, Throwable?>>()
        try {
            Logger.setMinSeverity(Severity.Verbose)
            Logger.setLogWriters(
                object : LogWriter() {
                    override fun log(
                        severity: Severity,
                        message: String,
                        tag: String,
                        throwable: Throwable?,
                    ) {
                        records += Triple(message, tag, throwable)
                    }
                },
            )

            assertFalse(determineAndroidDebugFlag { throw ClassNotFoundException(marker) })
            assertFalse(determineAndroidDebugFlag { MissingMethodActivityThread::class.java })
            assertFalse(determineAndroidDebugFlag { InaccessibleActivityThread::class.java })
            assertFalse(determineAndroidDebugFlag { InvocationFailureActivityThread::class.java })
            assertTrue(records.isNotEmpty())
            assertTrue(
                records.all { (message, _, throwable) ->
                    marker !in message && "synthetic-invocation-marker" !in message && throwable == null
                },
            )
        } finally {
            Logger.setLogWriters(priorWriters)
            Logger.setMinSeverity(priorSeverity)
        }
    }
}
