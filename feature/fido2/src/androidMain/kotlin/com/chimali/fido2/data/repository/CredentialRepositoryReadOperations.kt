package com.chimali.fido2.data.repository

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrNull
import com.chimali.core.common.result.onFailure
import com.chimali.core.common.result.onSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.util.matchesCredentialId
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Performs credential and metadata reads while owning and releasing each hydrated result. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class CredentialRepositoryReadOperations(
    private val passkeyCredentialDao: PasskeyCredentialDao,
    private val publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder,
    private val timeProvider: TimeProvider,
    private val metadataProtectionService: CredentialMetadataProtectionService,
    private val onSummaryCreated: (CredentialSummary) -> Unit,
) {
    private companion object {
        private const val CRED_PROTECT_UV_REQUIRED = 3L
        private const val STORAGE_OPERATION_FAILED = "Credential storage operation failed"
    }

    /**
     * Reconstructs one caller-owned credential from its persisted row. The mapper validates the
     * record and erases the source row on every outcome; malformed rows and SQL failures return
     * `null`. The caller owns and must erase a successfully returned credential.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential? {
        val entity =
            try {
                passkeyCredentialDao.getCredentialById(credentialId)
            } catch (_: android.database.SQLException) {
                Logger.e { "CredentialRepository: Failed to get credential by ID" }
                return null
            } ?: return null
        return entity.toDomainModel(publicKeyDecoder, metadataProtectionService).getOrNull()
    }

    /**
     * Returns a cold flow that reads rows when collected. Each valid credential transfers to the
     * collector only after successful emission; failed emission erases that model. Malformed rows
     * are skipped, every source row is erased, and SQL failure is logged as an empty completion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential> =
        flow {
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get credentials by RP ID") {
                    passkeyCredentialDao.getCredentialsByRpText(rpId.value).first()
                }
                    ?: return@flow
            try {
                entities.forEach { entity ->
                    entity
                        .toDomainModel(
                            publicKeyDecoder,
                            metadataProtectionService,
                        ).onSuccess { emitOwnedCredential(it) }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Returns a cold flow that defers the user lookup until collection. Valid hydrated models
     * transfer only after downstream emission succeeds; failed emission erases its model. Invalid
     * rows are skipped, source rows are erased, and SQL failure is logged as empty completion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential> =
        flow {
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get credentials by user ID") {
                    passkeyCredentialDao.getCredentialsByUserId(userId).first()
                }
                    ?: return@flow
            try {
                entities.forEach { entity ->
                    entity
                        .toDomainModel(
                            publicKeyDecoder,
                            metadataProtectionService,
                        ).onSuccess { emitOwnedCredential(it) }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Returns a cold flow that reads all rows at collection time. Valid hydrated models transfer
     * only after downstream emission succeeds; rejected emission erases the model. Malformed rows
     * are skipped, source rows are erased, and SQL failure is logged as empty completion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAllCredentials(): Flow<PasskeyCredential> =
        flow {
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get all credentials") {
                    passkeyCredentialDao.getAllCredentials().first()
                } ?: return@flow
            try {
                entities.forEach { entity ->
                    entity.toDomainModel(publicKeyDecoder, metadataProtectionService).onSuccess {
                        emitOwnedCredential(it)
                    }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Hydrates one page, skipping malformed rows and returning an owned credential list. Source
     * rows are always erased; partial models are erased if mapping or result construction fails.
     * SQL failures return a database error, and callers own successful models until erased.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val entities =
            try {
                passkeyCredentialDao.getPagedCredentials(limit, offset)
            } catch (_: android.database.SQLException) {
                Logger.e { "CredentialRepository: Failed to get paged credentials" }
                return Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
            }
        val validCredentials = mutableListOf<PasskeyCredential>()
        var transferred = false
        try {
            for (entity in entities) {
                entity
                    .toDomainModel(publicKeyDecoder, metadataProtectionService)
                    .onSuccess { validCredentials.admitCredential(it) }
                    .onFailure { Logger.w { "Skipping malformed credential metadata" } }
            }
            val result =
                createOwnedBatchResult(validCredentials, PasskeyCredential::eraseSensitiveText) {
                    Outcome.Success(it)
                }
            transferred = true
            return result
        } finally {
            entities.forEach { it.eraseSensitiveMetadata() }
            if (!transferred) validCredentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }

    /**
     * Hydrates one RP-scoped page, skipping malformed rows and transferring the successful list
     * to the caller. Source rows are always erased and partial models are erased on failure; SQL
     * errors from acquisition return a database error.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val entities =
            try {
                passkeyCredentialDao.getPagedCredentialsByRpId(rpId, limit, offset)
            } catch (_: android.database.SQLException) {
                Logger.e { "CredentialRepository: Failed to get paged credentials for RP" }
                return Outcome.Error(DomainError.DatabaseError(STORAGE_OPERATION_FAILED))
            }
        val validCredentials = mutableListOf<PasskeyCredential>()
        var transferred = false
        try {
            for (entity in entities) {
                entity
                    .toDomainModel(publicKeyDecoder, metadataProtectionService)
                    .onSuccess { validCredentials.admitCredential(it) }
                    .onFailure { Logger.w { "Skipping malformed credential metadata" } }
            }
            val result =
                createOwnedBatchResult(validCredentials, PasskeyCredential::eraseSensitiveText) {
                    Outcome.Success(it)
                }
            transferred = true
            return result
        } finally {
            entities.forEach { it.eraseSensitiveMetadata() }
            if (!transferred) validCredentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }

    /**
     * Returns owned full credential models for an RP, erasing source rows and any partial models
     * on failure. Malformed rows are skipped; SQL acquisition failure preserves empty success.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialsForRp(rpId: RpId): Outcome<List<PasskeyCredential>, DomainError> {
        val entities =
            try {
                passkeyCredentialDao.getCredentialsByRpText(rpId.value).first()
            } catch (_: android.database.SQLException) {
                Logger.e { "CredentialRepository: Failed to get credentials for RP" }
                return Outcome.Success(emptyList())
            }
        val credentials = mutableListOf<PasskeyCredential>()
        var transferred = false
        try {
            // This API returns full domain models, so each row is reconstructed with its
            // registered public-key metadata. Assertion selection uses the separate summary read.
            entities.forEach { entity ->
                when (val result = entity.toDomainModel(publicKeyDecoder, metadataProtectionService)) {
                    is Outcome.Success -> credentials.admitCredential(result.data)
                    is Outcome.Error -> Unit
                }
            }
            val result =
                createOwnedBatchResult(credentials, PasskeyCredential::eraseSensitiveText) {
                    Outcome.Success(it)
                }
            transferred = true
            return result
        } finally {
            entities.forEach { it.eraseSensitiveMetadata() }
            if (!transferred) credentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }

    /**
     * Reads metadata-only assertion candidates for an RP without key derivation. Successful
     * summaries are independently owned by the caller; malformed persisted identity or profile
     * data returns a validation error and erases partial summaries/source rows. SQL failure keeps
     * the existing empty-success fallback.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError> =
        getCredentialSummariesForRpText(rpId.value)

    /**
     * Borrows [rpId] for this call and returns caller-owned summaries without key derivation.
     * Malformed persisted rows reject the batch; source rows and partial summaries are erased on
     * failure. SQL failures retain the empty-success fallback.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialSummariesForRpText(rpId: CharSequence): Outcome<List<CredentialSummary>, DomainError> =
        run {
            // Keep the empty-result fallback scoped to SQLDelight acquisition. Mapping and the
            // ownership observer below must not convert a consumer/callback failure into success.
            val entities =
                try {
                    passkeyCredentialDao.getCredentialsByRpText(rpId).first()
                } catch (_: android.database.SQLException) {
                    Logger.e { "CredentialRepository: Failed to get credential summaries for RP" }
                    return@run Outcome.Success(emptyList())
                } catch (_: IllegalArgumentException) {
                    // Preserve the DAO's malformed-query fallback while keeping mapping and
                    // observer failures outside this acquisition boundary.
                    Logger.w { "CredentialRepository: Rejected malformed credential summary query" }
                    return@run Outcome.Error(
                        DomainError.ValidationError("Malformed credential summary metadata"),
                    )
                }
            val summaries = mutableListOf<CredentialSummary>()
            var transferred = false
            try {
                for (entity in entities) {
                    val summary =
                        try {
                            entity.toCredentialSummary(rpId)
                        } catch (_: IllegalArgumentException) {
                            Logger.w { "CredentialRepository: Rejected malformed credential summary" }
                            return@run Outcome.Error(
                                DomainError.ValidationError("Malformed credential summary metadata"),
                            )
                        }
                    summaries.admitCredentialSummary(summary)
                    onSummaryCreated(summary)
                }
                val result =
                    createOwnedBatchResult(summaries, CredentialSummary::eraseSensitiveText) {
                        Outcome.Success(it)
                    }
                transferred = true
                result
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
                if (!transferred) summaries.forEach(CredentialSummary::eraseSensitiveText)
            }
        }

    /**
     * Resolves the supplied IDs and optional RP filter into caller-owned models. Credentials
     * removed by the filter are erased; partial results are erased if resolution fails. SQL errors
     * retain the empty-success fallback, and callers own returned models until erased.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialsByIds(
        credentialIds: Set<CredentialId>,
        rpId: RpId?,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val credentials = mutableListOf<PasskeyCredential>()
        var transferred = false
        try {
            credentialIds.forEach { id -> getCredentialById(id)?.let(credentials::admitCredential) }
            rpId?.let { filterCredentialsForRelyingParty(credentials, it) }
            val result =
                createOwnedBatchResult(credentials, PasskeyCredential::eraseSensitiveText) {
                    Outcome.Success(it)
                }
            transferred = true
            return result
        } finally {
            if (!transferred) credentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }

    /** Erases credentials filtered from an owned result batch before it is returned. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun filterCredentialsForRelyingParty(
        credentials: MutableList<PasskeyCredential>,
        rpId: RpId,
    ) {
        val iterator = credentials.iterator()
        while (iterator.hasNext()) {
            val credential = iterator.next()
            if (!credential.rpId.contentEquals(rpId.value, ignoreCase = true)) {
                iterator.remove()
                credential.eraseSensitiveText()
            }
        }
    }

    /** Counts rows for [rpId] without hydrating credentials; source metadata is erased afterward.
     * SQL failure is logged and returns zero.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getCredentialCountByRpId(rpId: RpId): Int =
        try {
            val entities = passkeyCredentialDao.getCredentialsByRpText(rpId.value).first()
            try {
                entities.size
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        } catch (_: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to get credential count for RP" }
            0
        }

    // ── Querying / searching ──────────────────────────────────────────────────

    /**
     * Returns a cold search flow. The caller must keep [query] valid through collection because
     * its sensitive text is borrowed lazily. Matching models transfer only after successful
     * emission; nonmatches, rejected emissions and source rows are erased. Malformed rows are
     * skipped and SQL failure is logged as empty completion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun searchCredentials(query: SensitiveCredentialText): Flow<PasskeyCredential> =
        flow {
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Credential search failed") {
                    passkeyCredentialDao.getAllCredentials().first()
                } ?: return@flow
            try {
                entities.forEach { entity ->
                    entity
                        .toDomainModel(publicKeyDecoder, metadataProtectionService)
                        .getOrNull()
                        ?.let { credential ->
                            if (credential.userName.containsIgnoreCase(query) ||
                                credential.userDisplayName.containsIgnoreCase(query)
                            ) {
                                emitOwnedCredential(credential)
                            } else {
                                credential.eraseSensitiveText()
                            }
                        }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Returns a cold flow of models last used before the computed cutoff; collection performs the
     * query and hydration. Successful emissions transfer ownership, failed emissions and source
     * rows are erased, malformed rows are skipped, and SQL failure completes empty after logging.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> =
        flow {
            val cutoff = timeProvider.now() - days.days
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get recently unused credentials") {
                    passkeyCredentialDao.getAllCredentials().first()
                } ?: return@flow
            try {
                entities
                    .filter { entity ->
                        val lastUsed =
                            entity.last_used_at
                                ?.let { Instant.fromEpochMilliseconds(it) }
                                ?: Instant.fromEpochMilliseconds(entity.created_at)
                        lastUsed < cutoff
                    }.forEach { entity ->
                        entity
                            .toDomainModel(
                                publicKeyDecoder,
                                metadataProtectionService,
                            ).onSuccess { emitOwnedCredential(it) }
                    }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Returns a cold flow of rows whose persisted credProtect policy requires UV. Hydrated models
     * transfer only after successful emission; rejected emissions and source rows are erased.
     * Malformed rows are skipped and SQL failure is logged as empty completion.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> =
        flow {
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get credentials requiring UV") {
                    passkeyCredentialDao.getAllCredentials().first()
                } ?: return@flow
            try {
                entities.filter { it.cred_protect_policy == CRED_PROTECT_UV_REQUIRED }.forEach { entity ->
                    entity
                        .toDomainModel(
                            publicKeyDecoder,
                            metadataProtectionService,
                        ).onSuccess { emitOwnedCredential(it) }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }

    /**
     * Returns a cold flow of credentials older than [maxAgeDays], evaluating the cutoff at
     * collection time. Valid models transfer only after accepted emission; failed emissions and
     * source rows are erased. Malformed rows are skipped and SQL failure is logged as empty
     * completion.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> =
        flow {
            val cutoff = timeProvider.now() - maxAgeDays.days
            val entities =
                loadCredentialRowsOrNull("CredentialRepository: Failed to get expired credentials") {
                    passkeyCredentialDao.getAllCredentials().first()
                } ?: return@flow
            try {
                entities.filter { Instant.fromEpochMilliseconds(it.created_at) < cutoff }.forEach { entity ->
                    entity
                        .toDomainModel(
                            publicKeyDecoder,
                            metadataProtectionService,
                        ).onSuccess { emitOwnedCredential(it) }
                }
            } finally {
                entities.forEach { it.eraseSensitiveMetadata() }
            }
        }
}

/** Maps one persisted row to an independently owned metadata-only summary. */
@Req("NFR-SEC-041", "TR-AUTH-031")
private fun PasskeyCredentialEntity.toCredentialSummary(rpId: CharSequence): CredentialSummary {
    require(cose_algorithm == cose_algorithm.toInt().toLong()) { "Invalid persisted algorithm value" }
    val credentialId = CredentialId.fromEncoded(credential_id)
    require(CredentialId.fromEncoded(id).matchesCredentialId(credentialId)) {
        "Inconsistent stored credential identity"
    }
    CredentialKeyDescriptor(
        credentialId = credentialId,
        coseAlgorithm = cose_algorithm.toInt(),
        derivationProfile = derivation_profile,
    )
    return CredentialSummary.fromText(
        id = id,
        rpId = rpId,
        credentialId = credentialId,
        lastUsedAt = Instant.fromEpochMilliseconds(last_used_at ?: created_at),
        coseAlgorithm = cose_algorithm.toInt(),
        derivationProfile = derivation_profile,
        storedPublicKeyEncoding =
            java.util.Base64
                .getDecoder()
                .decode(public_key),
        credProtectPolicy = CredentialProtectionPolicy.fromPersisted(cred_protect_policy),
    )
}

/** Retains a newly hydrated credential only after the local result batch accepts it. */
internal fun MutableList<PasskeyCredential>.admitCredential(
    credential: PasskeyCredential,
    admit: () -> Boolean = { add(credential) },
) {
    var admitted = false
    try {
        check(admit()) { "Credential batch admission failed" }
        admitted = true
    } finally {
        if (!admitted) credential.eraseSensitiveText()
    }
}

/** Retains a newly hydrated summary only after the local result batch accepts it. */
internal fun MutableList<CredentialSummary>.admitCredentialSummary(
    summary: CredentialSummary,
    admit: () -> Boolean = { add(summary) },
) {
    var admitted = false
    try {
        check(admit()) { "Credential-summary batch admission failed" }
        admitted = true
    } finally {
        if (!admitted) summary.eraseSensitiveText()
    }
}

/** Keeps an owned batch local until its caller-visible result wrapper has been constructed. */
internal fun <T, R> createOwnedBatchResult(
    batch: List<T>,
    erase: (T) -> Unit,
    createResult: (List<T>) -> R,
): R {
    var transferred = false
    try {
        val result = createResult(batch)
        transferred = true
        return result
    } finally {
        if (!transferred) batch.forEach(erase)
    }
}

/** Restricts database-error handling to row acquisition so downstream failures still propagate. */
@Req("NFR-SEC-041", "TR-AUTH-031")
private suspend fun loadCredentialRowsOrNull(
    failureMessage: String,
    load: suspend () -> List<PasskeyCredentialEntity>,
): List<PasskeyCredentialEntity>? =
    try {
        load()
    } catch (_: android.database.SQLException) {
        Logger.e { failureMessage }
        null
    }

/** Transfers a hydrated credential only after downstream accepts it. */
@Req("NFR-SEC-041", "TR-AUTH-031")
private suspend fun FlowCollector<PasskeyCredential>.emitOwnedCredential(credential: PasskeyCredential) {
    var transferred = false
    try {
        emit(credential)
        transferred = true
    } finally {
        if (!transferred) credential.eraseSensitiveText()
    }
}
