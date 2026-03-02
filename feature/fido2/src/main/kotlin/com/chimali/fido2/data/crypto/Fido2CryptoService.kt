package com.chimali.fido2.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Fido2CryptoService"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/**
 * T057 — ECDSA P-256 (secp256r1) key pair generation backed by Android KeyStore.
 *
 * Each credential key-pair is stored under a unique alias derived from the credential ID.
 * Keys are hardware-backed when StrongBox or TEE is available.
 *
 * Key alias format: `fido2_cred_<credentialId>`
 */
@Singleton
class Fido2CryptoService @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates an ECDSA P-256 key pair in Android KeyStore for the given credential ID.
     *
     * @param credentialId   Credential identifier; used as the KeyStore alias suffix.
     * @param requireUserAuth If true the key is protected by user authentication (biometric/PIN).
     * @return [Fido2KeyPair] with alias + uncompressed public key bytes on success.
     */
    suspend fun generateCredentialKeyPair(
        credentialId: String,
        requireUserAuth: Boolean = false
    ): Result<Fido2KeyPair> = runCatching {
        val alias = credentialAlias(credentialId)
        Log.d(TAG, "Generating ECDSA P-256 key pair for alias=$alias requireUserAuth=$requireUserAuth")

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(requireUserAuth)
            .build()

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        kpg.initialize(spec)
        val keyPair = kpg.generateKeyPair()

        val pubKeyBytes = encodeUncompressedPoint(keyPair.public)
        Log.d(TAG, "Key pair generated: alias=$alias pubKeyLen=${pubKeyBytes.size}")
        Fido2KeyPair(alias = alias, publicKeyBytes = pubKeyBytes)
    }.mapFailure { e ->
        Log.e(TAG, "Key generation failed", e)
        Fido2Exception.KeyGenerationFailed(e.message ?: "Key generation failed", e)
    }

    /**
     * Returns the public key bytes (uncompressed, 65 bytes) for a stored credential key.
     */
    suspend fun getPublicKeyBytes(credentialId: String): ByteArray? {
        return try {
            val alias = credentialAlias(credentialId)
            val cert = keyStore.getCertificate(alias) ?: return null
            encodeUncompressedPoint(cert.publicKey)
        } catch (e: Exception) {
            Log.w(TAG, "getPublicKeyBytes failed for $credentialId", e)
            null
        }
    }

    /**
     * Returns the raw [PublicKey] for a stored credential (for signature verification).
     */
    suspend fun getPublicKey(credentialId: String): PublicKey? {
        return try {
            keyStore.getCertificate(credentialAlias(credentialId))?.publicKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true if a key pair exists for the given credential ID.
     */
    suspend fun keyExists(credentialId: String): Boolean {
        return try {
            keyStore.containsAlias(credentialAlias(credentialId))
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes the key pair associated with a credential.
     */
    suspend fun deleteCredentialKey(credentialId: String): Result<Unit> = runCatching {
        val alias = credentialAlias(credentialId)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }.mapFailure { e ->
        Fido2Exception.KeyDeletionFailed(e.message ?: "Key deletion failed", e)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Encodes an EC public key as an uncompressed point (0x04 || X || Y, 65 bytes).
     *
     * Android KeyStore returns `sun.security.ec.ECPublicKeyImpl` whose `encoded` field
     * is SubjectPublicKeyInfo (DER). We extract the 65-byte bit-string from the DER encoding.
     */
    private fun encodeUncompressedPoint(publicKey: PublicKey): ByteArray {
        val encoded = publicKey.encoded                          // DER SubjectPublicKeyInfo
        // The last 65 bytes of a P-256 SubjectPublicKeyInfo are the uncompressed point.
        // DER structure: SEQUENCE { SEQUENCE { OID, OID }, BIT STRING { 0x00, 0x04, X(32), Y(32) } }
        return if (encoded.size >= 65 && encoded[encoded.size - 65] == 0x04.toByte()) {
            encoded.copyOfRange(encoded.size - 65, encoded.size)
        } else {
            // Fallback: use raw encoded as-is (may happen in unit tests with software keys)
            encoded
        }
    }

    companion object {
        private const val EC_CURVE = "secp256r1"

        /** Returns the KeyStore alias for a given credential ID. */
        fun credentialAlias(credentialId: String) = "fido2_cred_$credentialId"

        /** COSE algorithm identifier for ES256 (ECDSA with SHA-256). */
        const val COSE_ES256 = -7
    }
}

/** Result type returned by [Fido2CryptoService.generateCredentialKeyPair]. */
data class Fido2KeyPair(
    /** The Android KeyStore alias under which the private key is securely stored. */
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

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    recoverCatching { e -> throw transform(e) }
