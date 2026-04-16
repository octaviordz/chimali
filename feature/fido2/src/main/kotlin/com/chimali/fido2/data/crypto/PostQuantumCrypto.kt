package com.chimali.fido2.data.crypto

import org.bouncycastle.jcajce.spec.MLDSAParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

// COSE algorithm identifier for ML-DSA-65 (NIST FIPS 204, Level 3)
// IANA final assignment: https://www.iana.org/assignments/cose/cose.xhtml
const val COSE_ML_DSA_65 = -49

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
class PostQuantumCrypto
    @Inject
    constructor() {
        init {
            // On Android, the system provides a crippled "BC" provider that lacks PQC.
            // We must ensure our BouncyCastle 1.80 provider is used.
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)

            Timber.d(
                "PQC Provider registered: %s (version %.1f)",
                BouncyCastleProvider.PROVIDER_NAME,
                Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.version ?: 0.0,
            )
        }

        // ── Provider check ────────────────────────────────────────────────────────

        fun isMlDsaSupported(): Boolean =
            try {
                Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null
            } catch (e: Exception) {
                false
            }

        // ── Key generation ────────────────────────────────────────────────────────

        /**
         * Generates a deterministic ML-DSA-65 key pair from [pqChildSeed].
         *
         * Per NIST FIPS 204 §5.1, ML-DSA.KeyGen needs 32 bytes of entropy (ξ) from
         * which all key material is derived deterministically. We supply those 32 bytes
         * via [DeterministicSecureRandom], which uses SHA-256 CTR expansion to produce
         * a reproducible byte stream from the seed.
         *
         * **Why not SHA1PRNG?**
         * Android's `SecureRandom("SHA1PRNG")` implementation *accumulates* system entropy
         * even after `setSeed()`, so two calls with the same seed produce different key pairs.
         * This would mean the public key stored at registration and the private key
         * re-derived at signing time belong to **different** key pairs, causing every
         * signature to fail ("Invalid data" on the WebAuthn server).
         *
         * @param pqChildSeed 64-byte BIP-85-derived PQ branch seed from [MasterSeedProvider.getPqChildSeed].
         * @return [KeyPair] or null if ML-DSA is not supported on this device.
         */
        fun generateMlDsaKeyPair(pqChildSeed: ByteArray): KeyPair? {
            require(pqChildSeed.size >= 32) { "PQ child seed must be at least 32 bytes" }
            return try {
                val kpg = KeyPairGenerator.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
                // SHA-256 of the seed is the 32-byte ξ for ML-DSA.KeyGen_internal (FIPS 204 Algorithm 6).
                // DeterministicSecureRandom delivers it byte-by-byte with no external entropy injection.
                val xi = MessageDigest.getInstance("SHA-256").digest(pqChildSeed)
                kpg.initialize(MLDSAParameterSpec.ml_dsa_65, DeterministicSecureRandom(xi))
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
         * @return ML-DSA signature bytes, or null on failure.
         */
        fun sign(
            privateKey: PrivateKey,
            data: ByteArray,
        ): ByteArray? =
            try {
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
        fun verify(
            publicKey: PublicKey,
            data: ByteArray,
            signature: ByteArray,
        ): Boolean =
            try {
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

/**
 * A [SecureRandom] that expands a fixed seed deterministically via SHA-256 CTR-DRBG.
 *
 * Unlike Android's `SHA1PRNG`, this class never injects external entropy — `setSeed`
 * overrides are intentional no-ops. This guarantees that two calls with the same
 * constructor seed always produce identical byte sequences, a requirement for
 * re-deriving ML-DSA-65 key pairs from credential seeds.
 *
 * Byte stream: block_n = SHA-256(seed || n), blocks concatenated in order.
 * Each 32-byte block supplies enough entropy for one ML-DSA KeyGen (ξ = 32 bytes
 * per NIST FIPS 204 §5.1). The block counter prevents wrap-around aliasing.
 */
@Suppress("serial")
internal class DeterministicSecureRandom(seed: ByteArray) : SecureRandom() {
    private val md = MessageDigest.getInstance("SHA-256")
    private val seedSnapshot: ByteArray = seed.copyOf()
    private var block: ByteArray = nextBlock(0)
    private var blockPos = 0
    private var blockCounter = 1

    private fun nextBlock(counter: Int): ByteArray {
        md.reset()
        md.update(seedSnapshot)
        md.update(counter.toByte())
        md.update((counter ushr 8).toByte())
        md.update((counter ushr 16).toByte())
        md.update((counter ushr 24).toByte())
        return md.digest()
    }

    override fun nextBytes(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            if (blockPos >= block.size) {
                block = nextBlock(blockCounter++)
                blockPos = 0
            }
            bytes[i++] = block[blockPos++]
        }
    }

    override fun generateSeed(numBytes: Int): ByteArray = ByteArray(numBytes).also { nextBytes(it) }

    // Reject all external entropy injection to preserve determinism.
    override fun setSeed(seed: Long) = Unit

    override fun setSeed(seed: ByteArray) = Unit
}
