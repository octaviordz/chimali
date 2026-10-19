package com.chimali.fido2

import android.content.Context
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import com.chimali.fido2.util.logging.PrivacyScrubbingLogWriter
import com.chimali.fido2.util.performance.WarmUpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles module-specific initialization for the FIDO2 feature.
 */
object Fido2Initializer {
    private val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Configures local privacy-scrubbed logging and starts best-effort cryptographic warm-up. */
    @Req("NFR-SEC-010", "FR-AUTH-030")
    fun init(context: Context) {
        val writers = mutableListOf<LogWriter>()
        if (com.chimali.core.common.isDebug) {
            writers.add(PrivacyScrubbingLogWriter(LogcatWriter()))
        }

        // This writer writes EVERYTHING >= INFO to the local file for post-crash analysis,
        // and ALWAYS masks sensitive parameters via PrivacyLogScrubber.
        writers.add(
            LocalCrashReportingLogWriter(
                com.chimali.fido2.util.logging
                    .AndroidLogDirectoryProvider(context),
            ),
        )

        Logger.setLogWriters(writers)

        Logger.d("FIDO2 Logging Initialized (Local-only. Privacy Scrubbing Active)")

        // Best-effort provider warm-up; device measurements remain the source of performance evidence.
        featureScope.launch {
            WarmUpHelper.warmUpBouncyCastle()
        }
    }
}
