package com.chimali.fido2.data.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

// COSE algorithm identifier for ML-DSA-65 (NIST FIPS 204, Level 3)
// Working-draft value; IANA final assignment pending.
const val COSE_ML_DSA_65 = -257

/**
 * T017a — Post-Quantum signing via **ML-DSA-65** (Dilithium, NIST FIPS 204 Level 3).
 *
 * ## Design
 * Keys are derived deterministically from a BIP-85-style child seed produced by
 * [WalletMasterSeedProvider.getPqChildSeed], which is cryptographically isolated from
 * the ECDSA branch via a fully-hardened CKD path `[83696968', 83286642', 2']`.
 *
 * This follows the **Hybrid HD Wallet** design from the Tectonic Labs blogpost:
 * https://hackmd.io/abYfydDxRMGkwguLiAqVbg
 *
 * ## Algorithm Choice
 * - **ML-DSA-65** (Dilithium Level 3) is chosen over Falcon/FN-DSA because its deterministic
 *   key generation (`KeyGen_internal`, Algorithm 6) is fully standardized in FIPS 204.
 * - Falcon's seed-based key gen is still evolving and not yet backward-compatible.
 * - ML-DSA-65 is supported by BouncyCastle 1.80+ (`bcprov-jdk18on`) via
 *   `org.bouncycastle.jcajce.spec.MLDSAParameterSpec` in the standard BC provider.
 *
 * ## Security
 * - Provides ~128-bit post-quantum security under Grover/lattice assumptions.
 * - The 64-byte PQ child seed is kept in memory only; never persisted to disk.
 * - Signing does not require network access or Android KeyStore.
 */
@Singleton
class PostQuantumCrypto @Inject constructor() {

    init {
        // On Android, the system provides a crippled "BC" provider that lacks PQC.
        // We must ensure our BouncyCastle 1.80 provider is used.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        
        Timber.d("PQC Provider registered: %s (version %.1f)", 
            BouncyCastleProvider.PROVIDER_NAME, 
            Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.version ?: 0.0)
    }

    // ── Provider check ────────────────────────────────────────────────────────

    fun isMlDsaSupported(): Boolean = try {
        Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null
    } catch (e: Exception) {
        false
    }

    // ── Key generation ────────────────────────────────────────────────────────

    /**
     * Generates a deterministic ML-DSA-65 key pair from [pqChildSeed].
     *
     * The seed is used to initialize a SHA1PRNG [SecureRandom] so the same seed always
     * produces the same key pair (Known Answer Test property, required for credential replay).
     *
     * @param pqChildSeed 64-byte BIP-85-derived PQ branch seed from [MasterSeedProvider.getPqChildSeed].
     * @return [KeyPair] or null if ML-DSA is not supported on this device.
     */
    fun generateMlDsaKeyPair(pqChildSeed: ByteArray): KeyPair? {
        require(pqChildSeed.size >= 32) { "PQ child seed must be at least 32 bytes" }
        return try {
            val sr = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(pqChildSeed) }
            val kpg = KeyPairGenerator.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
            kpg.initialize(MLDSAParameterSpec.ml_dsa_65, sr)
            kpg.generateKeyPair().also {
                Timber.d("ML-DSA-65 key pair generated; pubKeyLen=%d", it.public.encoded.size)
            }
        } catch (e: Exception) {
            Timber.e(e, "ML-DSA key generation failed")
            null
        }
    }

    // ── Signing ───────────────────────────────────────────────────────────────

    /**
     * Signs [data] with an ML-DSA-65 private key.
     *
     * @param privateKey ML-DSA-65 private key from [generateMlDsaKeyPair].
     * @param data       Raw bytes to sign (typically `authData || clientDataHash` in CTAP2).
     * @return DER-encoded ML-DSA signature bytes, or null on failure.
     */
    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray? = try {
        val sig = Signature.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        sig.initSign(privateKey)
        sig.update(data)
        sig.sign().also { Timber.d("ML-DSA-65 signature produced; sigLen=%d", it.size) }
    } catch (e: Exception) {
        Timber.e(e, "ML-DSA signing failed")
        null
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Verifies an ML-DSA-65 [signature] over [data] using [publicKey].
     *
     * Primarily used in unit tests and for local attestation checks.
     *
     * @return true if the signature is valid.
     */
    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean = try {
        val sig = Signature.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
        sig.initVerify(publicKey)
        sig.update(data)
        sig.verify(signature)
    } catch (e: Exception) {
        Timber.e(e, "ML-DSA verification failed")
        false
    }

    // ── Public key encoding ───────────────────────────────────────────────────

    /**
     * Returns the raw DER-encoded bytes of an ML-DSA-65 public key.
     * These bytes are stored in the credential repository alongside the COSE alg ID.
     */
    fun publicKeyBytes(keyPair: KeyPair): ByteArray = keyPair.public.encoded
}
