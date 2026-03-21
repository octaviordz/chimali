package com.chimali.fido2.util.performance

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * One-shot warm-up utilities run at module initialization to eliminate JIT and
 * provider/HAL registration overhead from the first live FIDO2 ceremony.
 *
 * Call order in [com.chimali.fido2.Fido2Initializer.init]:
 *   1. [warmUpBouncyCastle] — warms BC provider + JIT for in-process EC math
 *   2. [warmUpAndroidKeyStore] — warms the AndroidKeyStore HAL / TEE IPC channel
 */
object WarmUpHelper {

    // ── AndroidKeyStore warm-up ─────────────────────────────────────────────

    /**
     * Fixed alias for the dedicated warm-up key stored in AndroidKeyStore.
     * This key is never used for real credential operations; it exists solely
     * to keep the HAL IPC channel warm.
     */
    private const val WARMUP_KEY_ALIAS = "chimali_fido2_hal_warmup"

    /**
     * Warms up the AndroidKeyStore TEE/HAL IPC path used by all FIDO2 credential signing.
     *
     * ## Problem
     *
     * `cryptoService.sign()` uses `Signature.getInstance("SHA256withECDSA", "AndroidKeyStore")`
     * with a hardware-backed key. The first call in an app session initializes the IPC
     * channel to the Trusted Execution Environment (TEE) or StrongBox HAL, costing
     * **~180–360ms** on the first invocation vs. **~170ms** steady-state. This latency
     * cannot be amortized using the [warmUpBouncyCastle] path because BC and AndroidKeyStore
     * use entirely separate signing engines.
     *
     * ## Fix: persistent warmup key
     *
     * A dedicated P-256 EC key is generated once under [WARMUP_KEY_ALIAS] and stored in
     * AndroidKeyStore. On every subsequent app start the key already exists, so the
     * [KeyStore.load] + `initSign` + one-byte `sign` call exercises the full TEE path
     * (~5ms key lookup + steady-state TEE cost) without the one-time HAL init spike.
     *
     * The signed result is discarded immediately. The key material never leaves the TEE.
     *
     * ## Why a persistent key is safe
     *
     * The warmup key is P-256 with purpose `SIGN` only and no `userAuthenticationRequired`.
     * It is separate from all real credential keys (which live under their own aliases).
     * It cannot be used to impersonate a credential or decrypt any data.
     */
    fun warmUpAndroidKeyStore() {
        try {
            val t0 = System.currentTimeMillis()
            Timber.d("AndroidKeyStore warm-up START")

            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

            // Generate the warmup key if it doesn't yet exist (first install or after factory
            // reset / full uninstall). Subsequent app starts skip straight to the sign step.
            if (!keyStore.containsAlias(WARMUP_KEY_ALIAS)) {
                Timber.d("AndroidKeyStore warm-up: generating warmup key (first run)")
                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
                )
                kpg.initialize(
                    KeyGenParameterSpec.Builder(
                        WARMUP_KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        // No user authentication required — this key is for warmup only,
                        // never for protecting user credentials.
                        .build()
                )
                kpg.generateKeyPair()
                Timber.d("AndroidKeyStore warm-up: key created in %dms", System.currentTimeMillis() - t0)
            } else {
                Timber.d("AndroidKeyStore warm-up: reusing existing warmup key")
            }

            // Retrieve the private key and run one throwaway ECDSA sign.
            // This is the step that exercises the TEE IPC path and triggers ART JIT
            // compilation of the AndroidKeyStore signing internals. The result is discarded.
            val t1 = System.currentTimeMillis()
            val privateKey = keyStore.getKey(WARMUP_KEY_ALIAS, null)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initSign(privateKey as java.security.PrivateKey)
            sig.update(byteArrayOf(0x00))
            sig.sign()  // result intentionally discarded

            Timber.d("AndroidKeyStore warm-up DONE: sign=%dms total=%dms", System.currentTimeMillis() - t1, System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            // Non-fatal: the first real ceremony will pay the warm-up cost itself.
            Timber.w(e, "AndroidKeyStore warm-up FAILED (non-fatal): %s", e.message)
        }
    }

    // ── BouncyCastle warm-up ────────────────────────────────────────────────

    /**
     * Warms up the BouncyCastle security provider and JIT-compiles the in-process
     * ECDSA signing path (used for key derivation math, not for TEE signing).
     *
     * The first call to `SHA256withECDSA` via BouncyCastle incurs two one-time costs:
     *   1. `Security.addProvider` — registers the BC `JCE` implementation (~20ms).
     *   2. ART JIT compilation of the EC math hot path (~50–120ms on first invocation).
     *
     * A throwaway sign over an ephemeral, immediately discarded P-256 key amortizes
     * both costs before the first real ceremony.
     */
    fun warmUpBouncyCastle() {
        try {
            val t0 = System.currentTimeMillis()
            Timber.d("BouncyCastle warm-up START")

            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }

            val bcProvider = BouncyCastleProvider()
            val kpg = KeyPairGenerator.getInstance("EC", bcProvider)
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val ephemeralKeyPair = kpg.generateKeyPair()

            val sig = Signature.getInstance("SHA256withECDSA", bcProvider)
            sig.initSign(ephemeralKeyPair.private)
            sig.update(byteArrayOf(0x00))
            sig.sign()  // result intentionally discarded

            Timber.d("BouncyCastle warm-up DONE: %dms", System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            Timber.w(e, "BouncyCastle warm-up FAILED (non-fatal): %s", e.message)
        }
    }
}
