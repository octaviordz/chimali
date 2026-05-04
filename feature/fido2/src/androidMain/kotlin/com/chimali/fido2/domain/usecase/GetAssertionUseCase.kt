package com.chimali.fido2.domain.usecase

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrDefault
import com.chimali.core.common.result.getOrElse
import com.chimali.core.common.result.onFailure
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import org.koin.core.annotation.Factory

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
@Factory
class GetAssertionUseCase(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val selectCredentialUseCase: SelectCredentialUseCase,
    private val cryptoService: Fido2CryptoService,
) {
    companion object {
        private const val FLAG_USER_PRESENT = 0x01
        private const val FLAG_USER_VERIFIED = 0x04

        /** FIDO2.1: credProtect policy 3 requires user verification. */
        private const val POLICY_UV_REQUIRED = 3

        private const val COUNTER_BYTE_3_SHIFT = 24
        private const val COUNTER_BYTE_2_SHIFT = 16
        private const val COUNTER_BYTE_1_SHIFT = 8
        private const val BYTE_MASK = 0xFF
    }

    suspend operator fun invoke(options: GetAssertionOptions): Outcome<AssertionObject, DomainError> {
        Logger.d { "GetAssertion for rpId=${options.rpId.value}" }

        // 1 — user verification availability check (result is cached in UserVerificationServiceImpl)
        val uvOutcome = performUserVerification(options)
        if (uvOutcome is Outcome.Error) return uvOutcome

        // 2 — Phase 1: list candidate summaries (pure DB read, no HDK derivation)
        val candidates = findCandidateSummaries(options)
        if (candidates.isEmpty()) {
            return Outcome.Error(
                DomainError.NotFound(
                    "No credentials found for rpId=${options.rpId.value}",
                    Fido2Exception.CredentialNotFound(options.rpId.value),
                ),
            )
        }

        // 3 — Phase 2: select best candidate (MRU or allow-list match) — still no HDK
        val selectedSummary = selectCredentialUseCase(candidates, options).getOrElse { return Outcome.Error(it) }

        // 4 — Phase 3: sign — 1 HDK derivation (private key) + ECDSA.
        //
        // CredentialSummary.id is the credential's database primary key, which is the same
        // key passed to Fido2CryptoService.sign(). There is no hydration step here;
        // getCredentialById() (and its implicit getPublicKey() HDK derivation) is skipped
        // because the public key is not needed to produce an assertion signature.
        val selectedId = selectedSummary.id
        val rpIdHash = ClientDataHashService.rpIdHash(options.rpId.value)
        val signCount =
            credentialRepository.getSignCount(selectedSummary.credentialId).getOrElse {
                return Outcome.Error(DomainError.CryptoError("Failed to get sign count: ${it.message}", it.cause))
            }
        val newSignCount = signCount + 1
        val authData =
            buildAuthData(
                rpIdHash = rpIdHash,
                userVerified = options.userVerification != UserVerificationRequirement.DISCOURAGED,
                signCount = newSignCount,
            )

        val signature =
            signWithCredential(
                selectedSummary.credentialId,
                authData,
                options.clientDataHash,
                selectedSummary.coseAlgorithm,
            ).getOrElse { return Outcome.Error(it) }

        // 5 — persist incremented sign count
        credentialRepository
            .updateSignCount(selectedSummary.credentialId, newSignCount)
            .onFailure { e -> Logger.w { "Failed to update sign count: ${e.message}" } }

        // Use credentialId bytes from the summary — no full object hydration needed.
        val credDesc = PublicKeyCredentialDescriptor.create(id = selectedSummary.credentialId)

        Logger.d { "Assertion complete: credId=$selectedId signCount=$newSignCount" }
        return Outcome.Success(
            AssertionObject(
                credential = credDesc,
                authData = authData,
                signature = signature,
                user = null,
                numberOfCredentials = if (candidates.size > 1) candidates.size else null,
            ),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun performUserVerification(options: GetAssertionOptions): Outcome<Unit, DomainError> {
        // Always call getUserVerificationAvailability() regardless of the UV requirement.
        // When the result comes from the cache (populated during HID connect), this is a
        // pure memory read (~0ms). When the cache is cold, this pays the Binder IPC cost
        // once and caches the result for subsequent calls within the same session.
        val availability = userVerificationService.getUserVerificationAvailability()
        if (options.userVerification == UserVerificationRequirement.REQUIRED) {
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                return Outcome.Error(DomainError.OperationDenied("No verification method available"))
            }
        }
        return Outcome.Success(Unit)
    }

    private suspend fun findCandidateSummaries(options: GetAssertionOptions): List<CredentialSummary> {
        val all =
            credentialRepository
                .getCredentialSummariesForRp(options.rpId)
                .getOrDefault(emptyList())

        val candidates =
            if (options.isDiscoverableFlow()) {
                // Discoverable: any resident credential for this RP
                all
            } else {
                // Non-discoverable: filter to allow-listed credential IDs only.
                // Use normalized (no-padding) string comparison to guard against
                // Base64url padding mismatches between wire-parsed IDs (ABSENT padding,
                // via CredentialId.fromByteArray) and DB-stored IDs (which may carry
                // trailing '=' from legacy storage paths via CredentialId.fromEncoded).
                val allowNormalized = options.allowCredentials!!.map { it.id.normalized }.toHashSet()
                all.filter { summary -> summary.credentialId.normalized in allowNormalized }
            }

        // FIDO2.1 credProtect enforcement:
        // If policy is 3 (userVerificationRequired) and UV is not requested (DISCOURAGED),
        // the authenticator MUST NOT enumerate or use the credential.
        val uvWillBePerformed = options.userVerification != UserVerificationRequirement.DISCOURAGED
        return candidates.filter { summary ->
            !(summary.credProtectPolicy == POLICY_UV_REQUIRED && !uvWillBePerformed) // Ignore this credential
        }
    }

    private fun buildAuthData(
        rpIdHash: ByteArray,
        userVerified: Boolean,
        signCount: Long,
    ): ByteArray {
        var flags = 0
        // User Present (UP) bit is always set for assertions
        flags = flags or FLAG_USER_PRESENT
        if (userVerified) flags = flags or FLAG_USER_VERIFIED
        val counter =
            byteArrayOf(
                ((signCount shr COUNTER_BYTE_3_SHIFT) and BYTE_MASK.toLong()).toByte(),
                ((signCount shr COUNTER_BYTE_2_SHIFT) and BYTE_MASK.toLong()).toByte(),
                ((signCount shr COUNTER_BYTE_1_SHIFT) and BYTE_MASK.toLong()).toByte(),
                (signCount and BYTE_MASK.toLong()).toByte(),
            )
        return rpIdHash + byteArrayOf(flags.toByte()) + counter // 37 bytes
    }

    private suspend fun signWithCredential(
        credentialId: CredentialId,
        authData: ByteArray,
        clientDataHash: ByteArray,
        algId: Int,
    ): Outcome<ByteArray, DomainError> = cryptoService.sign(credentialId, authData + clientDataHash, algId)
}
