package com.chimali.fido2.data.crypto

import android.util.Log
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.hdkeys.P256Group
import com.chimali.fido2.domain.exception.Fido2Exception
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.KeyFactory
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECPoint as JavaECPoint
import java.security.spec.ECParameterSpec
import java.security.spec.ECFieldFp
import java.security.spec.EllipticCurve
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Fido2CryptoService"

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
    private val masterSeedProvider: com.chimali.fido2.data.crypto.MasterSeedProvider
) {

    init {
        // Ensure BouncyCastle is registered for Signature operations
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Derives an EC P-256 key pair for the given credential ID using HDK.
     *
     * The derivation path is `[FIDO2_APP_INDEX, credentialPathIndex(credentialId)]`.
     *
     * @param credentialId   Unique credential identifier string.
     * @param requireUserAuth Ignored in HDK model; maintained for API compatibility.
     * @return [Fido2KeyPair] with alias and uncompressed public key bytes (65 bytes).
     */
    suspend fun generateCredentialKeyPair(
        credentialId: String,
        requireUserAuth: Boolean = false
    ): Result<Fido2KeyPair> = runCatching {
        val seed = masterSeedProvider.getMasterSeed()
            ?: throw Fido2Exception.KeyGenerationFailed("Master seed not available", null)

        val deviceKeyPair = masterSeedProvider.getDeviceKeyPair()
            ?: throw Fido2Exception.KeyGenerationFailed("Device key pair not available", null)

        val devicePubKeyBytes = P256Group.serializeElement(deviceKeyPair.publicKey)
        val path = derivationPath(credentialId)

        Log.d(TAG, "Deriving HDK key pair for credentialId=$credentialId path=$path")

        val hdkResult = hdkManager.deriveHdk(
            devicePublicKey = devicePubKeyBytes,
            seed = seed,
            path = path
        )

        val publicKeyBytes = P256Group.serializeElement(hdkResult.publicKey) // 65 bytes uncompressed

        Log.d(TAG, "HDK key pair derived: credentialId=$credentialId pubKeyLen=${publicKeyBytes.size}")
        Fido2KeyPair(
            alias = credentialAlias(credentialId),
            publicKeyBytes = publicKeyBytes
        )
    }.recoverCatching { e ->
        Log.e(TAG, "Key derivation failed", e)
        throw Fido2Exception.KeyGenerationFailed(e.message ?: "Key derivation failed", e)
    }

    /**
     * Returns the uncompressed public key bytes (65 bytes) for a credential.
     */
    suspend fun getPublicKeyBytes(credentialId: String): ByteArray? {
        return generateCredentialKeyPair(credentialId).getOrNull()?.publicKeyBytes
    }

    /**
     * Returns a [PublicKey] instance reconstructed from the derived public key bytes.
     */
    suspend fun getPublicKey(credentialId: String): PublicKey? {
        return try {
            val keyPair = generateCredentialKeyPair(credentialId).getOrNull() ?: return null
            decodeUncompressedPoint(keyPair.publicKeyBytes)
        } catch (e: Exception) {
            Log.w(TAG, "getPublicKey failed for $credentialId", e)
            null
        }
    }

    /**
     * Returns true if the master seed is available (prerequisite for any key existence).
     */
    suspend fun keyExists(credentialId: String): Boolean {
        return masterSeedProvider.getMasterSeed() != null
    }

    /**
     * No-op: HDK keys are derived on demand, there is no persistent key to delete.
     * Credential metadata cleanup is handled by the repository.
     */
    suspend fun deleteCredentialKey(credentialId: String): Result<Unit> = Result.success(Unit)

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
    suspend fun sign(credentialId: String, data: ByteArray): Result<ByteArray> = runCatching {
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

        // Derive the blinded private key: sk' = sk * bf mod n
        val blindingFactorBytes = P256Group.serializeScalar(hdkResult.blindingFactor)
        val blindedPrivKeyBytes = hdkManager.blindPrivateKey(
            devicePrivateKey = devicePrivKeyBytes,
            blindingFactor = blindingFactorBytes
        )

        // Sign using BouncyCastle
        val signature = signWithRawScalar(blindedPrivKeyBytes, data).also {
            // Zero out sensitive material immediately
            blindedPrivKeyBytes.fill(0)
            devicePrivKeyBytes.fill(0)
        }

        Log.d(TAG, "Signed ${data.size} bytes for credentialId=$credentialId sigLen=${signature.size}")
        signature
    }.recoverCatching { e ->
        Log.e(TAG, "Signing failed for $credentialId", e)
        throw Fido2Exception.SigningFailed(e.message ?: "Signing failed", e)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Computes a deterministic derivation path index from a credential ID string.
     *
     * Path: [FIDO2_APP_INDEX, stableHashIndex(credentialId)]
     * Both indices are non-negative 31-bit integers to stay within ECDH-P256 limits.
     */
    private fun derivationPath(credentialId: String): List<Int> {
        val hashBytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(credentialId.toByteArray(Charsets.UTF_8))
        // Take first 4 bytes as a 31-bit positive integer
        val credIndex = ((hashBytes[0].toInt() and 0x7F) shl 24) or
                        ((hashBytes[1].toInt() and 0xFF) shl 16) or
                        ((hashBytes[2].toInt() and 0xFF) shl 8)  or
                         (hashBytes[3].toInt() and 0xFF)
        return listOf(FIDO2_APP_INDEX, credIndex)
    }

    /**
     * Signs data using a raw P-256 private scalar via BouncyCastle.
     * Returns DER-encoded ECDSA signature.
     */
    private fun signWithRawScalar(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val sk = BigInteger(1, privateKeyBytes)
        // Reconstruct java.security.PrivateKey from scalar via BouncyCastle ECPrivateKeySpec
        val ecSpec = org.bouncycastle.jce.spec.ECNamedCurveSpec(
            "secp256r1",
            P256Group.G.curve,
            P256Group.G,
            P256Group.ORDER,
            P256Group.G.curve.cofactor
        )
        val ecPrivKeySpec = java.security.spec.ECPrivateKeySpec(sk, ecSpec)
        val privateKey = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            .generatePrivate(ecPrivKeySpec)

        return Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME).apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    /**
     * Decodes an uncompressed EC point (65 bytes) to a [PublicKey] using BouncyCastle.
     */
    private fun decodeUncompressedPoint(bytes: ByteArray): PublicKey {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) {
            "Expected uncompressed EC point (65 bytes, 0x04 prefix)"
        }
        val point = P256Group.deserializeElement(bytes)
        val x = point.normalize().affineXCoord.toBigInteger()
        val y = point.normalize().affineYCoord.toBigInteger()

        val ecSpec = org.bouncycastle.jce.spec.ECNamedCurveSpec(
            "secp256r1",
            P256Group.G.curve,
            P256Group.G,
            P256Group.ORDER,
            P256Group.G.curve.cofactor
        )
        val javaPoint = JavaECPoint(x, y)
        val pubKeySpec = ECPublicKeySpec(javaPoint, ecSpec)
        return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            .generatePublic(pubKeySpec)
    }

    companion object {
        /** Application-level BIP32-style namespace index for FIDO2 keys. */
        private const val FIDO2_APP_INDEX = 0x4649_4432 // "FID2" as 31-bit int (positive)

        /** Returns the logical alias for a credential (used for lookup / metadata). */
        fun credentialAlias(credentialId: String) = "fido2_cred_$credentialId"

        /** COSE algorithm identifier for ES256 (ECDSA with SHA-256). */
        const val COSE_ES256 = -7
    }
}

/** Result type returned by [Fido2CryptoService.generateCredentialKeyPair]. */
data class Fido2KeyPair(
    /** Logical alias for the credential's key (used for metadata lookup). */
    val alias: String,
    /** Uncompressed EC public key bytes: 0x04 || X(32) || Y(32), total 65 bytes. */
    val publicKeyBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fido2KeyPair) return false
        return alias == other.alias && publicKeyBytes.contentEquals(other.publicKeyBytes)
    }
    override fun hashCode(): Int = 31 * alias.hashCode() + publicKeyBytes.contentHashCode()
    override fun toString() = "Fido2KeyPair(alias=$alias, pubKeyLen=${publicKeyBytes.size})"
}
