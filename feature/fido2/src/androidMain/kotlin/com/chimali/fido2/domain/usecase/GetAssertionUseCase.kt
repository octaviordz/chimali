package com.chimali.fido2.domain.usecase

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrDefault
import com.chimali.core.common.result.getOrElse
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.util.matchesCredentialId
import com.chimali.fido2.util.performance.PerformanceTrace
import org.koin.core.annotation.Factory

/**
 * T080 — Authenticate use case: executes a FIDO2 GetAssertion ceremony.
 *
 * ## Credential selection and key derivation (NFR-PERF-030)
 *
 * [CredentialSummary] carries the identity, persisted algorithm/profile descriptor, and
 * registered public-key encoding needed for selection and signing, so candidates are not
 * hydrated into full credential models. Key derivation happens only after a credential is
 * selected; this class makes no latency claim.
 *
 * ```
 * Phase 1 — List:    getCredentialSummariesForRp()  →  pure DB read, 0 × HDK
 * Phase 2 — Select:  selectCredentialUseCase()      →  metadata only, 0 × HDK
 * Phase 3 — Sign:    cryptoService.sign()           →  selected-key derivation
 *                                                  ────────────────────────────
 *                                                   Total: at most 1 selected-key derivation
 * ```
 *
 * Bluetooth HID connection triggers a best-effort asynchronous availability-cache pre-warm;
 * the active ceremony still handles a cold [UserVerificationService.getUserVerificationAvailability]
 * result when that warm-up has not completed.
 */
@Req("NFR-PERF-030", "NFR-SEC-041")
@Factory
class GetAssertionUseCase(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val selectCredentialUseCase: SelectCredentialUseCase,
    private val cryptoService: Fido2CryptoService,
    private val clientDataHashService: ClientDataHashService,
) {
    companion object {
        private const val COUNTER_PERSISTENCE_FAILED = "Unable to persist credential signing counter"
        private const val COUNTER_OUT_OF_RANGE = "Credential signing counter is outside the supported range"
        private const val MAX_AUTHENTICATOR_COUNTER = 0xFFFF_FFFFL
        private const val FLAG_USER_PRESENT = 0x01
        private const val FLAG_USER_VERIFIED = 0x04

        private const val COUNTER_BYTE_3_SHIFT = 24
        private const val COUNTER_BYTE_2_SHIFT = 16
        private const val COUNTER_BYTE_1_SHIFT = 8
        private const val BYTE_MASK = 0xFF
    }

    /**
     * Performs a FIDO2 assertion using the supplied request options.
     *
     * [options] and its byte-backed values are borrowed for this call; this use case does not
     * take ownership of or erase them. On success, ownership of the returned
     * [AssertionObject] transfers to the caller, which must erase its sensitive buffers after
     * encoding or otherwise consuming the response. Internally acquired credential summaries
     * are erased before this call completes. Cancellation is propagated; operational and
     * validation failures are returned as [Outcome.Error].
     *
     * @param options borrowed GetAssertion request data
     * @param userVerificationPerformed whether the caller already completed required verification
     * @return the owned assertion response, or a domain error
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend operator fun invoke(
        options: GetAssertionOptions,
        userVerificationPerformed: Boolean = false,
    ): Outcome<AssertionObject, DomainError> =
        invokeInternal(options, userVerificationPerformed, prefetchedCandidates = null)

    /**
     * Completes the CTAP headless path using the candidate snapshot already fetched to
     * decide that exactly one credential matches. This path never performs user verification;
     * apply credProtect filtering to the supplied snapshot before selecting or signing.
     * The supplied summary owners are borrowed; the caller remains responsible for erasing them.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun invokeHeadless(
        options: GetAssertionOptions,
        candidates: List<CredentialSummary>,
    ): Outcome<AssertionObject, DomainError> =
        invokeInternal(options, userVerificationPerformed = false, prefetchedCandidates = candidates)

    // Each early return is a fail-closed gate before derivation, signing, or releasing an assertion.
    @Suppress("ReturnCount")
    private suspend fun invokeInternal(
        options: GetAssertionOptions,
        userVerificationPerformed: Boolean,
        prefetchedCandidates: List<CredentialSummary>?,
    ): Outcome<AssertionObject, DomainError> {
        Logger.d { "GetAssertion processing started" }

        // 1 — user verification availability check (result is cached in UserVerificationServiceImpl)
        val verificationResult =
            PerformanceTrace.section("Fido2.Assertion.UserVerification") {
                performUserVerification(options, userVerificationPerformed)
            }
        verificationResult.getOrElse { return Outcome.Error(it) }

        // 2 — Phase 1: list candidate summaries (pure DB read, no HDK derivation)
        val candidateResult =
            prefetchedCandidates?.let { Outcome.Success(it) }
                ?: PerformanceTrace.section("Fido2.Assertion.CandidateLookup") {
                    getCandidateSummaries(options)
                }
        val candidates = candidateResult.getOrElse { return Outcome.Error(it) }
        try {
            if (candidates.any { !CredentialProtectionPolicy.isSupported(it.credProtectPolicy) }) {
                return Outcome.Error(DomainError.ValidationError("Credential-protection policy is invalid"))
            }
            val authorizedCandidates =
                candidates.filter { summary ->
                    when (summary.credProtectPolicy) {
                        CredentialProtectionPolicy.USER_VERIFICATION_REQUIRED -> userVerificationPerformed
                        CredentialProtectionPolicy.OPTIONAL_WITH_CREDENTIAL_ID_LIST ->
                            userVerificationPerformed ||
                                options.allowCredentials?.any { it.id.matchesCredentialId(summary.credentialId) } ==
                                true
                        else -> true
                    }
                }
            if (authorizedCandidates.isEmpty()) {
                return Outcome.Error(
                    DomainError.NotFound(
                        "No credentials found",
                    ),
                )
            }

            // 3 — Phase 2: select best candidate (MRU or allow-list match) — still no HDK
            val selectionResult =
                PerformanceTrace.section("Fido2.Assertion.Selection") {
                    selectCredentialUseCase(authorizedCandidates, options)
                }
            val selectedSummary = selectionResult.getOrElse { return Outcome.Error(it) }

            // 4 — Phase 3: derive/sign only the selected key. The summary supplies the persisted
            // descriptor and registered public-key encoding, so listing and selection do not
            // hydrate credentials or derive candidate keys.
            val descriptor =
                try {
                    CredentialKeyDescriptor(
                        credentialId = selectedSummary.credentialId,
                        coseAlgorithm = selectedSummary.coseAlgorithm,
                        derivationProfile = selectedSummary.derivationProfile,
                    )
                } catch (_: IllegalArgumentException) {
                    return Outcome.Error(DomainError.ValidationError("Selected credential descriptor is invalid"))
                }
            val rpIdOwner = options.copyRpIdOwner()
            val rpIdHash =
                try {
                    clientDataHashService.rpIdHash(rpIdOwner)
                } finally {
                    rpIdOwner.erase()
                }
            val signCountResult =
                PerformanceTrace.section("Fido2.Assertion.CounterRead") {
                    credentialRepository.getSignCount(selectedSummary.credentialId)
                }
            val signCount =
                signCountResult.getOrElse {
                    return Outcome.Error(DomainError.CryptoError("Unable to read credential signing counter"))
                }
            if (signCount !in 0L until MAX_AUTHENTICATOR_COUNTER) {
                return Outcome.Error(DomainError.DatabaseError(COUNTER_OUT_OF_RANGE))
            }
            val newSignCount = signCount + 1
            val authData =
                buildAuthData(
                    rpIdHash = rpIdHash,
                    userVerified = userVerificationPerformed,
                    signCount = newSignCount,
                )

            val signatureResult =
                PerformanceTrace.section("Fido2.Assertion.DeriveAndSign") {
                    signWithCredential(
                        descriptor,
                        selectedSummary,
                        authData,
                        options.clientDataHash,
                    )
                }
            val signature = signatureResult.getOrElse { return Outcome.Error(it) }

            // 5 — persist the incremented sign count before releasing the assertion to the caller.
            var outputReleased = false
            try {
                val counterUpdate =
                    PerformanceTrace.section("Fido2.Assertion.CounterWrite") {
                        credentialRepository.updateSignCount(selectedSummary.credentialId, newSignCount)
                    }
                if (counterUpdate is Outcome.Error) {
                    Logger.w { "Failed to update assertion sign count (${counterUpdate.error::class.simpleName})" }
                    return Outcome.Error(DomainError.DatabaseError(COUNTER_PERSISTENCE_FAILED))
                }

                // Use credentialId bytes from the summary — no full object hydration needed.
                val credDesc = PublicKeyCredentialDescriptor.create(id = selectedSummary.credentialId)

                val assertion =
                    AssertionObject(
                        credential = credDesc,
                        authData = authData,
                        signature = signature,
                        user = null,
                        numberOfCredentials = if (authorizedCandidates.size > 1) authorizedCandidates.size else null,
                    )
                Logger.d { "Assertion completed" }
                outputReleased = true
                return Outcome.Success(assertion)
            } finally {
                if (!outputReleased) {
                    signature.fill(0)
                    authData.fill(0)
                }
            }
        } finally {
            if (prefetchedCandidates == null) candidates.forEach(CredentialSummary::eraseSensitiveText)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun performUserVerification(
        options: GetAssertionOptions,
        userVerificationPerformed: Boolean,
    ): Outcome<Unit, DomainError> {
        // Always call getUserVerificationAvailability() regardless of the UV requirement.
        // When the result comes from the cache (populated during HID connect), this is a
        // pure memory read (~0ms). When the cache is cold, this pays the Binder IPC cost
        // once and caches the result for subsequent calls within the same session.
        val availability = userVerificationService.getUserVerificationAvailability()
        if (options.userVerification == UserVerificationRequirement.REQUIRED) {
            if (!userVerificationPerformed) {
                return Outcome.Error(DomainError.OperationDenied("User verification was not performed"))
            }
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                return Outcome.Error(DomainError.OperationDenied("No verification method available"))
            }
        }
        return Outcome.Success(Unit)
    }

    /**
     * Finds credentials matching the RP and allow-list before user verification.
     * The CTAP handler uses this potential-candidate snapshot to choose its prompt path;
     * credentials requiring UV must remain visible here so they can trigger verification.
     * Each returned summary is caller-owned and must be erased after its last use.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun findCandidateSummaries(options: GetAssertionOptions): List<CredentialSummary> =
        getCandidateSummaries(options).getOrDefault(emptyList())

    private suspend fun getCandidateSummaries(
        options: GetAssertionOptions,
    ): Outcome<List<CredentialSummary>, DomainError> {
        val all =
            when (val result = credentialRepository.getCredentialSummariesForRpText(options.rpIdText)) {
                is Outcome.Success -> result.data
                is Outcome.Error -> return Outcome.Error(result.error)
            }

        var transferredCandidates: List<CredentialSummary>? = null
        try {
            val candidates =
                if (options.isDiscoverableFlow()) {
                    // Discoverable: any resident credential for this RP
                    all
                } else {
                    // Non-discoverable: match the bounded allow-list with the same byte-based
                    // primitive used for policy checks. Do not hash or compare credential IDs as
                    // Strings before an assertion is authorized.
                    val allowCredentials = options.allowCredentials.orEmpty()
                    all.filter { summary ->
                        allowCredentials.any { it.id.matchesCredentialId(summary.credentialId) }
                    }
                }

            // Keep potential candidates intact here. The caller applies policy 3 only after it
            // knows whether verification completed; the CTAP handler also needs these rows to
            // prevent a protected single credential from taking the headless path.
            transferredCandidates = candidates
            return Outcome.Success(candidates)
        } finally {
            all
                .filterNot { summary -> transferredCandidates?.any { it === summary } == true }
                .forEach(CredentialSummary::eraseSensitiveText)
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
        descriptor: CredentialKeyDescriptor,
        summary: CredentialSummary,
        authData: ByteArray,
        clientDataHash: ByteArray,
    ): Outcome<ByteArray, DomainError> =
        cryptoService.sign(descriptor, authData + clientDataHash, summary.storedPublicKeyEncoding)
}
