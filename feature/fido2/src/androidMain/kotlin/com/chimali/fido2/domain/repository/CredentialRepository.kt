package com.chimali.fido2.domain.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing FIDO2 credentials.
 * Provides abstract methods for credential storage and retrieval operations.
 */
interface CredentialRepository {
    /**
     * Borrows [rpId] for this call's duration and does not retain it. The caller owns each returned
     * summary and must call [CredentialSummary.eraseSensitiveText] after its final borrower
     * completes. Failures transfer no summaries.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialSummariesForRpText(rpId: CharSequence): Outcome<List<CredentialSummary>, DomainError>

    /** Borrows RP/user text for creation validation without retaining either input or returning an owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun validateCredentialCreationForText(
        rpId: CharSequence,
        userId: CharSequence,
    ): Outcome<Unit, DomainError>

    /**
     * Borrows [rpId] for this call's duration without retaining it. Any reconstructed mutable
     * model transfers to the caller, which must erase its sensitive text after the final borrower.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getRelyingPartyForText(rpId: CharSequence): RelyingParty?

    /**
     * Saves a new passkey credential to storage.
     *
     * Borrows the credential through completion; the caller retains its terminal erasure duty.
     *
     * @param credential The credential to save
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError>

    /**
     * Retrieves a credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve
     * @return A caller-owned credential if found, null otherwise. Erase its sensitive text after
     * its final borrower completes.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential?

    /**
     * Retrieves credentials for one relying party as a cold flow.
     *
     * Each accepted emission transfers a mutable credential to the collector, which must erase it
     * after its final borrower completes. If downstream rejects an emission, the producer remains
     * responsible for erasing that credential.
     *
     * @param rpId The ID of the relying party
     * @return Flow of credentials for the RP
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential>

    /**
     * Retrieves credentials for one user as a cold flow.
     *
     * Each accepted emission transfers a mutable credential to the collector, which must erase it
     * after its final borrower completes. If downstream rejects an emission, the producer remains
     * responsible for erasing that credential.
     *
     * @param userId The ID of the user
     * @return Flow of credentials for the user
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential>

    /**
     * Retrieves all credentials stored in the system.
     *
     * Each emitted mutable credential transfers to the collector when emission succeeds. The
     * collector must erase it after its final borrower completes. If emission fails, the producer
     * remains responsible for erasing the credential it could not transfer.
     *
     * @return Flow of all credentials
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAllCredentials(): Flow<PasskeyCredential>

    /**
     * Retrieves a paginated list of credentials stored in the system.
     *
     * @param limit Maximum number of credentials to return
     * @param offset Number of credentials to skip
     * @return Outcome containing a caller-owned page on success; each credential must be erased
     * after its final borrower completes. Errors transfer no credential owner.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
     * @return Outcome containing a caller-owned page on success; each credential must be erased
     * after its final borrower completes. Errors transfer no credential owner.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateLastUsedAt(credentialId: CredentialId): Outcome<Unit, DomainError>

    /**
     * Deletes a credential by its ID.
     *
     * @param credentialId The ID of the credential to delete
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError>

    /**
     * Checks if a credential exists for a given RP and user combination.
     *
     * @param rpId The ID of the relying party
     * @param userId The ID of the user
     * @return True if credential exists, false otherwise
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun credentialExists(
        rpId: RpId,
        userId: UserId,
    ): Boolean

    /**
     * Retrieves expired credentials as a cold flow.
     *
     * Each accepted emission transfers a mutable credential to the collector, which must erase it
     * after its final borrower completes. If downstream rejects an emission, the producer remains
     * responsible for erasing that credential.
     *
     * @param maxAgeDays Maximum age in days before considering as expired
     * @return Flow of expired credentials
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getExpiredCredentials(maxAgeDays: Long = 730): Flow<PasskeyCredential>

    /**
     * Retrieves the count of credentials for a relying party.
     *
     * @param rpId The ID of the relying party
     * @return The number of credentials for the RP
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialCountByRpId(rpId: RpId): Int

    /**
     * Retrieves credentials that haven't been used recently as a cold flow.
     *
     * Each accepted emission transfers a mutable credential to the collector, which must erase it
     * after its final borrower completes. If downstream rejects an emission, the producer remains
     * responsible for erasing that credential.
     *
     * @param days Number of days to consider as "recent"
     * @return Flow of recently unused credentials
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRecentlyUnusedCredentials(days: Long = 30): Flow<PasskeyCredential>

    /**
     * Returns a cold flow and borrows [query] for its full collection lifetime. The caller must
     * keep the query valid until collection completes, then erase it. Each accepted credential
     * emission transfers to the collector for erasure after its final borrower; a rejected
     * emission remains the producer's cleanup responsibility.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun searchCredentials(query: SensitiveCredentialText): Flow<PasskeyCredential>

    /**
     * Validates that a credential can be created for the given RP and user.
     *
     * @param rpId The ID of the relying party
     * @param userId The ID of the user
     * @return Outcome indicating if creation is allowed
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun validateCredentialCreation(
        rpId: RpId,
        userId: UserId,
    ): Outcome<Unit, DomainError>

    /**
     * Retrieves credentials that require user verification as a cold flow.
     *
     * Each accepted emission transfers a mutable credential to the collector, which must erase it
     * after its final borrower completes. If downstream rejects an emission, the producer remains
     * responsible for erasing that credential.
     *
     * @return Flow of credentials requiring user verification
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential>

    /**
     * T156b — Saves a new relying party or updates an existing one.
     *
     * Borrows [rp] until this call completes without retaining its mutable text; the caller retains
     * terminal erasure responsibility.
     * @param rp The relying party to save
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError>

    /**
     * Updates relying-party metadata. The callback borrows the current model for its invocation
     * and must not retain it. Its returned model transfers to the repository and is erased when
     * the call ends; the callback must not retain that returned owner either.
     *
     * @param rpId The ID of the relying party
     * @param update Function to update the RP entity
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun updateRelyingParty(
        rpId: RpId,
        update: (RelyingParty) -> RelyingParty,
    ): Outcome<Unit, DomainError>

    /**
     * Retrieves relying party information.
     *
     * @param rpId The ID of the relying party
     * @return A caller-owned relying party if found, null otherwise. Erase its sensitive text
     * after its final borrower completes.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getRelyingParty(rpId: RpId): RelyingParty?

    /**
     * Saves user consent record.
     *
     * Borrows [consent] until completion without retaining its mutable text; the caller retains
     * terminal erasure responsibility after the call completes.
     * @param consent The consent record to save
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun saveUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError>

    /**
     * Retrieves recent user consent records as a cold flow.
     *
     * Each accepted mutable record transfers to the collector, which must call
     * [UserConsentRecord.eraseSensitiveText] after its final borrower completes. If downstream
     * rejects an emission, the producer remains responsible for erasing that record. Immutable
     * optional metadata fields retain ordinary runtime-managed String lifetime.
     *
     * @param rpId Optional filter by relying party ID
     * @param limit Maximum number of records to retrieve
     * @return Flow of recent consent records
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRecentUserConsent(
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun isUserConsentRequired(
        rpId: RpId,
        operationType: String,
    ): Boolean

    /**
     * Retrieves credential statistics.
     *
     * @return Credential statistics including total count, by RP, etc.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialStatistics(): CredentialStatistics

    /**
     * T083 — Retrieves all credentials for a specific RP as a list (not Flow).
     * Used by GetAssertionUseCase for synchronous candidate resolution.
     *
     * @param rpId The ID of the relying party
     * @return A caller-owned credential list on success; erase each credential after its final
     * borrower completes. Errors transfer no credential owner.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
     * @return A caller-owned list on success; erase each summary's sensitive RP text after its
     * final borrower completes. Errors transfer no summary owner.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError>

    /**
     * T084 — Retrieves the current sign count for a credential.
     *
     * @param credentialId  ID of the credential
     * @return Outcome containing the sign count (0 if not found)
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getSignCount(credentialId: CredentialId): Outcome<Long, DomainError>

    /**
     * T085 — Retrieves a batch of credentials by their IDs.
     * Used by SelectCredentialUseCase to filter an allow-list efficiently.
     *
     * @param credentialIds Set of credential IDs to look up
     * @param rpId          Optional RP filter for additional scoping
     * @return A caller-owned list on success; erase each credential after its final borrower
     * completes. Errors transfer no credential owner.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun cleanupExpiredCredentials(maxAgeDays: Long = 730): Outcome<Int, DomainError>

    /**
     * T110 — Deletes all FIDO2 credentials. If [rpId] is provided, only deletes credentials for that RP.
     *
     * @param rpId Optional RP ID to filter destruction
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun deleteAllCredentials(rpId: RpId? = null): Outcome<Unit, DomainError>

    /**
     * T111 — Performs a complete authenticator reset, erasing all credentials,
     * keys, PINs, and returning the authenticator to factory defaults.
     *
     * @return Outcome indicating success or failure
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun resetAuthenticator(): Outcome<Unit, DomainError>
}

/**
 * Data class representing credential statistics.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
data class CredentialStatistics(
    val totalCredentials: Int,
    val expiredCredentials: Int,
    val recentlyUsedCredentials: Int,
    val credentialsRequiringUserVerification: Int,
    val averageAgeDays: Double,
)
