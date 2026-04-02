package com.chimali.fido2.domain.usecase

import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import timber.log.Timber
import javax.inject.Inject

/**
 * T080 — Authenticate use case: executes a FIDO2 GetAssertion ceremony.
 *
 * ## Credential selection and key derivation (NFR-PERF-030)
 *
 * HDK key derivation (~50–80ms) is the dominant software cost per ceremony.
 * [CredentialSummary] carries both fields the response assembly needs (`id` for signing
 * and `credentialId` bytes for the descriptor), so there is **no hydration step**.
 *
 * ```
 * Phase 1 — List:    getCredentialSummariesForRp()  →  pure DB read, 0 × HDK
 * Phase 2 — Select:  selectCredentialUseCase()      →  metadata only, 0 × HDK
 * Phase 3 — Sign:    cryptoService.sign()           →  1 × HDK
 *                                                  ────────────────────────────
 *                                                   Total: 1 × HDK (always)
 * ```
 *
 * The cache pre-warm on Bluetooth HID connect ([BluetoothHidDeviceWrapper]) ensures
 * [UserVerificationService.getUserVerificationAvailability] never blocks on a cold
 * Binder IPC call during an active ceremony.
 */
class GetAssertionUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val selectCredentialUseCase: SelectCredentialUseCase,
    private val cryptoService: Fido2CryptoService
) {

    suspend operator fun invoke(
        options: GetAssertionOptions
    ): Result<AssertionObject> = runCatching {

        Timber.d("GetAssertion for rpId=%s", options.rpId)

        // 1 — user verification availability check (result is cached in UserVerificationServiceImpl)
        performUserVerification(options)

        // 2 — Phase 1: list candidate summaries (pure DB read, no HDK derivation)
        val candidates = findCandidateSummaries(options)
        if (candidates.isEmpty()) {
            throw Fido2Exception.CredentialNotFound("No credentials found for rpId=${options.rpId}")
        }

        // 3 — Phase 2: select best candidate (MRU or allow-list match) — still no HDK
        val selectedSummary = selectCredentialUseCase(candidates, options)
            .getOrElse { throw it }

        // 4 — Phase 3: sign — 1 HDK derivation (private key) + ECDSA.
        //
        // CredentialSummary.id is the credential's database primary key, which is the same
        // key passed to Fido2CryptoService.sign(). There is no hydration step here;
        // getCredentialById() (and its implicit getPublicKey() HDK derivation) is skipped
        // because the public key is not needed to produce an assertion signature.
        val selectedId = selectedSummary.id
        val rpIdHash   = ClientDataHashService.rpIdHash(options.rpId)
        val signCount  = credentialRepository.getSignCount(selectedId).getOrElse { throw it }
        val newSignCount = signCount + 1
        val authData = buildAuthData(
            rpIdHash     = rpIdHash,
            userVerified = options.userVerification != UserVerificationRequirement.DISCOURAGED,
            signCount    = newSignCount
        )

        val signature = signWithCredential(selectedId, authData, options.clientDataHash, selectedSummary.coseAlgorithm)

        // 5 — persist incremented sign count
        credentialRepository.updateSignCount(selectedId, newSignCount)
            .getOrElse { e -> Timber.w("Failed to update sign count: %s", e.message) }

        // Use credentialId bytes from the summary — no full object hydration needed.
        val credDesc = PublicKeyCredentialDescriptor.create(id = selectedSummary.credentialId)

        Timber.d("Assertion complete: credId=%s signCount=%d", selectedId, newSignCount)
        AssertionObject(
            credential          = credDesc,
            authData            = authData,
            signature           = signature,
            user                = null,
            numberOfCredentials = if (candidates.size > 1) candidates.size else null
        )
    }.recoverCatching { e ->
        // CredentialNotFound is expected during pre-registration probes -- log at debug level.
        if (e is Fido2Exception.CredentialNotFound) {
            Timber.d("GetAssertion (expected): %s", e.message)
        } else {
            Timber.e(e, "GetAssertion failed: %s", e.message)
        }
        throw when (e) {
            is Fido2Exception -> e
            else -> Fido2Exception.AuthenticationFailed(e.message ?: "GetAssertion failed", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun performUserVerification(options: GetAssertionOptions) {
        // Always call getUserVerificationAvailability() regardless of the UV requirement.
        // When the result comes from the cache (populated during HID connect), this is a
        // pure memory read (~0ms). When the cache is cold, this pays the Binder IPC cost
        // once and caches the result for subsequent calls within the same session.
        val availability = userVerificationService.getUserVerificationAvailability()
        if (options.userVerification == UserVerificationRequirement.REQUIRED) {
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                throw Fido2Exception.NoVerificationMethodAvailable("No method available")
            }
        }
    }

    private suspend fun findCandidateSummaries(
        options: GetAssertionOptions
    ): List<CredentialSummary> {
        val all = credentialRepository.getCredentialSummariesForRp(options.rpId)
            .getOrDefault(emptyList())
            
        val candidates = if (options.isDiscoverableFlow()) {
            // Discoverable: any resident credential for this RP
            all
        } else {
            // Non-discoverable: filter to allow-listed credential IDs only
            val allowIds = options.allowCredentials!!.map { it.id }
            all.filter { summary -> allowIds.any { it.contentEquals(summary.credentialId) } }
        }
        
        // FIDO2.1 credProtect enforcement:
        // If policy is 3 (userVerificationRequired) and UV is not requested (DISCOURAGED),
        // the authenticator MUST NOT enumerate or use the credential.
        val uvWillBePerformed = options.userVerification != UserVerificationRequirement.DISCOURAGED
        return candidates.filter { summary ->
            if (summary.credProtectPolicy == 3 && !uvWillBePerformed) {
                false // Ignore this credential
            } else {
                true
            }
        }
    }

    private fun buildAuthData(
        rpIdHash: ByteArray,
        userVerified: Boolean,
        signCount: Long
    ): ByteArray {
        var flags = 0
        // User Present (UP) bit is always set for assertions
        flags = flags or 0x01
        if (userVerified) flags = flags or 0x04
        val counter = byteArrayOf(
            ((signCount shr 24) and 0xFF).toByte(),
            ((signCount shr 16) and 0xFF).toByte(),
            ((signCount shr 8)  and 0xFF).toByte(),
            (signCount and 0xFF).toByte()
        )
        return rpIdHash + byteArrayOf(flags.toByte()) + counter  // 37 bytes
    }

    private suspend fun signWithCredential(
        credentialId: String,
        authData: ByteArray,
        clientDataHash: ByteArray,
        algId: Int
    ): ByteArray {
        return cryptoService.sign(CredentialId.fromString(credentialId), authData + clientDataHash, algId)
            .getOrElse { throw Fido2Exception.SigningFailed(it.message ?: "Signing failed", it) }
    }
}
