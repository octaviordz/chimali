package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.domain.model.ConsentMethod
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.SensitiveText
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.domain.service.VerificationMethod
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import org.koin.core.annotation.Factory

/**
 * Use case for managing user consent in FIDO2 operations.
 * Handles consent recording, retrieval, and validation.
 */
@Factory
class GetUserConsentUseCase(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
) {
    companion object {
        private const val RECENT_CONSENT_LOOKBACK_LIMIT = 10
    }

    /**
     * Records user consent for a specific operation.
     *
     * @param rpId The ID of the relying party
     * @param operationType The type of operation
     * @param credentialId Optional ID of the credential involved
     * @param requireVerification Whether user verification is required
     * @param prompt Custom prompt message for the user
     * @return Result containing the consent record on success, error on failure
     */
    suspend operator fun invoke(
        rpId: RpId,
        operationType: ConsentOperationType,
        credentialId: CredentialId? = null,
        isVerificationRequired: Boolean = false,
        prompt: String? = null,
    ): Result<UserConsentRecord> {
        return try {
            // Validate inputs
            validateConsentRequest(rpId)

            // Check if consent is required for this operation
            val consentRequired =
                userVerificationService.isUserVerificationRequired(
                    rpId = rpId,
                    operationType = operationType.name,
                    context =
                        when (operationType) {
                            ConsentOperationType.REGISTRATION -> VerificationContext.CREDENTIAL_CREATION
                            ConsentOperationType.AUTHENTICATION -> VerificationContext.AUTHENTICATION
                            ConsentOperationType.CREDENTIAL_DELETION -> VerificationContext.CREDENTIAL_DELETION
                            ConsentOperationType.CREDENTIAL_UPDATE -> VerificationContext.CREDENTIAL_UPDATE
                        },
                )

            // If verification is required, perform user verification
            val verificationResult =
                if (isVerificationRequired &&
                    consentRequired == com.chimali.fido2.domain.service.UserVerificationRequirement.REQUIRED
                ) {
                    performUserVerificationForConsent()
                } else {
                    // Silent/implicit consent — no explicit verification performed
                    Result.success(
                        ConsentVerificationResult(
                            isBiometricUsed = false,
                            isPinUsed = false,
                            verificationMethod = null,
                        ),
                    )
                }

            if (verificationResult.isFailure) {
                return Result.failure(
                    verificationResult.exceptionOrNull()
                        ?: Fido2Exception.UserVerificationFailed("User verification failed"),
                )
            }

            val verification = verificationResult.getOrThrow()

            // Create consent record
            val consentRecord =
                UserConsentRecord.create(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    operationType = operationType,
                    rpId = rpId,
                    credentialId = credentialId,
                    isBiometricUsed = verification.isBiometricUsed,
                    isPinUsed = verification.isPinUsed,
                    // Will be populated by actual implementation
                    ipAddress = null,
                    // Will be populated by actual implementation
                    userAgent = null,
                    // Will be populated by actual implementation
                    deviceId = null,
                )

            var transferred = false
            try {
                // The repository borrows this record; this use case retains ownership until the
                // successful result transfers it to its caller.
                val saveResult = credentialRepository.saveUserConsent(consentRecord)
                if (saveResult is Outcome.Error) {
                    return Result.failure(
                        Fido2Exception.ConsentStorageFailed("Failed to save consent"),
                    )
                }

                val result = Result.success(consentRecord)
                transferred = true
                return result
            } finally {
                if (!transferred) consentRecord.eraseSensitiveText()
            }
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Fido2Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves recent user consent records.
     *
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of recent consent records
     */
    fun getRecentConsentRecords(
        rpId: RpId? = null,
        limit: Int = 50,
    ): Flow<UserConsentRecord> = credentialRepository.getRecentUserConsent(rpId, limit)

    /**
     * Retrieves consent records for a specific operation type.
     *
     * @param operationType The type of operation to filter by
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of consent records for the operation type
     */
    fun getConsentRecordsByOperationType(
        operationType: ConsentOperationType,
        rpId: RpId? = null,
        limit: Int = 50,
    ): Flow<UserConsentRecord> =
        getRecentConsentRecords(rpId, limit)
            .filterAndEraseRejected { it.operationType == operationType }

    /**
     * Retrieves consent records for a specific credential.
     *
     * @param credentialId The ID of the credential to filter by
     * @param limit Maximum number of records to retrieve
     * @return Flow of consent records for the credential
     */
    fun getConsentRecordsByCredential(
        credentialId: CredentialId,
        limit: Int = 50,
    ): Flow<UserConsentRecord> =
        getRecentConsentRecords(null, limit)
            .filterAndEraseRejected { it.isForCredential(credentialId) }

    /**
     * Retrieves consent records for a specific relying party.
     *
     * @param rpId The ID of the relying party to filter by
     * @param limit Maximum number of records to retrieve
     * @return Flow of consent records for the RP
     */
    fun getConsentRecordsByRpId(
        rpId: RpId,
        limit: Int = 50,
    ): Flow<UserConsentRecord> =
        getRecentConsentRecords(rpId, limit)
            .filterAndEraseRejected { it.isForRelyingParty(rpId) }

    /**
     * Retrieves recent consent records within a time range.
     *
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @param rpId Optional filter by relying party ID
     * @return Flow of consent records within the time range
     */
    fun getConsentRecordsByTimeRange(
        startTime: Instant,
        endTime: Instant,
        rpId: RpId? = null,
    ): Flow<UserConsentRecord> =
        getRecentConsentRecords(rpId, Int.MAX_VALUE)
            .filterAndEraseRejected {
                it.timestamp > startTime && it.timestamp < endTime
            }

    /**
     * Checks if consent was recently granted for a specific operation.
     *
     * @param rpId The ID of the relying party
     * @param operationType The type of operation
     * @param minutes Number of minutes to consider as "recent"
     * @return True if consent was granted recently, false otherwise
     */
    suspend fun isRecentConsentGranted(
        rpId: RpId,
        operationType: ConsentOperationType,
        minutes: Int = 5,
    ): Boolean {
        var recentConsentFound = false
        getRecentConsentRecords(rpId, RECENT_CONSENT_LOOKBACK_LIMIT).collect { consent ->
            try {
                if (consent.operationType == operationType &&
                    consent.isRecent(minutes) &&
                    (consent.isRegistrationConsent() || consent.isAuthenticationConsent())
                ) {
                    recentConsentFound = true
                }
            } finally {
                consent.eraseSensitiveText()
            }
        }
        return recentConsentFound
    }

    /**
     * Retrieves consent statistics.
     *
     * @param rpId Optional filter by relying party ID
     * @return Consent statistics for the specified RP or all RPs
     */
    suspend fun getConsentStatistics(rpId: RpId? = null): ConsentStatistics {
        val consentRecords = mutableListOf<UserConsentRecord>()
        try {
            getRecentConsentRecords(rpId, Int.MAX_VALUE).collect { consent ->
                admitOwnedConsentRecord(consent) { consentRecords.add(consent) }
            }

            val totalConsents = consentRecords.size
            val registrationConsents = consentRecords.count { it.isRegistrationConsent() }
            val authenticationConsents = consentRecords.count { it.isAuthenticationConsent() }
            val biometricConsents = consentRecords.count { it.getConsentMethod() == ConsentMethod.BIOMETRIC }
            val pinConsents = consentRecords.count { it.getConsentMethod() == ConsentMethod.PIN }
            val combinedConsents = consentRecords.count { it.getConsentMethod() == ConsentMethod.BIOMETRIC_AND_PIN }
            val uniqueRelyingParties = mutableListOf<SensitiveText>()
            consentRecords.forEach { consent ->
                if (uniqueRelyingParties.none { it.contentEquals(consent.rpIdText) }) {
                    uniqueRelyingParties += consent.rpIdText
                }
            }
            val recentConsents = consentRecords.count { it.isRecent(minutes = 60) }

            return ConsentStatistics(
                totalConsents = totalConsents,
                registrationConsents = registrationConsents,
                authenticationConsents = authenticationConsents,
                biometricConsents = biometricConsents,
                pinConsents = pinConsents,
                combinedConsents = combinedConsents,
                uniqueRelyingParties = uniqueRelyingParties.size,
                recentConsents = recentConsents,
                averageConsentsPerDay = calculateAverageConsentsPerDay(consentRecords),
            )
        } finally {
            consentRecords.forEach(UserConsentRecord::eraseSensitiveText)
        }
    }

    /**
     * Validates consent request parameters.
     */
    private fun validateConsentRequest(rpId: RpId) {
        require(RelyingParty.isValidRpId(rpId.value)) {
            "RP ID must be a valid domain or HTTPS origin"
        }
    }

    /**
     * Performs user verification for consent operations.
     */
    private suspend fun performUserVerificationForConsent(): Result<ConsentVerificationResult> {
        val availability = userVerificationService.getUserVerificationAvailability()
        val bestMethod = availability.getBestAvailableMethod()

        if (bestMethod == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
            return Result.failure(
                Fido2Exception.NoVerificationMethodAvailable(
                    "User verification is required but no method is available",
                ),
            )
        }

        return Result.success(
            ConsentVerificationResult(
                isBiometricUsed = availability.isBiometricAvailable,
                isPinUsed = availability.isPinAvailable,
                verificationMethod =
                    when (bestMethod) {
                        com.chimali.fido2.domain.service.VerificationMethod.BIOMETRIC ->
                            VerificationMethod.BIOMETRIC
                        com.chimali.fido2.domain.service.VerificationMethod.PIN ->
                            VerificationMethod.PIN
                        com.chimali.fido2.domain.service.VerificationMethod.BIOMETRIC_AND_PIN ->
                            VerificationMethod.BIOMETRIC
                        else -> null
                    },
            ),
        )
    }

    /**
     * Calculates the average number of consents per day.
     */
    private fun calculateAverageConsentsPerDay(consentRecords: List<UserConsentRecord>): Double {
        val consents = consentRecords
        if (consents.isEmpty()) return 0.0

        val oldestTimestamp: Instant? = consents.minByOrNull { it.timestamp }?.timestamp
        val newestTimestamp: Instant? = consents.maxByOrNull { it.timestamp }?.timestamp

        return if (oldestTimestamp != null && newestTimestamp != null) {
            val daysBetween = (newestTimestamp - oldestTimestamp).inWholeDays
            if (daysBetween > 0) {
                consents.size.toDouble() / daysBetween
            } else {
                0.0
            }
        } else {
            0.0
        }
    }
}

/** Transfers a consent record into its collection only after admission succeeds. */
internal fun admitOwnedConsentRecord(
    consent: UserConsentRecord,
    admit: () -> Boolean,
) {
    var transferred = false
    try {
        check(admit()) { "Consent-record collection admission failed" }
        transferred = true
    } finally {
        if (!transferred) consent.eraseSensitiveText()
    }
}

/**
 * Data class representing consent verification result.
 */
data class ConsentVerificationResult(
    val isBiometricUsed: Boolean,
    val isPinUsed: Boolean,
    val verificationMethod: VerificationMethod?,
)

/**
 * Data class representing consent statistics.
 */
data class ConsentStatistics(
    val totalConsents: Int,
    val registrationConsents: Int,
    val authenticationConsents: Int,
    val biometricConsents: Int,
    val pinConsents: Int,
    val combinedConsents: Int,
    val uniqueRelyingParties: Int,
    val recentConsents: Int,
    val averageConsentsPerDay: Double,
) {
    /**
     * Returns the most used consent method.
     */
    fun getMostUsedMethod(): ConsentMethod =
        when {
            biometricConsents > pinConsents && biometricConsents > combinedConsents -> ConsentMethod.BIOMETRIC
            pinConsents > biometricConsents && pinConsents > combinedConsents -> ConsentMethod.PIN
            combinedConsents > biometricConsents && combinedConsents > pinConsents -> ConsentMethod.BIOMETRIC_AND_PIN
            else -> ConsentMethod.NONE
        }

    /**
     * Returns the total number of verification methods used.
     */
    fun getTotalVerificationMethods(): Int =
        setOfNotNull(
            if (biometricConsents > 0) ConsentMethod.BIOMETRIC else null,
            if (pinConsents > 0) ConsentMethod.PIN else null,
            if (combinedConsents > 0) ConsentMethod.BIOMETRIC_AND_PIN else null,
        ).size

    /**
     * Returns a summary of the statistics.
     */
    fun getSummary(): String {
        val methods = mutableListOf<String>()
        if (biometricConsents > 0) methods.add("Biometric: $biometricConsents")
        if (pinConsents > 0) methods.add("PIN: $pinConsents")
        if (combinedConsents > 0) methods.add("Combined: $combinedConsents")

        val methodSummary = if (methods.isNotEmpty()) methods.joinToString(", ") else "None"

        val avgPerDay = "%.2f".format(averageConsentsPerDay)
        return "Total: $totalConsents consents (Registration: $registrationConsents, " +
            "Authentication: $authenticationConsents, Recent: $recentConsents, " +
            "Avg/day: $avgPerDay) - Methods: $methodSummary"
    }
}

@Suppress("TooGenericExceptionCaught") // Erasure must run for any predicate failure, including Errors.
private fun Flow<UserConsentRecord>.filterAndEraseRejected(
    predicate: (UserConsentRecord) -> Boolean,
): Flow<UserConsentRecord> =
    flow {
        collect { consent ->
            val accepted =
                try {
                    predicate(consent)
                } catch (failure: Throwable) {
                    consent.eraseSensitiveText()
                    throw failure
                }
            if (accepted) {
                var transferred = false
                try {
                    emit(consent)
                    transferred = true
                } finally {
                    if (!transferred) consent.eraseSensitiveText()
                }
            } else {
                consent.eraseSensitiveText()
            }
        }
    }
