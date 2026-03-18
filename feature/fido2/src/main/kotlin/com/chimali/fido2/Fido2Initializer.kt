package com.chimali.fido2

import android.content.Context
import com.chimali.fido2.util.logging.LocalCrashReportingTree
import timber.log.Timber

/**
 * Handles module-specific initialization for the FIDO2 feature.
 */
object Fido2Initializer {
    
    fun init(context: Context, isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        
        // This tree writes EVERYTHING >= INFO to the local file for post-crash analysis,
        // and ALWAYS masks sensitive parameters via PrivacyLogScrubber.
        Timber.plant(LocalCrashReportingTree(context))
        
        Timber.d("FIDO2 Logging Initialized (Local-only. Privacy Scrubbing Active)")
    }
}
