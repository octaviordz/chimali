package com.chimali.fido2.data.crypto

import com.chimali.core.common.di.DefaultDispatcher
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.hdkeys.P256Group
import com.chimali.fido2.data.transport.BluetoothHidTransportImpl
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.util.performance.LatencyProfiler
import com.chimali.fido2.util.performance.WarmUpHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a FIDO2 key pair derived via HDK.
 */
data class Fido2KeyPair(
    val alias: String,
    val publicKeyBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Fido2KeyPair
        if (alias != other.alias) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Fido2KeyPair(alias='$alias', publicKeyBytes.size=${publicKeyBytes.size})"
    }
}

/**
 * T145a — Software-derived FIDO2 key management via HdkManager (HDK-ECDH-P256).
 *
 * Replaces the previous Android KeyStore implementation to enable deterministic
 * key derivation from the Master Seed, satisfying the backup requirement FR-AUTH-030
 * and NFR-SEC-040.
 *
 * Key design:
 * - The device key pair (root) is generated once per install via [HdkManager.generateDeviceKeyPair].
 * - A unique derivation path `[FIDO2_APP_INDEX, credentialIndex]` is used per credential,
 *   where credentialIndex is derived deterministically from the credential ID string.
 * - Private keys are derived in-memory on demand and never persisted to disk.
 * - Public key bytes (uncompressed, 65 bytes) are stored alongside credential metadata.
 */
@Singleton
class Fido2CryptoService @Inject constructor(
    private val hdkManager: HdkManager,
    private val masterSeedProvider: MasterSeedProvider,
    private val postQuantumCrypto: PostQuantumCrypto,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    init {
        // Ensure BouncyCastle is registered for Signature operations
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Derives a key pair for the given credential ID using the appropriate algorithm.
     *
     * ## Algorithm routing
     *
     * | Algorithm  | COSE ID | Derivation mechanism |
     * |------------|---------|----------------------|
     * | ES256      | -7      | **HDK-ECDH-P256** via [HdkManager.deriveHdk] (§2.3–2.5 of draft-dijkhuis-cfrg-hdkeys-06) |
     * | Ed25519    | -19     | **SHA-512 hash** — isolated branch; see note below |
     * | ML-DSA-65  | -49     | **BIP-85 + SHA-512** — isolated PQ branch via [MasterSeedProvider.getPqChildSeed] |
     *
     * ### Ed25519 isolation note
     *
     * Ed25519 keys use `SHA-512(masterSeed || "Ed25519" || credentialId)[0..31]` rather than
     * [HdkManager.deriveHdk]. This is **intentional**: Ed25519 operates on a different curve
     * (Curve25519) and is incompatible with the P-256 multiplicative blinding used by
     * HDK-ECDH-P256 (§3.2.2). The derivation is deterministic (reproducible across restarts),
     * satisfies FR-AUTH-030, and is **cryptographically isolated** from the P-256 HDK tree.
     *
     * The derivation path for ES256 is `[FIDO2_APP_INDEX, credentialPathIndex(credentialId)]`.
     *
     * @param credentialId   Unique credential identifier.
     * @param algId          COSE algorithm identifier (default [COSE_ES256]).
     * @return [Fido2KeyPair] with alias and public key bytes.
     */
    suspend fun generateCredentialKeyPair(
        credentialId: CredentialId,
        algId: Int = COSE_ES256
    ): Result<Fido2KeyPair> = withContext(defaultDispatcher) {
        runCatching {
            if (algId == COSE_ML_DSA_65) {
                val pqChildSeed = masterSeedProvider.getPqChildSeed()
                    ?: throw Fido2Exception.KeyGenerationFailed("PQ seed not available", null)
                val derivedSeed = java.security.MessageDigest.getInstance("SHA-512").apply {
                    update(pqChildSeed)
                    update(credentialId.toByteArray())
                }.digest()
                val keyPair = postQuantumCrypto.generateMlDsaKeyPair(derivedSeed)
                    ?: throw Fido2Exception.KeyGenerationFailed("ML-DSA not supported", null)
                val publicKeyBytes = postQuantumCrypto.publicKeyBytes(keyPair)
                derivedSeed.fill(0)
                
                Timber.d("ML-DSA key pair generated: credentialId=%s pubKeyLen=%d", credentialId, publicKeyBytes.size)
                return@withContext Result.success(Fido2KeyPair(credentialAlias(credentialId), publicKeyBytes))
            }

            // Ed25519 branch — intentionally isolated from HDK-ECDH-P256.
            // Derivation: SHA-512(masterSeed || "Ed25519" || credentialId)[0..31]
            // Ed25519 (Curve25519) is incompatible with P-256 multiplicative blinding (§3.2.2
            // of draft-dijkhuis-cfrg-hdkeys-06), so HdkManager.deriveHdk cannot be used here.
            // The 32-byte truncation produces the Ed25519 private key seed deterministically.
            // The private key is zeroed immediately after the public key is extracted.
            if (algId == COSE_ED25519) {
                val seed = masterSeedProvider.getMasterSeed()
                    ?: throw Fido2Exception.KeyGenerationFailed("Master seed not available", null)
                val derivedSeed = java.security.MessageDigest.getInstance("SHA-512").apply {
                    update(seed)
                    update("Ed25519".toByteArray())
                    update(credentialId.toByteArray())
                }.digest().copyOf(32)

                val privParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(derivedSeed, 0)
                val publicKeyBytes = privParams.generatePublicKey().encoded
                
                derivedSeed.fill(0)
                
                Timber.d("Ed25519 key pair generated: credentialId=%s pubKeyLen=%d", credentialId, publicKeyBytes.size)
                return@withContext Result.success(Fido2KeyPair(credentialAlias(credentialId), publicKeyBytes))
            }

            val seed = masterSeedProvider.getMasterSeed()
                ?: throw Fido2Exception.KeyGenerationFailed("Master seed not available", null)

            val deviceKeyPair = masterSeedProvider.getDeviceKeyPair()
                ?: throw Fido2Exception.KeyGenerationFailed("Device key pair not available", null)

            val devicePubKeyBytes = P256Group.serializeElement(deviceKeyPair.publicKey)
            val path = derivationPath(credentialId)

            Timber.d("Deriving HDK key pair for credentialId=%s path=%s", credentialId, path)

            val hdkResult = hdkManager.deriveHdk(
                devicePublicKey = devicePubKeyBytes,
                seed = seed,
                path = path
            )

            val publicKeyBytes = P256Group.serializeElement(hdkResult.publicKey) // 65 bytes uncompressed

            Timber.d("HDK key pair derived: credentialId=%s pubKeyLen=%d", credentialId, publicKeyBytes.size)
            Fido2KeyPair(
                alias = credentialAlias(credentialId),
                publicKeyBytes = publicKeyBytes
            )
        }.recoverCatching { e ->
            Timber.e(e, "Key derivation failed")
            throw Fido2Exception.KeyGenerationFailed(e.message ?: "Key derivation failed", e)
        }
    }

    /**
     * Returns the uncompressed public key bytes (65 bytes) for a credential.
     */
    suspend fun getPublicKeyBytes(credentialId: CredentialId): ByteArray? {
        return generateCredentialKeyPair(credentialId).getOrNull()?.publicKeyBytes
    }

    /**
     * Returns a [PublicKey] instance reconstructed from the derived public key bytes.
     */
    suspend fun getPublicKey(credentialId: CredentialId, algId: Int): PublicKey? {
        return try {
            val keyPair = generateCredentialKeyPair(credentialId, algId).getOrNull() ?: return null
            if (algId == COSE_ML_DSA_65) {
                val bcProvider = BouncyCastleProvider()
                val kf = KeyFactory.getInstance("ML-DSA-65", bcProvider)
                val x509Spec = java.security.spec.X509EncodedKeySpec(keyPair.publicKeyBytes)
                kf.generatePublic(x509Spec)
            } else if (algId == COSE_ED25519) {
                val bcProvider = BouncyCastleProvider()
                val kf = KeyFactory.getInstance("Ed25519", bcProvider)
                val prefix = byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00)
                val x509Spec = java.security.spec.X509EncodedKeySpec(prefix + keyPair.publicKeyBytes)
                kf.generatePublic(x509Spec)
            } else {
                decodeUncompressedPoint(keyPair.publicKeyBytes)
            }
        } catch (e: Exception) {
            Timber.w(e, "getPublicKey failed for %s", credentialId)
            null
        }
    }

    /**
     * Returns true if the master seed is available (prerequisite for any key existence).
     */
    suspend fun keyExists(@Suppress("UNUSED_PARAMETER") credentialId: CredentialId): Boolean {
        return masterSeedProvider.getMasterSeed() != null
    }

    /**
     * No-op: HDK keys are derived on demand, there is no persistent key to delete.
     * Credential metadata cleanup is handled by the repository.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun deleteCredentialKey(@Suppress("UNUSED_PARAMETER") credentialId: CredentialId): Result<Unit> = Result.success(Unit)

    /**
     * Pre-warms the master seed cache to eliminate first-ceremony latency.
     *
     * ## Problem (NFR-PERF-030)
     *
     * [sign] starts by calling [MasterSeedProvider.getMasterSeed], which on the first
     * call decrypts the BIP39 mnemonic from `EncryptedSharedPreferences`. That decrypt
     * uses an AES-256-GCM key stored in AndroidKeyStore under
     * `_androidx_security_master_key_v2`. The **first access to that specific key** in
     * a process session costs ~150ms due to the same HAL IPC init that affects all
     * AndroidKeyStore operations, plus the actual AES-GCM decrypt.
     *
     * [WarmUpHelper.warmUpAndroidKeyStore] does NOT help here because it exercises a
     * *different* key alias (`chimali_fido2_hal_warmup`), not the EncryptedSharedPreferences
     * master key. Each distinct AndroidKeyStore key has its own lazy-init cost.
     *
     * ## Fix
     *
     * Call this method once in a fire-and-forget coroutine when the Bluetooth HID host
     * connects (in [BluetoothHidTransportImpl.observeConnectionState]). By the time
     * the first real [sign] call arrives, [MasterSeedProvider.getMasterSeed] returns
     * the in-memory cached value (~0ms), eliminating the ~150ms cold-start spike.
     *
     * The result is intentionally discarded. Failures are non-fatal.
     */
    suspend fun warmUpMasterSeed() {
        runCatching {
            val t0 = System.currentTimeMillis()
            Timber.d("Master seed pre-warm START")

            // (1) Decrypt BIP39 mnemonic from EncryptedSharedPreferences (~150ms first call).
            //     WalletMasterSeedProvider caches the result; subsequent calls return in ~0ms.
            val seed = masterSeedProvider.getMasterSeed()
                ?: run {
                    Timber.w("Master seed pre-warm: seed not available — skipping full warmup")
                    return@runCatching
                }

            val deviceKeyPair = masterSeedProvider.getDeviceKeyPair()
                ?: run {
                    Timber.w("Master seed pre-warm: device key pair not available — skipping full warmup")
                    return@runCatching
                }

            val t1 = System.currentTimeMillis()
            Timber.d("Master seed pre-warm: seed loaded in %dms — warming full sign() path", t1 - t0)

            // (2) Mirror the full sign() execution path to JIT-compile every hotspot:
            //
            //  Previous approach used path=[0] (1 level, ~20ms) but the real sign() uses
            //  path=[FIDO2_APP_INDEX, credentialIndex] (2 levels, ~65ms). Level 2 HMAC and the
            //  blindPrivateKey + signWithRawScalar steps were still cold on first ceremony.
            //
            //  Now we run the exact same sequence as sign(), with dummy data:
            //   a) serializeElement + serializeScalar for device key pair
            //   b) deriveHdk with the REAL 2-level FIDO2 index path
            //   c) serializeScalar for blinding factor
            //   d) blindPrivateKey (BigInteger multiply mod n)
            //   e) signWithRawScalar (KeyFactory.generatePrivate + Signature.sign via BC)
            //
            //  After this, every code path in sign() is JIT-compiled. The first real ceremony
            //  should cost only the steady-state amount (~80-100ms HDK + ECDSA).
            val devicePubKeyBytes = P256Group.serializeElement(deviceKeyPair.publicKey)
            val devicePrivKeyBytes = P256Group.serializeScalar(deviceKeyPair.privateKey)

            val warmupPath = derivationPath(CredentialId.fromString("warmup")) // warms MessageDigest.getInstance("SHA-256")
            val hdkResult = hdkManager.deriveHdk(
                devicePublicKey = devicePubKeyBytes,
                seed = seed,
                path = warmupPath // real 2-level path derived same way as sign()
            )

            val blindingFactorBytes = P256Group.serializeScalar(hdkResult.blindingFactor)
            withBlindedPrivateKey(devicePrivKeyBytes, blindingFactorBytes) { blindedPrivKeyBytes ->
                // Perform a throwaway sign to warm signWithRawScalar (BC KeyFactory + Signature path).
                // Result is discarded, dummy data avoids doing anything meaningful.
                signWithRawScalar(blindedPrivKeyBytes, ByteArray(32) { it.toByte() })
            }

            // Zeroise sensitive warmup material
            devicePrivKeyBytes.fill(0)

            Timber.d("Master seed pre-warm DONE: seed=%dms sign-path=%dms total=%dms",
                t1 - t0, System.currentTimeMillis() - t1, System.currentTimeMillis() - t0)
        }.onFailure { e ->
            Timber.w(e, "Master seed pre-warm FAILED (non-fatal): %s", e.message)
        }
    }

    /**
     * Signs [data] with the HDK-derived ECDSA P-256 private key for [credentialId].
     *
     * This is the primary signing entry point used by [GetAssertionUseCase]. The
     * blinded private key is derived in-memory and zeroed after use.
     *
     * @param credentialId The credential whose key should sign the data.
     * @param data         The byte array to sign (authData || clientDataHash in CTAP2).
     * @return DER-encoded ECDSA signature bytes.
     */
    suspend fun sign(
        credentialId: CredentialId, 
        data: ByteArray, 
        algId: Int = COSE_ES256
    ): Result<ByteArray> = withContext(defaultDispatcher) {
        runCatching {
            // NFR-PERF-030: Measure crypto signing overhead (HDK derivation + ECDSA)
            LatencyProfiler.start("Crypto.sign")
            
            if (algId == COSE_ML_DSA_65) {
                val pqChildSeed = masterSeedProvider.getPqChildSeed()
                    ?: throw Fido2Exception.KeyNotFound("PQ seed not available")
                val derivedSeed = java.security.MessageDigest.getInstance("SHA-512").apply {
                    update(pqChildSeed)
                    update(credentialId.toByteArray())
                }.digest()
                val keyPair = postQuantumCrypto.generateMlDsaKeyPair(derivedSeed)
                    ?: throw Fido2Exception.SigningFailed("ML-DSA generation failed", null)
                val signature = postQuantumCrypto.sign(keyPair.private, data)
                    ?: throw Fido2Exception.SigningFailed("ML-DSA signing failed", null)
                
                derivedSeed.fill(0)
                LatencyProfiler.end("Crypto.sign")
                Timber.d("Signed %d bytes with ML-DSA for credentialId=%s sigLen=%d", data.size, credentialId, signature.size)
                return@withContext Result.success(signature)
            }

            // Ed25519 branch — intentionally isolated from HDK-ECDH-P256.
            // Re-derives the private key from scratch: SHA-512(masterSeed || "Ed25519" || credentialId)[0..31]
            // The private scalar is zeroed immediately after signing. It is never persisted.
            if (algId == COSE_ED25519) {
                val seed = masterSeedProvider.getMasterSeed()
                    ?: throw Fido2Exception.KeyNotFound("Master seed not available")
                val derivedSeed = java.security.MessageDigest.getInstance("SHA-512").apply {
                    update(seed)
                    update("Ed25519".toByteArray())
                    update(credentialId.toByteArray())
                }.digest().copyOf(32)

                val privParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(derivedSeed, 0)
                val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
                signer.init(true, privParams)
                signer.update(data, 0, data.size)
                val signature = signer.generateSignature()
                
                derivedSeed.fill(0)
                
                LatencyProfiler.end("Crypto.sign")
                Timber.d("Signed %d bytes with Ed25519 for credentialId=%s sigLen=%d", data.size, credentialId, signature.size)
                return@withContext Result.success(signature)
            }

            val seed = masterSeedProvider.getMasterSeed()
                ?: throw Fido2Exception.KeyNotFound("Master seed not available")

            val deviceKeyPair = masterSeedProvider.getDeviceKeyPair()
                ?: throw Fido2Exception.KeyNotFound("Device key pair not available")

            val devicePubKeyBytes = P256Group.serializeElement(deviceKeyPair.publicKey)
            val devicePrivKeyBytes = P256Group.serializeScalar(deviceKeyPair.privateKey)

            val path = derivationPath(credentialId)
            val hdkResult = hdkManager.deriveHdk(
                devicePublicKey = devicePubKeyBytes,
                seed = seed,
                path = path
            )

            // Derive the blinded private key safely scoped
            val blindingFactorBytes = P256Group.serializeScalar(hdkResult.blindingFactor)

            val signature = try {
                withBlindedPrivateKey(devicePrivKeyBytes, blindingFactorBytes) { blindedPrivKeyBytes ->
                    signWithRawScalar(blindedPrivKeyBytes, data)
                }
            } finally {
                // T183: Zero blindingFactorBytes immediately after use — it must not linger in memory.
                // withBlindedPrivateKey already zeroes blindedPrivKeyBytes internally.
                blindingFactorBytes.fill(0)
            }

            // Zero out device private key
            devicePrivKeyBytes.fill(0)

            LatencyProfiler.end("Crypto.sign")
            Timber.d("Signed %d bytes for credentialId=%s sigLen=%d", data.size, credentialId, signature.size)
            signature
        }.recoverCatching { e ->
            LatencyProfiler.end("Crypto.sign") // ensure timer ends on failure path too
            Timber.e(e, "Signing failed for %s", credentialId)
            throw Fido2Exception.SigningFailed(e.message ?: "Signing failed", e)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Safely executes a block with a blinded private key and ensures it is zeroised,
     * enforcing that the blinding key is not leaked or persisted (T178).
     */
    private inline fun <R> withBlindedPrivateKey(
        devicePrivKeyBytes: ByteArray,
        blindingFactorBytes: ByteArray,
        block: (ByteArray) -> R
    ): R {
        val blindedPrivKeyBytes = hdkManager.blindPrivateKey(devicePrivKeyBytes, blindingFactorBytes)
        return try {
            block(blindedPrivKeyBytes)
        } finally {
            blindedPrivKeyBytes.fill(0)
        }
    }



    /**
     * Signs data using a raw P-256 private scalar via BouncyCastle.
     * Returns DER-encoded ECDSA signature.
     *
     * Note: We pass the [BouncyCastleProvider] *instance* rather than the provider name string
     * because Android's security framework silently overrides the "BC" provider name with its
     * own stripped-down variant, which lacks EC KeyFactory support.
     */
    private fun signWithRawScalar(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val sk = BigInteger(1, privateKeyBytes)
        val bcProvider = BouncyCastleProvider()
        // Reconstruct java.security.PrivateKey from scalar via BouncyCastle ECPrivateKeySpec
        val ecSpec = org.bouncycastle.jce.spec.ECNamedCurveSpec(
            "secp256r1",
            P256Group.G.curve,
            P256Group.G,
            P256Group.ORDER,
            P256Group.G.curve.cofactor
        )
        val ecPrivKeySpec = java.security.spec.ECPrivateKeySpec(sk, ecSpec)
        val privateKey = KeyFactory.getInstance("EC", bcProvider)
            .generatePrivate(ecPrivKeySpec)

        return Signature.getInstance("SHA256withECDSA", bcProvider).apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    /**
     * Decodes an uncompressed EC point (65 bytes) to a [PublicKey] using BouncyCastle.
     *
     * Note: We pass the [BouncyCastleProvider] *instance* rather than the provider name string
     * because Android's security framework silently overrides the "BC" provider name with its
     * own stripped-down variant, which lacks EC KeyFactory support.
     */
    private fun decodeUncompressedPoint(bytes: ByteArray): PublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) {
            "Expected uncompressed EC point (65 bytes, 0x04 prefix)"
        }
        val point = P256Group.deserializeElement(bytes)
        val x = point.normalize().affineXCoord.toBigInteger()
        val y = point.normalize().affineYCoord.toBigInteger()

        val bcProvider = BouncyCastleProvider()
        val ecSpec = org.bouncycastle.jce.spec.ECNamedCurveSpec(
            "secp256r1",
            P256Group.G.curve,
            P256Group.G,
            P256Group.ORDER,
            P256Group.G.curve.cofactor
        )
        val javaPoint = ECPoint(x, y)
        val pubKeySpec = ECPublicKeySpec(javaPoint, ecSpec)
        return KeyFactory.getInstance("EC", bcProvider)
            .generatePublic(pubKeySpec)
    }

    companion object {
        /**
         * Application-level HDK namespace index for FIDO2 keys — the first level of the
         * two-level derivation path `[FIDO2_APP_INDEX, credentialIndex]` used in
         * [derivationPath] per §2.3 of `draft-dijkhuis-cfrg-hdkeys-06`.
         *
         * The value `0x46494432` is the ASCII encoding of `"FID2"` (F=0x46, I=0x49, D=0x44,
         * 2=0x32), chosen to be self-describing. The top bit is intentionally clear (31-bit
         * positive integer) to stay within the P-256 scalar field constraints used by HDK.
         *
         * Note: this index has **no structural relation to BIP-32 paths**. HDK path indices
         * are opaque integers fed into [HdkManager.deriveHdk]; they carry no BIP-32 semantics
         * such as hardened/non-hardened derivation.
         */
        private const val FIDO2_APP_INDEX: UInt = 0x4649_4432u // ASCII "FID2"

        /**
         * Computes a deterministic derivation path index from a credential ID.
         *
         * Uses [CredentialId.toByteArray] (UTF-8 encoding of the Base64 string).
         * Path: [FIDO2_APP_INDEX, stableHashIndex(credentialId.toByteArray())]
         * Both indices are full 32-bit unsigned integers.
         */
        private fun derivationPath(credentialId: CredentialId): List<UInt> {
            val hashBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(credentialId.toByteArray())
            // Take first 4 bytes as a full 32-bit unsigned integer (extracting maximum entropy)
            val credIndex = ((hashBytes[0].toUInt() and 0xFFu) shl 24) or
                            ((hashBytes[1].toUInt() and 0xFFu) shl 16) or
                            ((hashBytes[2].toUInt() and 0xFFu) shl 8)  or
                             (hashBytes[3].toUInt() and 0xFFu)
            return listOf(FIDO2_APP_INDEX, credIndex)
        }

        /**
         * Returns the logical alias for a credential using the format defined
         * in §2.8 of draft-dijkhuis-cfrg-hdkeys-06 (origin-alias "/" path).
         *
         * `origin-alias` is "device-key".
         * `path` is the `[`[FIDO2_APP_INDEX]`, credIndex]` derivation path separated by slashes.
         */
        fun credentialAlias(credentialId: CredentialId): String {
            val path = derivationPath(credentialId)
            return "device-key/${path.joinToString("/")}"
        }

        /** COSE algorithm identifier for ES256 (ECDSA with SHA-256). */
        const val COSE_ES256 = -7

        // COSE algorithm identifier for ML-DSA-65 (NIST FIPS 204, Level 3)
        // Working-draft value; IANA final assignment pending.
        const val COSE_ML_DSA_65 = -49

        /** COSE algorithm identifier for Ed25519. */
        const val COSE_ED25519 = -19
    }
}
