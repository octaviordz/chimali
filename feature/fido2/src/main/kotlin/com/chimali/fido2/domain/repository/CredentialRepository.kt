package com.chimali.fido2.domain.repository

import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
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
     * @return Result indicating success or failure
     */
    suspend fun saveCredential(credential: PasskeyCredential): Result<Unit>
    
    /**
     * Retrieves a credential by its ID.
     * 
     * @param credentialId The ID of the credential to retrieve
     * @return The credential if found, null otherwise
     */
    suspend fun getCredentialById(credentialId: String): PasskeyCredential?
    
    /**
     * Retrieves all credentials for a specific relying party.
     * 
     * @param rpId The ID of the relying party
     * @return Flow of credentials for the RP
     */
    suspend fun getCredentialsByRpId(rpId: String): Flow<PasskeyCredential>
    
    /**
     * Retrieves all credentials for a specific user.
     * 
     * @param userId The ID of the user
     * @return Flow of credentials for the user
     */
    suspend fun getCredentialsByUserId(userId: String): Flow<PasskeyCredential>
    
    /**
     * Retrieves all credentials stored in the system.
     * 
     * @return Flow of all credentials
     */
    suspend fun getAllCredentials(): Flow<PasskeyCredential>
    
    /**
     * Updates the sign count for a credential.
     * 
     * @param credentialId The ID of the credential to update
     * @param newSignCount The new sign count value
     * @return Result indicating success or failure
     */
    suspend fun updateSignCount(credentialId: String, newSignCount: Long): Result<Unit>
    
    /**
     * Updates the last used timestamp for a credential.
     * 
     * @param credentialId The ID of the credential to update
     * @return Result indicating success or failure
     */
    suspend fun updateLastUsedAt(credentialId: String): Result<Unit>
    
    /**
     * Deletes a credential by its ID.
     * 
     * @param credentialId The ID of the credential to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteCredential(credentialId: String): Result<Unit>
    
    /**
     * Checks if a credential exists for a given RP and user combination.
     * 
     * @param rpId The ID of the relying party
     * @param userId The ID of the user
     * @return True if credential exists, false otherwise
     */
    suspend fun credentialExists(rpId: String, userId: String): Boolean
    
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
    suspend fun getCredentialCountByRpId(rpId: String): Int
    
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
     * @return Result indicating if creation is allowed
     */
    suspend fun validateCredentialCreation(rpId: String, userId: String): Result<Unit>
    
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
     * @return Result indicating success or failure
     */
    suspend fun saveRelyingParty(rp: RelyingParty): Result<Unit>

    /**
     * Updates the relying party information for all credentials belonging to an RP.
     * 
     * @param rpId The ID of the relying party
     * @param update Function to update the RP entity
     * @return Result indicating success or failure
     */
    suspend fun updateRelyingParty(rpId: String, update: (RelyingParty) -> RelyingParty): Result<Unit>
    
    /**
     * Retrieves relying party information.
     * 
     * @param rpId The ID of the relying party
     * @return The relying party if found, null otherwise
     */
    suspend fun getRelyingParty(rpId: String): RelyingParty?
    
    /**
     * Saves user consent record.
     * 
     * @param consent The consent record to save
     * @return Result indicating success or failure
     */
    suspend fun saveUserConsent(consent: UserConsentRecord): Result<Unit>
    
    /**
     * Retrieves recent user consent records.
     * 
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of recent consent records
     */
    suspend fun getRecentUserConsent(
        rpId: String? = null, 
        limit: Int = 50
    ): Flow<UserConsentRecord>
    
    /**
     * Checks if user consent is required for an operation.
     * 
     * @param rpId The ID of the relying party
     * @param operationType The type of operation
     * @return True if consent is required, false otherwise
     */
    suspend fun isUserConsentRequired(
        rpId: String, 
        operationType: String
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
     * @return Result containing list of matching credentials
     */
    suspend fun getCredentialsForRp(rpId: String): Result<List<PasskeyCredential>>

    /**
     * T083a — Retrieves lightweight [CredentialSummary] projections for a specific RP.
     *
     * Unlike [getCredentialsForRp], this method performs **no HDK key derivation** — it reads
     * only the database columns required for candidate selection (id, credentialId, lastUsedAt).
     * Use this in the first phase of GetAssertion to pick the best candidate, then call
     * [getCredentialById] once to hydrate only the winner with its derived public key.
     *
     * @param rpId  The relying party identifier to filter by.
     * @return Result containing a list of summaries (empty list on DB error).
     */
    suspend fun getCredentialSummariesForRp(rpId: String): Result<List<CredentialSummary>>

    /**
     * T084 — Retrieves the current sign count for a credential.
     *
     * @param credentialId  ID of the credential
     * @return Result containing the sign count (0 if not found)
     */
    suspend fun getSignCount(credentialId: String): Result<Long>

    /**
     * T085 — Retrieves a batch of credentials by their IDs.
     * Used by SelectCredentialUseCase to filter an allow-list efficiently.
     *
     * @param credentialIds Set of credential IDs to look up
     * @param rpId          Optional RP filter for additional scoping
     * @return Result containing list of matching credentials
     */
    suspend fun getCredentialsByIds(
        credentialIds: Set<String>,
        rpId: String? = null
    ): Result<List<PasskeyCredential>>

    /**
     * Performs cleanup of expired credentials.
     *
     * @param maxAgeDays Maximum age in days before deletion
     * @return Result with count of deleted credentials
     */
    suspend fun cleanupExpiredCredentials(maxAgeDays: Long = 730): Result<Int>

    /**
     * T110 — Deletes all FIDO2 credentials. If [rpId] is provided, only deletes credentials for that RP.
     *
     * @param rpId Optional RP ID to filter destruction
     * @return Result indicating success or failure
     */
    suspend fun deleteAllCredentials(rpId: String? = null): Result<Unit>

    /**
     * T111 — Performs a complete authenticator reset, erasing all credentials, 
     * keys, PINs, and returning the authenticator to factory defaults.
     *
     * @return Result indicating success or failure
     */
    suspend fun resetAuthenticator(): Result<Unit>
    
    /**
     * Updates the custom label or note for a specific credential.
     */
    suspend fun updateLabel(credentialId: String, label: String?): Result<Unit>
}

/**
 * Data class representing credential statistics.
 */
data class CredentialStatistics(
    val totalCredentials: Int,
    val credentialsByRp: Map<String, Int>,
    val expiredCredentials: Int,
    val recentlyUsedCredentials: Int,
    val credentialsRequiringUserVerification: Int,
    val averageAgeDays: Double
)
