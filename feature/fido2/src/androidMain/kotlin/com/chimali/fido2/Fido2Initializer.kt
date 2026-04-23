package com.chimali.fido2

import android.content.Context
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LogcatWriter
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import com.chimali.fido2.util.performance.WarmUpHelper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Handles module-specific initialization for the FIDO2 feature.
 */
object Fido2Initializer {
    @OptIn(DelicateCoroutinesApi::class)
    fun init(
        context: Context,
        isDebug: Boolean,
    ) {
        val writers = mutableListOf<LogWriter>()
        if (isDebug) {
            writers.add(LogcatWriter())
        }

        // This writer writes EVERYTHING >= INFO to the local file for post-crash analysis,
        // and ALWAYS masks sensitive parameters via PrivacyLogScrubber.
        writers.add(LocalCrashReportingLogWriter(com.chimali.fido2.util.logging.AndroidLogDirectoryProvider(context)))

        Logger.setLogWriters(writers)

        Logger.d("FIDO2 Logging Initialized (Local-only. Privacy Scrubbing Active)")

        // Warm up the BouncyCastle provider and JIT-compile the signing path.
        // This is moved to a background thread to avoid blocking the main thread during startup (NFR-PERF-030).
        GlobalScope.launch(Dispatchers.Default) {
            WarmUpHelper.warmUpBouncyCastle()
            WarmUpHelper.warmUpAndroidKeyStore()
        }
    }
}
