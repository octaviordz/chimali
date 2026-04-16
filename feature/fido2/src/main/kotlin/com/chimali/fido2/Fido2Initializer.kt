package com.chimali.fido2

import android.content.Context
import com.chimali.fido2.util.logging.LocalCrashReportingTree
import com.chimali.fido2.util.performance.WarmUpHelper
import timber.log.Timber

/**
 * Handles module-specific initialization for the FIDO2 feature.
 */
object Fido2Initializer {
    fun init(
        context: Context,
        isDebug: Boolean,
    ) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }

        // This tree writes EVERYTHING >= INFO to the local file for post-crash analysis,
        // and ALWAYS masks sensitive parameters via PrivacyLogScrubber.
        Timber.plant(LocalCrashReportingTree(context))

        Timber.d("FIDO2 Logging Initialized (Local-only. Privacy Scrubbing Active)")

        // Warm up the BouncyCastle provider and JIT-compile the signing path.
        //
        // The first call to SHA256withECDSA via BouncyCastle incurs two one-time costs:
        //   1. Security.addProvider() — registers the BouncyCastleProvider instance with
        //      the JVM's security framework (~20ms on first use after app start).
        //   2. JIT compilation of signWithRawScalar — the JVM interprets bytecode on
        //      the first invocation before compiling it to native code (~50–120ms).
        //
        // Both costs would otherwise appear in the first real GetAssertion or
        // MakeCredential ceremony, causing an intermittent NFR-PERF-030 overrun.
        // Running a throwaway no-op sign here amortizes these costs at startup, so
        // the ceremony path always hits fully-compiled native code.
        WarmUpHelper.warmUpBouncyCastle()

        // Warm up the AndroidKeyStore TEE/HAL IPC channel.
        //
        // cryptoService.sign() uses Signature.getInstance("SHA256withECDSA", "AndroidKeyStore")
        // with a hardware-backed key. The very first call to this path per app session
        // initializes the HAL IPC binder to the TEE or StrongBox, costing ~180–360ms
        // (vs. ~170ms steady-state). warmUpBouncyCastle() does NOT help here because
        // BouncyCastle and AndroidKeyStore use completely separate engine implementations.
        //
        // This call runs a throwaway sign using a persistent P-256 key stored under
        // "chimali_fido2_hal_warmup" in AndroidKeyStore. The key is created on first install
        // and reused on every subsequent app start (5ms key lookup vs. key-gen cost).
        WarmUpHelper.warmUpAndroidKeyStore()
    }
}
