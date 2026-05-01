package com.chimali.fido2.domain.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing FIDO2 credentials.
 * Provides abstract methods for credential storage and retrieval operations.
 */
interface CredentialRepository {
    /**
     * Saves a new passkey credential to storage.
     *
     * @param credential The credential to save
     * @return Outcome indicating success or failure
     */
    suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError>

    /**
     * Retrieves a credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve
     * @return The credential if found, null otherwise
     */
    suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential?

    /**
     * Retrieves all credentials for a specific relying party.
     *
     * @param rpId The ID of the relying party
     * @return Flow of credentials for the RP
     */
    suspend fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential>

    /**
     * Retrieves all credentials for a specific user.
     *
     * @param userId The ID of the user
     * @return Flow of credentials for the user
     */
    suspend fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential>

    /**
     * Retrieves all credentials stored in the system.
     *
     * @return Flow of all credentials
     */
    suspend fun getAllCredentials(): Flow<PasskeyCredential>

    /**
     * Retrieves a paginated list of credentials stored in the system.
     *
     * @param limit Maximum number of credentials to return
     * @param offset Number of credentials to skip
     * @return Outcome containing list of credentials for the requested page
     */
    suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError>

    /**
     * Retrieves a paginated list of credentials for a specific relying party.
     *
     * @param rpId The ID of the relying party
     * @param limit Maximum number of credentials to return
     * @param offset Number of credentials to skip
     * @return Outcome containing list of credentials for the requested page
     */
    suspend fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError>

    /**
     * Updates the sign count for a credential.
     *
     * @param credentialId The ID of the credential to update
     * @param newSignCount The new sign count value
     * @return Outcome indicating success or failure
     */
    suspend fun updateSignCount(
        credentialId: CredentialId,
        newSignCount: Long,
    ): Outcome<Unit, DomainError>

    /**
     * Updates the last used timestamp for a credential.
     *
     * @param credentialId The ID of the credential to update
     * @return Outcome indicating success or failure
     */
    suspend fun updateLastUsedAt(credentialId: CredentialId): Outcome<Unit, DomainError>

    /**
     * Deletes a credential by its ID.
     *
     * @param credentialId The ID of the credential to delete
     * @return Outcome indicating success or failure
     */
    suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError>

    /**
     * Checks if a credential exists for a given RP and user combination.
     *
     * @param rpId The ID of the relying party
     * @param userId The ID of the user
     * @return True if credential exists, false otherwise
     */
    suspend fun credentialExists(
        rpId: RpId,
        userId: UserId,
    ): Boolean

    /**
     * Retrieves credentials that are expired.
     *
     * @param maxAgeDays Maximum age in days before considering as expired
     * @return Flow of expired credentials
     */
    suspend fun getExpiredCredentials(maxAgeDays: Long = 730): Flow<PasskeyCredential>

    /**
     * Retrieves the count of credentials for a relying party.
     *
     * @param rpId The ID of the relying party
     * @return The number of credentials for the RP
     */
    suspend fun getCredentialCountByRpId(rpId: RpId): Int

    /**
     * Retrieves credentials that haven't been used recently.
     *
     * @param days Number of days to consider as "recent"
     * @return Flow of recently unused credentials
     */
    suspend fun getRecentlyUnusedCredentials(days: Long = 30): Flow<PasskeyCredential>

    /**
     * Searches credentials by display name or user name.
     *
     * @param query The search query
     * @return Flow of matching credentials
     */
    suspend fun searchCredentials(query: String): Flow<PasskeyCredential>

    /**
     * Validates that a credential can be created for the given RP and user.
     *
     * @param rpId The ID of the relying party
     * @param userId The ID of the user
     * @return Outcome indicating if creation is allowed
     */
    suspend fun validateCredentialCreation(
        rpId: RpId,
        userId: UserId,
    ): Outcome<Unit, DomainError>

    /**
     * Retrieves credentials that require user verification.
     *
     * @return Flow of credentials requiring user verification
     */
    suspend fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential>

    /**
     * T156b — Saves a new relying party or updates an existing one.
     *
     * @param rp The relying party to save
     * @return Outcome indicating success or failure
     */
    suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError>

    /**
     * Updates the relying party information for all credentials belonging to an RP.
     *
     * @param rpId The ID of the relying party
     * @param update Function to update the RP entity
     * @return Outcome indicating success or failure
     */
    suspend fun updateRelyingParty(
        rpId: RpId,
        update: (RelyingParty) -> RelyingParty,
    ): Outcome<Unit, DomainError>

    /**
     * Retrieves relying party information.
     *
     * @param rpId The ID of the relying party
     * @return The relying party if found, null otherwise
     */
    suspend fun getRelyingParty(rpId: RpId): RelyingParty?

    /**
     * Saves user consent record.
     *
     * @param consent The consent record to save
     * @return Outcome indicating success or failure
     */
    suspend fun saveUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError>

    /**
     * Retrieves recent user consent records.
     *
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of recent consent records
     */
    suspend fun getRecentUserConsent(
        rpId: RpId? = null,
        limit: Int = 50,
    ): Flow<UserConsentRecord>

    /**
     * Checks if user consent is required for an operation.
     *
     * @param rpId The ID of the relying party
     * @param operationType The type of operation
     * @return True if consent is required, false otherwise
     */
    suspend fun isUserConsentRequired(
        rpId: RpId,
        operationType: String,
    ): Boolean

    /**
     * Retrieves credential statistics.
     *
     * @return Credential statistics including total count, by RP, etc.
     */
    suspend fun getCredentialStatistics(): CredentialStatistics

    /**
     * T083 — Retrieves all credentials for a specific RP as a list (not Flow).
     * Used by GetAssertionUseCase for synchronous candidate resolution.
     *
     * @param rpId The ID of the relying party
     * @return Outcome containing list of matching credentials
     */
    suspend fun getCredentialsForRp(rpId: RpId): Outcome<List<PasskeyCredential>, DomainError>

    /**
     * T083a — Retrieves lightweight [CredentialSummary] projections for a specific RP.
     *
     * Unlike [getCredentialsForRp], this method performs **no HDK key derivation** — it reads
     * only the database columns required for candidate selection (id, credentialId, lastUsedAt).
     * Use this in the first phase of GetAssertion to pick the best candidate, then call
     * [getCredentialById] once to hydrate only the winner with its derived public key.
     *
     * @param rpId  The relying party identifier to filter by.
     * @return Outcome containing a list of summaries (empty list on DB error).
     */
    suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError>

    /**
     * T084 — Retrieves the current sign count for a credential.
     *
     * @param credentialId  ID of the credential
     * @return Outcome containing the sign count (0 if not found)
     */
    suspend fun getSignCount(credentialId: CredentialId): Outcome<Long, DomainError>

    /**
     * T085 — Retrieves a batch of credentials by their IDs.
     * Used by SelectCredentialUseCase to filter an allow-list efficiently.
     *
     * @param credentialIds Set of credential IDs to look up
     * @param rpId          Optional RP filter for additional scoping
     * @return Outcome containing list of matching credentials
     */
    suspend fun getCredentialsByIds(
        credentialIds: Set<CredentialId>,
        rpId: RpId? = null,
    ): Outcome<List<PasskeyCredential>, DomainError>

    /**
     * Performs cleanup of expired credentials.
     *
     * @param maxAgeDays Maximum age in days before deletion
     * @return Outcome with count of deleted credentials
     */
    suspend fun cleanupExpiredCredentials(maxAgeDays: Long = 730): Outcome<Int, DomainError>

    /**
     * T110 — Deletes all FIDO2 credentials. If [rpId] is provided, only deletes credentials for that RP.
     *
     * @param rpId Optional RP ID to filter destruction
     * @return Outcome indicating success or failure
     */
    suspend fun deleteAllCredentials(rpId: RpId? = null): Outcome<Unit, DomainError>

    /**
     * T111 — Performs a complete authenticator reset, erasing all credentials,
     * keys, PINs, and returning the authenticator to factory defaults.
     *
     * @return Outcome indicating success or failure
     */
    suspend fun resetAuthenticator(): Outcome<Unit, DomainError>

    /**
     * Updates the custom label or note for a specific credential.
     */
    suspend fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ): Outcome<Unit, DomainError>
}

/**
 * Data class representing credential statistics.
 */
data class CredentialStatistics(
    val totalCredentials: Int,
    val credentialsByRp: Map<RpId, Int>,
    val expiredCredentials: Int,
    val recentlyUsedCredentials: Int,
    val credentialsRequiringUserVerification: Int,
    val averageAgeDays: Double,
)
