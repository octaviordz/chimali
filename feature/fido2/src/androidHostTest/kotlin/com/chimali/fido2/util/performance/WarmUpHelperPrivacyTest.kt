package com.chimali.fido2.util.performance

import co.touchlab.kermit.Logger
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.util.crypto.BouncyCastleLoader
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import com.chimali.fido2.util.logging.LogDirectoryProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir

class WarmUpHelperPrivacyTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `warmup failure logs a fixed message without the provider exception text`() {
        val exceptionMarker = "synthetic-private-provider-error"
        val priorWriters = Logger.mutableConfig.logWriterList
        val localLog = tempDir.resolve("logs/fido2_crash_log.txt")
        try {
            Logger.setLogWriters(
                LocalCrashReportingLogWriter(
                    object : LogDirectoryProvider {
                        override fun getLogDirectory(): Path = tempDir.resolve("logs").toOkioPath()
                    },
                ),
            )
            mockkObject(BouncyCastleLoader)
            every { BouncyCastleLoader.ensureRegistered() } throws GeneralSecurityException(exceptionMarker)

            WarmUpHelper.warmUpBouncyCastle()

            val output = localLog.readText()
            assertTrue(output.contains("BouncyCastle warm-up FAILED (non-fatal): operation failure"))
            assertFalse(output.contains(exceptionMarker))
        } finally {
            unmockkObject(BouncyCastleLoader)
            Logger.setLogWriters(priorWriters)
        }
    }

    @Test
    fun `warmup failure does not attach provider throwable to raw diagnostic writers`() {
        val exceptionMarker = "synthetic-private-provider-throwable"
        try {
            mockkObject(BouncyCastleLoader)
            every { BouncyCastleLoader.ensureRegistered() } throws GeneralSecurityException(exceptionMarker)

            CapturedDiagnostics().use { diagnostics ->
                WarmUpHelper.warmUpBouncyCastle()
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(exceptionMarker)
            }
        } finally {
            unmockkObject(BouncyCastleLoader)
        }
    }
}
