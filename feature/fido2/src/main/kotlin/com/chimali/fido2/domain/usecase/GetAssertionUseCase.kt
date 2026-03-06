package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.data.crypto.ClientDataHashService
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import javax.inject.Inject

private const val TAG = "GetAssertionUseCase"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SHA256ECDSA = "SHA256withECDSA"

/**
 * T080 — Authenticate use case: executes a FIDO2 GetAssertion ceremony.
 *
 * Flow:
 * 1. Validate [GetAssertionOptions].
 * 2. Look up matching credentials in the repository.
 * 3. If multiple match, delegate to [SelectCredentialUseCase].
 * 4. Perform user verification (biometric/PIN) per UserVerification requirement.
 * 5. Build authenticatorData bytes.
 * 6. Sign (authData || clientDataHash) with the credential's private key.
 * 7. Increment sign count in the repository.
 * 8. Return [AssertionObject] ready for CTAP2 serialisation.
 */
class GetAssertionUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val selectCredentialUseCase: SelectCredentialUseCase
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    suspend operator fun invoke(
        options: GetAssertionOptions
    ): Result<AssertionObject> = runCatching {

        Log.d(TAG, "GetAssertion for rpId=${options.rpId}")

        // 1 — user verification
        performUserVerification(options)

        // 2 — look up candidate credentials
        val candidates = findCandidateCredentials(options)
        if (candidates.isEmpty()) {
            throw Fido2Exception.CredentialNotFound("No credentials found for rpId=${options.rpId}")
        }

        // 3 — select one credential (may involve UI, but here we auto-select or delegate)
        val selectedCred = selectCredentialUseCase(candidates, options)
            .getOrElse { throw it }
        val selectedId = selectedCred.id

        // 4 — build authenticatorData
        val rpIdHash = ClientDataHashService.rpIdHash(options.rpId)
        val signCount = credentialRepository.getSignCount(selectedId)
            .getOrElse { throw it }
        val newSignCount = signCount + 1
        val authData = buildAuthData(
            rpIdHash  = rpIdHash,
            userPresent   = true,
            userVerified  = options.userVerification != UserVerificationRequirement.DISCOURAGED,
            signCount = newSignCount
        )

        // 5 — sign
        val signature = signWithCredential(
            credentialId  = selectedId,
            authData      = authData,
            clientDataHash = options.clientDataHash
        )

        // 6 — persist incremented sign count
        credentialRepository.updateSignCount(selectedId, newSignCount)
            .getOrElse { e -> Log.w(TAG, "Failed to update sign count: ${e.message}") }

        // 7 — build the response descriptor
        val credDesc = PublicKeyCredentialDescriptor.create(id = selectedCred.credentialId)

        Log.d(TAG, "Assertion complete: credId=$selectedId signCount=$newSignCount")
        AssertionObject(
            credential          = credDesc,
            authData            = authData,
            signature           = signature,
            user                = null,         // populated for discoverable flow by caller
            numberOfCredentials = if (candidates.size > 1) candidates.size else null
        )
    }.recoverCatching { e ->
        Log.e(TAG, "GetAssertion failed: ${e.message}", e)
        throw when (e) {
            is Fido2Exception -> e
            else -> Fido2Exception.AuthenticationFailed(e.message ?: "GetAssertion failed", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun performUserVerification(options: GetAssertionOptions) {
        if (options.userVerification == UserVerificationRequirement.REQUIRED) {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                throw Fido2Exception.NoVerificationMethodAvailable("No method available")
            }
        }
    }

    private suspend fun findCandidateCredentials(
        options: GetAssertionOptions
    ): List<PasskeyCredential> {
        return if (options.isDiscoverableFlow()) {
            // Discoverable: any resident credential for this RP
            credentialRepository.getCredentialsForRp(options.rpId)
                .getOrDefault(emptyList())
        } else {
            // Non-discoverable: filter by the allow-list
            val allowIds = options.allowCredentials!!.map { it.id }
            credentialRepository.getCredentialsForRp(options.rpId)
                .getOrDefault(emptyList())
                .filter { cred -> allowIds.any { it.contentEquals(cred.credentialId) } }
        }
    }

    private fun buildAuthData(
        rpIdHash: ByteArray,
        userPresent: Boolean,
        userVerified: Boolean,
        signCount: Long
    ): ByteArray {
        var flags = 0
        if (userPresent)  flags = flags or 0x01
        if (userVerified) flags = flags or 0x04
        val counter = byteArrayOf(
            ((signCount shr 24) and 0xFF).toByte(),
            ((signCount shr 16) and 0xFF).toByte(),
            ((signCount shr 8)  and 0xFF).toByte(),
            (signCount and 0xFF).toByte()
        )
        return rpIdHash + byteArrayOf(flags.toByte()) + counter  // 37 bytes
    }

    private fun signWithCredential(
        credentialId: String,
        authData: ByteArray,
        clientDataHash: ByteArray
    ): ByteArray {
        val alias = "fido2_cred_$credentialId"
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw Fido2Exception.KeyNotFound(alias)
        return Signature.getInstance(SHA256ECDSA).apply {
            initSign(privateKey)
            update(authData + clientDataHash)
        }.sign()
    }
}
