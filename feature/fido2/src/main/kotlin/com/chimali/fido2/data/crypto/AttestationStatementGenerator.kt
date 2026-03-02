package com.chimali.fido2.data.crypto

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AttestationGenerator"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SHA256_WITH_ECDSA = "SHA256withECDSA"

/**
 * T058 — Creates CTAP2 attestation statements for `authenticatorMakeCredential` responses.
 *
 * Supports two attestation formats per FIDO2 spec §8:
 * - **none** (§8.7): No attestation — produces an empty `attStmt`.
 * - **packed** (§8.2): Self-attestation — signs concatenation of `authData || clientDataHash`
 *   with the credential's ECDSA P-256 private key stored in Android KeyStore.
 *
 * In production deployments you would integrate a device attestation certificate chain.
 * For initial implementation, `none` format (which all platforms accept for basic registration)
 * and `packed` self-attestation are sufficient.
 */
@Singleton
class AttestationStatementGenerator @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a `none` format attestation statement (no trust anchor, widest compat).
     *
     * @param authData      Serialised authenticatorData bytes.
     * @param clientDataHash SHA-256 of the serialised clientDataJSON.
     */
    fun generateNoneAttestation(
        authData: AuthenticatorData,
        clientDataHash: ByteArray
    ): AttestationStatement {
        Log.d(TAG, "Generating 'none' attestation")
        return AttestationStatement(
            alg  = "ES256",
            fmt  = "none",
            attCert = null,
            authData = null,
            x5c  = null
        )
    }

    /**
     * Generates a `packed` self-attestation by signing `authData || clientDataHash` with the
     * credential's private ECDSA P-256 key from Android KeyStore.
     *
     * @param credentialId     Credential ID; used to look up the KeyStore private key.
     * @param authDataBytes    Serialised authenticatorData bytes (as produced by [AuthenticatorDataBuilder]).
     * @param clientDataHash   SHA-256 hash of the JSON-encoded clientData.
     * @return [AttestationStatement] with `fmt="packed"` and `attCert` set to the ECDSA signature.
     */
    suspend fun generatePackedSelfAttestation(
        credentialId: String,
        authDataBytes: ByteArray,
        clientDataHash: ByteArray
    ): Result<AttestationStatement> = runCatching {
        val alias = Fido2CryptoService.credentialAlias(credentialId)
        Log.d(TAG, "Generating packed self-attestation for alias=$alias")

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw Fido2Exception.KeyNotFound(alias)

        // Per CTAP2 spec §8.2: sign (authData || clientDataHash)
        val signedData = authDataBytes + clientDataHash
        val sig = Signature.getInstance(SHA256_WITH_ECDSA).apply {
            initSign(privateKey)
            update(signedData)
        }.sign()

        Log.d(TAG, "Self-attestation signature generated: ${sig.size} bytes")
        AttestationStatement(
            alg     = "ES256",
            fmt     = "packed",
            attCert = sig,          // DER-encoded ECDSA signature
            authData = authDataBytes,
            x5c     = null          // no certificate chain for self-attestation
        )
    }.mapFailure { e ->
        Log.e(TAG, "Packed attestation failed", e)
        Fido2Exception.CryptographicException("Attestation signature failed: ${e.message}", e)
    }

    /**
     * Selects the best attestation format based on [fmt] preference string.
     * Falls back to `none` for unrecognised values.
     */
    suspend fun generateAttestation(
        fmt: String,
        credentialId: String,
        authDataBytes: ByteArray,
        authData: AuthenticatorData,
        clientDataHash: ByteArray
    ): Result<AttestationStatement> = when (fmt) {
        "packed" -> generatePackedSelfAttestation(credentialId, authDataBytes, clientDataHash)
        "none", "" -> Result.success(generateNoneAttestation(authData, clientDataHash))
        else -> {
            Log.w(TAG, "Unknown attestation fmt='$fmt', falling back to 'none'")
            Result.success(generateNoneAttestation(authData, clientDataHash))
        }
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    recoverCatching { e -> throw transform(e) }
