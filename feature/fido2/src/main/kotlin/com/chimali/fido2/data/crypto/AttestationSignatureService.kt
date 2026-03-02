package com.chimali.fido2.data.crypto

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AuthenticatorData
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AttestationSignService"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SHA256_WITH_ECDSA = "SHA256withECDSA"

/**
 * T060 — ECDSA P-256 signature generation for FIDO2 attestation and assertion.
 *
 * ### Attestation (MakeCredential)
 * Signs `authenticatorData || clientDataHash` with the newly generated credential key.
 * Used to produce `packed` self-attestation statements.
 *
 * ### Assertion (GetAssertion – future T091)
 * Signs `authenticatorData || clientDataHash` with an existing credential key.
 * Used to prove possession of the credential during authentication.
 *
 * The private key never leaves Android KeyStore; all signing operations happen within the TEE.
 */
@Singleton
class AttestationSignatureService @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Signs `authenticatorData || clientDataHash` using the credential's private ECDSA P-256 key.
     *
     * The returned bytes are a DER-encoded ECDSA signature (r, s), typically 70–72 bytes.
     *
     * @param credentialId  ID of the credential whose private key will be used.
     * @param authDataBytes Serialised authenticatorData bytes (32-byte rpIdHash + flags + counter + ...).
     * @param clientDataHash 32-byte SHA-256 of clientDataJSON.
     * @return DER-encoded ECDSA signature bytes.
     */
    suspend fun signAttestation(
        credentialId: String,
        authDataBytes: ByteArray,
        clientDataHash: ByteArray
    ): Result<ByteArray> {
        return sign(
            alias          = Fido2CryptoService.credentialAlias(credentialId),
            dataToSign     = authDataBytes + clientDataHash,
            operationLabel = "attestation"
        )
    }

    /**
     * Signs `authenticatorData || clientDataHash` for a GetAssertion response.
     * Functionally identical to [signAttestation] but named separately for clarity.
     *
     * @param credentialId   ID of the credential proving possession.
     * @param authDataBytes  Serialised authenticatorData for this assertion.
     * @param clientDataHash SHA-256 of clientDataJSON from the assertion request.
     */
    suspend fun signAssertion(
        credentialId: String,
        authDataBytes: ByteArray,
        clientDataHash: ByteArray
    ): Result<ByteArray> {
        return sign(
            alias          = Fido2CryptoService.credentialAlias(credentialId),
            dataToSign     = authDataBytes + clientDataHash,
            operationLabel = "assertion"
        )
    }

    /**
     * Signs arbitrary data with the key at [alias]. Low-level helper used by both
     * [signAttestation] and [signAssertion].
     */
    suspend fun signWithAlias(alias: String, data: ByteArray): Result<ByteArray> =
        sign(alias, data, "generic")

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun sign(
        alias: String,
        dataToSign: ByteArray,
        operationLabel: String
    ): Result<ByteArray> = runCatching {
        Log.d(TAG, "Signing $operationLabel with alias=$alias dataLen=${dataToSign.size}")

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw Fido2Exception.KeyNotFound(alias)

        val signature = Signature.getInstance(SHA256_WITH_ECDSA).apply {
            initSign(privateKey)
            update(dataToSign)
        }.sign()

        Log.d(TAG, "$operationLabel signature generated: ${signature.size} bytes")
        signature
    }.recoverCatching { e ->
        val wrapped = when (e) {
            is Fido2Exception -> e
            else              -> Fido2Exception.SignatureFailed(
                "$operationLabel signature failed: ${e.message}", e
            )
        }
        Log.e(TAG, "$operationLabel signing error: ${e.message}", e)
        throw wrapped
    }
}
