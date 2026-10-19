package com.chimali.fido2.util.performance

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.util.crypto.BouncyCastleLoader
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.time.TimeSource

/**
 * One-shot warm-up utilities run at module initialization to exercise the bundled
 * Bouncy Castle provider before the first live FIDO2 ceremony. This does not warm up
 * or imply hardware-backed credential signing.
 *
 * Call order in [com.chimali.fido2.Fido2Initializer.init]:
 *   1. [warmUpBouncyCastle] — registers and exercises BC's in-process EC implementation
 */
object WarmUpHelper {
    // ── Bouncy Castle warm-up ───────────────────────────────────────────────

    /**
     * Registers the bundled Bouncy Castle provider and exercises its in-process P-256
     * signing implementation with an ephemeral key. This is a best-effort provider warm-up;
     * it does not establish a performance budget or hardware protection claim.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun warmUpBouncyCastle() {
        try {
            val mark = TimeSource.Monotonic.markNow()
            Logger.d("BouncyCastle warm-up START")

            // Ensure our bundled BouncyCastle is registered and takes precedence.
            val bcProvider = BouncyCastleLoader.ensureRegistered()

            val kpg = KeyPairGenerator.getInstance("EC", bcProvider)
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val ephemeralKeyPair = kpg.generateKeyPair()

            val sig = Signature.getInstance("SHA256withECDSA", bcProvider)
            sig.initSign(ephemeralKeyPair.private)
            sig.update(byteArrayOf(0x00))
            sig.sign() // result intentionally discarded

            Logger.d { "BouncyCastle warm-up DONE: ${mark.elapsedNow()}" }
        } catch (e: java.security.GeneralSecurityException) {
            Logger.w(e) { "BouncyCastle warm-up FAILED (non-fatal): ${e.message}" }
        }
    }
}
