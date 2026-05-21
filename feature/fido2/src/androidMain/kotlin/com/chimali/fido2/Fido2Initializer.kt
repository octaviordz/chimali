package com.chimali.fido2

import android.content.Context
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Logger
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import com.chimali.fido2.util.performance.WarmUpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles module-specific initialization for the FIDO2 feature.
 */
object Fido2Initializer : KoinComponent {
    private val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val writers = mutableListOf<LogWriter>()
        if (com.chimali.core.common.isDebug) {
            writers.add(LogcatWriter())
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

        // Warm up the BouncyCastle provider and JIT-compile the signing path.
        // This is moved to a background thread to avoid blocking the main thread during startup (NFR-PERF-030).
        featureScope.launch {
            WarmUpHelper.warmUpBouncyCastle()
            WarmUpHelper.warmUpAndroidKeyStore()

            // Trigger the searchable metadata migration once keys are provisioned
            val masterSeedProvider: com.chimali.core.security.api.MasterSeedProvider by inject()
            val database: com.chimali.fido2.data.database.Fido2Database by inject()
            val protectionService: com.chimali.fido2.data.service.CredentialMetadataProtectionService by inject()

            // Ensuring the master seed is initialized provisions the searchable metadata symmetric keys
            if (masterSeedProvider.getMasterSeed() != null) {
                com.chimali.fido2.data.database.SearchableMetadataMigrationState
                    .migrate(database, protectionService)
            }
        }
    }
}
