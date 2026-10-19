package com.chimali.fido2.data.repository

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrNull
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import java.security.GeneralSecurityException
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Implementation of CredentialRepository using SQLDelight for data persistence
 * and Android KeyStore for secure credential storage.
 */
@Single
@Suppress("LargeClass") // The repository's existing interface groups the credential workflows in one implementation.
class CredentialRepositoryImpl(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val relyingPartyDao: RelyingPartyDao,
    private val userConsentRecordDao: UserConsentRecordDao,
    private val publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder,
    private val timeProvider: TimeProvider,
    @Named("passkey") private val aggregateService: AggregateService<PasskeyCommand, PasskeyState>,
    private val metadataProtectionService: CredentialMetadataProtectionService,
) : CredentialRepository {
    /** Test-only observer for retaining newly owned candidate text before batch transfer or failure cleanup. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onSummaryCreatedForTest: ((CredentialSummary) -> Unit)? = null

    /** Test-only observer for verifying cleanup of owners acquired by a destructive batch. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onDestructiveCredentialCreatedForTest: ((PasskeyCredential) -> Unit)? = null

    private val readOperations =
        CredentialRepositoryReadOperations(
            passkeyCredentialDao = passkeyCredentialDao,
            publicKeyDecoder = publicKeyDecoder,
            timeProvider = timeProvider,
            metadataProtectionService = metadataProtectionService,
            onSummaryCreated = { summary -> onSummaryCreatedForTest?.invoke(summary) },
        )

    private val destructiveBatchReader =
        DestructiveCredentialBatchReader(
            dao = passkeyCredentialDao,
            publicKeyDecoder = publicKeyDecoder,
            timeProvider = timeProvider,
            metadataProtectionService = metadataProtectionService,
            onCredentialHydrated = { credential -> onDestructiveCredentialCreatedForTest?.invoke(credential) },
        )

    companion object {
        private const val STORAGE_OPERATION_FAILED = "Credential storage operation failed"
        private const val MAX_USER_CREDENTIALS_PER_RP = 10
        private const val RECENT_USAGE_CUTOFF_DAYS = 90L
        private const val EXPIRY_DAYS_THRESHOLD = 730L

        /** CTAP2 credProtect policy 3 requires user verification before use. */
        private const val CRED_PROTECT_UV_REQUIRED = 3L
    }

    // ── Credential CRUD ──────────────────────────────────────────────────────

    /** Implements [CredentialRepository.saveCredential] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError> {
        var command: PasskeyCommand.Register? = null
        return try {
            // Event Sourcing: Dispatch Register command
            val registerCommand =
                PasskeyCommand.Register(
                    id = credential.id.encoded,
                    rpId = credential.rpId.copyCharacters(),
                    userId = credential.userId.copyCharacters(),
                    userName = credential.userName.copyCharacters(),
                    userDisplayName = credential.userDisplayName.copyCharacters(),
                    credentialId = credential.id.toByteArray(),
                    publicKey =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(credential.publicKey.encoded),
                    aaguid =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(credential.aaguid),
                    signCount = credential.signCount,
                    coseAlgorithm = credential.coseAlgorithm,
                    derivationProfile = credential.derivationProfile,
                )
            command = registerCommand

            val aggregateResult = aggregateService.execute(credential.id.encoded, registerCommand)
            try {
                if (aggregateResult.isFailure) {
                    return Outcome.Error(
                        DomainError.StorageError(
                            "Failed to persist passkey event",
                        ),
                    )
                }

                // Event append has committed. Replace the projection as a separate atomic phase.
                passkeyCredentialDao.replaceCredentialsForRpAndUser(credential)
                Outcome.Success(Unit)
            } finally {
                aggregateResult.getOrNull()?.eraseSensitiveMetadata()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to save credential" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        } catch (_: java.sql.SQLException) {
            Logger.e { "CredentialRepository: Failed to save credential" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        } catch (_: GeneralSecurityException) {
            credentialProjectionFailure()
        } catch (_: IllegalArgumentException) {
            credentialProjectionFailure()
        } catch (_: IllegalStateException) {
            credentialProjectionFailure()
        } catch (_: SecurityException) {
            credentialProjectionFailure()
        } finally {
            command?.eraseSensitiveMetadata()
        }
    }

    private fun credentialProjectionFailure(): Outcome.Error<DomainError.StorageError> {
        Logger.e { "CredentialRepository: Failed to project credential" }
        return Outcome.Error(DomainError.StorageError("Failed to project credential"))
    }

    /** Implements [CredentialRepository.getCredentialById] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential? =
        readOperations.getCredentialById(credentialId)

    /** Implements [CredentialRepository.getCredentialsByRpId] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential> = readOperations.getCredentialsByRpId(rpId)

    /** Implements [CredentialRepository.getCredentialsByUserId] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential> =
        readOperations.getCredentialsByUserId(userId)

    /** Implements [CredentialRepository.getAllCredentials] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getAllCredentials(): Flow<PasskeyCredential> = readOperations.getAllCredentials()

    /** Implements [CredentialRepository.getPagedCredentials] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> = readOperations.getPagedCredentials(limit, offset)

    /** Implements [CredentialRepository.getPagedCredentialsByRpId] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> = readOperations.getPagedCredentialsByRpId(rpId, limit, offset)

    /** Implements [CredentialRepository.deleteCredential] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError> =
        try {
            // Event Sourcing: Dispatch Delete command
            val aggregateResult =
                aggregateService.execute(
                    credentialId.encoded,
                    PasskeyCommand.Delete(credentialId.encoded),
                )
            try {
                if (aggregateResult.isFailure) {
                    val failure = aggregateResult.exceptionOrNull()
                    if (failure is CancellationException) throw failure
                    Logger.w { "Failed to persist credential deletion event" }
                    return Outcome.Error(DomainError.DatabaseError("Failed to persist credential deletion event"))
                }

                passkeyCredentialDao.deleteCredential(credentialId)
                Outcome.Success(Unit)
            } finally {
                aggregateResult.getOrNull()?.eraseSensitiveMetadata()
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to delete credential" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Implements [CredentialRepository.credentialExists] without retaining borrowed lookup values. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun credentialExists(
        rpId: RpId,
        userId: UserId,
    ): Boolean =
        try {
            val entities = passkeyCredentialDao.getCredentialsByRpIdAndUserId(rpId, userId)
            try {
                entities.isNotEmpty()
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        } catch (_: android.database.SQLException) {
            Logger.e {
                "CredentialRepository: Failed to check credential existence"
            }
            false
        }

    // ── Sign count & usage ────────────────────────────────────────────────────

    /** Implements [CredentialRepository.updateSignCount] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun updateSignCount(
        credentialId: CredentialId,
        newSignCount: Long,
    ): Outcome<Unit, DomainError> =
        try {
            // Event Sourcing: Dispatch Authenticate command (updates signCount and lastUsedAt)
            val aggregateResult =
                aggregateService.execute(
                    credentialId.encoded,
                    PasskeyCommand.Authenticate(credentialId.encoded, newSignCount),
                )
            try {
                if (aggregateResult.isFailure) {
                    Logger.w { "Failed to persist authentication event" }
                    val failure = aggregateResult.exceptionOrNull()
                    if (failure is CancellationException) throw failure
                    return Outcome.Error(DomainError.DatabaseError("Failed to persist credential authentication event"))
                }

                passkeyCredentialDao.updateSignCount(credentialId, newSignCount)
                Outcome.Success(Unit)
            } finally {
                aggregateResult.getOrNull()?.eraseSensitiveMetadata()
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to update credential signing counter" }
            Outcome.Error(DomainError.DatabaseError("Failed to update credential signing counter"))
        }

    /** Implements [CredentialRepository.updateLastUsedAt] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun updateLastUsedAt(credentialId: CredentialId): Outcome<Unit, DomainError> =
        try {
            // Event Sourcing: Dispatch Authenticate command with current sign count to update lastUsedAt
            val currentSignCount = passkeyCredentialDao.getSignCount(credentialId)
            val aggregateResult =
                aggregateService.execute(
                    credentialId.encoded,
                    PasskeyCommand.Authenticate(credentialId.encoded, currentSignCount),
                )
            try {
                if (aggregateResult.isFailure) {
                    Logger.w { "Failed to persist authentication event" }
                    val failure = aggregateResult.exceptionOrNull()
                    if (failure is CancellationException) throw failure
                    return Outcome.Error(DomainError.DatabaseError("Failed to persist credential last-used event"))
                }

                passkeyCredentialDao.updateLastUsedAt(credentialId)
                Outcome.Success(Unit)
            } finally {
                aggregateResult.getOrNull()?.eraseSensitiveMetadata()
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to update last used timestamp" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Implements [CredentialRepository.getSignCount] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getSignCount(credentialId: CredentialId): Outcome<Long, DomainError> =
        try {
            val count = passkeyCredentialDao.getSignCount(credentialId)
            Outcome.Success(count)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to read credential signing counter" }
            Outcome.Error(DomainError.DatabaseError("Failed to read credential signing counter"))
        }

    // ── Batch retrieval ───────────────────────────────────────────────────────

    /** Implements [CredentialRepository.getCredentialsForRp] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialsForRp(rpId: RpId): Outcome<List<PasskeyCredential>, DomainError> =
        readOperations.getCredentialsForRp(rpId)

    /** Implements [CredentialRepository.getCredentialSummariesForRp] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError> =
        readOperations.getCredentialSummariesForRp(rpId)

    /** Implements [CredentialRepository.getCredentialSummariesForRpText] with borrowed text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialSummariesForRpText(
        rpId: CharSequence,
    ): Outcome<List<CredentialSummary>, DomainError> = readOperations.getCredentialSummariesForRpText(rpId)

    /** Implements [CredentialRepository.getCredentialsByIds] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialsByIds(
        credentialIds: Set<CredentialId>,
        rpId: RpId?,
    ): Outcome<List<PasskeyCredential>, DomainError> = readOperations.getCredentialsByIds(credentialIds, rpId)

    /** Implements [CredentialRepository.getCredentialCountByRpId] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialCountByRpId(rpId: RpId): Int = readOperations.getCredentialCountByRpId(rpId)

    /** Implements [CredentialRepository.searchCredentials] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun searchCredentials(query: SensitiveCredentialText): Flow<PasskeyCredential> =
        readOperations.searchCredentials(query)

    /** Implements [CredentialRepository.getRecentlyUnusedCredentials] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> =
        readOperations.getRecentlyUnusedCredentials(days)

    /** FR-005 — Projects only credentials whose persisted credProtect policy requires UV. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> =
        readOperations.getCredentialsRequiringUserVerification()

    /** Implements [CredentialRepository.getExpiredCredentials] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> =
        readOperations.getExpiredCredentials(maxAgeDays)

    /** Implements [CredentialRepository.cleanupExpiredCredentials] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun cleanupExpiredCredentials(maxAgeDays: Long): Outcome<Int, DomainError> =
        try {
            when (val batch = destructiveBatchReader.read(expiredMaxAgeDays = maxAgeDays)) {
                is Outcome.Success -> deleteCredentialBatch(batch.data)
                is Outcome.Error -> batch
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to cleanup expired credentials" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    // ── Validation / checks ───────────────────────────────────────────────────

    /** Implements [CredentialRepository.validateCredentialCreation] without retaining borrowed lookup values. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun validateCredentialCreation(
        rpId: RpId,
        userId: UserId,
    ): Outcome<Unit, DomainError> = validateCredentialCreationForText(rpId.value, userId.value)

    /** Borrows RP/user text and erases hydrated rows/models before returning or propagating cancellation. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun validateCredentialCreationForText(
        rpId: CharSequence,
        userId: CharSequence,
    ): Outcome<Unit, DomainError> =
        try {
            val rows = passkeyCredentialDao.getCredentialsByRpText(rpId).first()
            try {
                val count = countOwnedUserCredentials(rows, userId)
                if (count >= MAX_USER_CREDENTIALS_PER_RP) {
                    Outcome.Error(DomainError.OperationDenied("Too many credentials for this user and RP"))
                } else {
                    Outcome.Success(Unit)
                }
            } finally {
                rows.forEach { it.eraseSensitiveMetadata() }
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Credential creation validation failed" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    private fun countOwnedUserCredentials(
        rows: List<PasskeyCredentialEntity>,
        userId: CharSequence,
    ): Int {
        var count = 0
        for (row in rows) {
            val credential = row.toDomainModel(publicKeyDecoder, metadataProtectionService).getOrNull() ?: continue
            try {
                if (credential.userId.contentEquals(userId, ignoreCase = true)) count++
            } finally {
                credential.eraseSensitiveText()
            }
        }
        return count
    }

    // ── Relying Party ─────────────────────────────────────────────────────────

    /** Implements [CredentialRepository.getRelyingParty] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getRelyingParty(rpId: RpId): RelyingParty? = getRelyingPartyForText(rpId.value)

    /** Borrows the query identifier and transfers the reconstructed RP model to its caller. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getRelyingPartyForText(rpId: CharSequence): RelyingParty? =
        try {
            relyingPartyDao.getRelyingPartyByText(rpId)?.toDomainModel()
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to get RP" }
            null
        }

    /** Implements [CredentialRepository.saveRelyingParty] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError> =
        try {
            relyingPartyDao.insertOrUpdateRelyingParty(rp)
            Outcome.Success(Unit)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to save relying party" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Implements [CredentialRepository.updateRelyingParty] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun updateRelyingParty(
        rpId: RpId,
        update: (RelyingParty) -> RelyingParty,
    ): Outcome<Unit, DomainError> {
        var currentRp: RelyingParty? = null
        var updatedRp: RelyingParty? = null
        return try {
            val loadedRp = getRelyingParty(rpId) ?: return Outcome.Error(DomainError.NotFound("RP not found"))
            currentRp = loadedRp
            // The callback borrows currentRp and transfers ownership of its result to this method.
            val resultRp = update(loadedRp)
            updatedRp = resultRp
            relyingPartyDao.updateRelyingParty(resultRp)
            Outcome.Success(Unit)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to update relying party" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        } finally {
            // An identity result is one owner and must be erased only once.
            if (updatedRp !== currentRp) updatedRp?.eraseSensitiveText()
            currentRp?.eraseSensitiveText()
        }
    }

    // ── User Consent ──────────────────────────────────────────────────────────

    /** Implements [CredentialRepository.saveUserConsent] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun saveUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError> =
        try {
            userConsentRecordDao.insertConsent(consent)
            Outcome.Success(Unit)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to save user consent" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Implements [CredentialRepository.getRecentUserConsent] using protected rows and transferring results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getRecentUserConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<UserConsentRecord> {
        val consentRows =
            try {
                userConsentRecordDao.getRecentConsent(rpId, limit)
            } catch (_: NoSuchElementException) {
                Logger.e { "CredentialRepository: Failed to get recent user consent" }
                return flowOf()
            }

        return flow {
            consentRows.collect { rows ->
                var consent: UserConsentRecord? = null
                var transferred = false
                try {
                    consent = rows.firstOrNull()?.toDomainModel()
                        ?: throw NoSuchElementException("Empty consent record list")
                    emit(consent)
                    transferred = true
                } finally {
                    if (!transferred) consent?.eraseSensitiveText()
                    rows.forEach { it.eraseSensitiveMetadata() }
                }
            }
        }
    }

    /** Implements [CredentialRepository.isUserConsentRequired] without retaining borrowed lookup values. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun isUserConsentRequired(
        rpId: RpId,
        operationType: String,
    ): Boolean {
        // By default, user consent is always required for FIDO2 operations
        return true
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    /** FR-005 — Includes the persisted-policy-derived user-verification count. */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getCredentialStatistics(): CredentialStatistics =
        try {
            val allCredentials = mutableListOf<PasskeyCredential>()
            try {
                getAllCredentials().collect { allCredentials.add(it) }

                val now = timeProvider.now()
                val cutoff = now - RECENT_USAGE_CUTOFF_DAYS.days
                val expired = allCredentials.count { (now - it.createdAt).inWholeDays > EXPIRY_DAYS_THRESHOLD }
                val recentlyUsed = allCredentials.count { it.lastUsedAt > cutoff }
                val needsUV = allCredentials.count { it.credProtectPolicy.toLong() == CRED_PROTECT_UV_REQUIRED }
                val avgAge =
                    if (allCredentials.isNotEmpty()) {
                        allCredentials.asSequence().map { (now - it.createdAt).inWholeDays }.average()
                    } else {
                        0.0
                    }

                CredentialStatistics(
                    totalCredentials = allCredentials.size,
                    expiredCredentials = expired,
                    recentlyUsedCredentials = recentlyUsed,
                    credentialsRequiringUserVerification = needsUV,
                    averageAgeDays = avgAge,
                )
            } finally {
                allCredentials.forEach { it.eraseSensitiveText() }
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to calculate credential statistics" }
            CredentialStatistics(0, 0, 0, 0, 0.0)
        }

    // ── Bulk deletion / reset ─────────────────────────────────────────────────

    /** Implements [CredentialRepository.deleteAllCredentials] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun deleteAllCredentials(rpId: RpId?): Outcome<Unit, DomainError> =
        try {
            when (val batch = destructiveBatchReader.read(rpId = rpId)) {
                is Outcome.Success ->
                    when (val result = deleteCredentialBatch(batch.data)) {
                        is Outcome.Success -> Outcome.Success(Unit)
                        is Outcome.Error -> result
                    }
                is Outcome.Error -> batch
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to delete credentials" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Implements [CredentialRepository.resetAuthenticator] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun resetAuthenticator(): Outcome<Unit, DomainError> =
        try {
            when (val batch = destructiveBatchReader.read()) {
                is Outcome.Error -> return batch
                is Outcome.Success ->
                    when (val result = deleteCredentialBatch(batch.data)) {
                        is Outcome.Error -> return result
                        is Outcome.Success -> Unit
                    }
            }
            // Finalize: relyingPartyDao.deleteAll() / userConsentRecordDao.deleteAll() once DAOs support it
            Outcome.Success(Unit)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to reset authenticator" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }

    /** Deletes credentials in flow order, stopping at the first typed failure while erasing each owner. */
    private suspend fun deleteCredentialBatch(credentials: List<PasskeyCredential>): Outcome<Int, DomainError> {
        var deletedCount = 0
        try {
            for (credential in credentials) {
                when (val result = deleteCredential(credential.id)) {
                    is Outcome.Success -> deletedCount++
                    is Outcome.Error -> return Outcome.Error(result.error)
                }
            }
            return Outcome.Success(deletedCount)
        } finally {
            credentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }

    /** Implements [CredentialRepository.updateLabel] through the aggregate and protected projections. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ): Outcome<Unit, DomainError> =
        try {
            passkeyCredentialDao.updateLabel(credentialId, label)
            Outcome.Success(Unit)
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to update credential label" }
            Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
        }
}
