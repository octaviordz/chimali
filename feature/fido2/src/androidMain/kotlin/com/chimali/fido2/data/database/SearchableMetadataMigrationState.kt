package com.chimali.fido2.data.database

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.SensitiveCredentialText
import java.util.concurrent.atomic.AtomicReference

enum class MigrationStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
}

object SearchableMetadataMigrationState {
    private const val MAX_CONSENT_RECORDS = 1_000_000L
    private const val MIGRATION_FAILURE_MESSAGE = "Searchable metadata migration failed"

    private val log = Logger.withTag("SearchableMetadataMigration")
    private val _status = AtomicReference(MigrationStatus.PENDING)
    private val _lastError = AtomicReference<Throwable?>(null)

    val status: MigrationStatus get() = _status.get()
    val lastError: Throwable? get() = _lastError.get()

    /**
     * Executes the transactional, retryable searchable-metadata migration. The database owns row byte arrays;
     * this operation borrows them only during decoding and erases them on every exit. Newly decoded text owners
     * and lookup-index arrays remain operation-owned and are erased before each row completes. Failures return a
     * fixed cause-free result so row values and decoder details do not escape through diagnostics.
     */
    @Suppress("TooGenericExceptionCaught")
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun migrate(
        database: Fido2Database,
        protectionService: CredentialMetadataProtectionService,
    ): Result<Unit> = migrateWithTextDecoder(database, protectionService, SensitiveCredentialText::fromUtf8Bytes)

    /** Testable decoding boundary; the decoder's returned owner transfers to this operation for immediate cleanup. */
    @Suppress("TooGenericExceptionCaught")
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun migrateWithTextDecoder(
        database: Fido2Database,
        protectionService: CredentialMetadataProtectionService,
        textDecoder: (ByteArray) -> SensitiveCredentialText,
    ): Result<Unit> {
        if (!_status.compareAndSet(MigrationStatus.PENDING, MigrationStatus.RUNNING) &&
            !_status.compareAndSet(MigrationStatus.FAILED, MigrationStatus.RUNNING)
        ) {
            log.d { "Migration already in state: $status, skipping execution." }
            if (status == MigrationStatus.SUCCESS) {
                return Result.success(Unit)
            }
            return Result.failure(IllegalStateException("Migration is already running or completed."))
        }

        log.i { "Starting searchable metadata migration..." }
        try {
            database.transaction {
                // 1. Migrate passkey credentials
                val credentials = database.passkeyCredentialQueries.select_all().executeAsList()
                log.d { "Found ${credentials.size} credentials to inspect for migration." }
                try {
                    for (cred in credentials) {
                        if (cred.rp_id_index == null || cred.user_id_index == null || cred.encrypted_metadata == null) {
                            log.i { "Migrating credential metadata." }
                            var rpId: SensitiveCredentialText? = null
                            var userId: SensitiveCredentialText? = null
                            var rpIdIndex: ByteArray? = null
                            var userIdIndex: ByteArray? = null
                            try {
                                rpId = textDecoder(cred.rp_id)
                                userId = textDecoder(cred.user_id)
                                val rpIdOwner = requireNotNull(rpId)
                                val userIdOwner = requireNotNull(userId)
                                rpIdIndex = protectionService.getRpIdIndex(rpIdOwner)
                                userIdIndex = protectionService.getUserIdIndex(userIdOwner)
                                val encryptedMetadata =
                                    protectionService.encryptCredentialMetadata(
                                        rpIdOwner,
                                        userIdOwner,
                                    )

                                database.passkeyCredentialQueries.update_migration_fields(
                                    rp_id_index = requireNotNull(rpIdIndex),
                                    user_id_index = requireNotNull(userIdIndex),
                                    encrypted_metadata = encryptedMetadata,
                                    id = cred.id,
                                )
                            } finally {
                                rpId?.erase()
                                userId?.erase()
                                rpIdIndex?.fill(0)
                                userIdIndex?.fill(0)
                            }
                        }
                    }
                } finally {
                    credentials.forEach { it.eraseSensitiveMetadata() }
                }

                // 2. Migrate relying parties
                val relyingParties = database.relyingPartyQueries.select_all().executeAsList()
                log.d { "Found ${relyingParties.size} relying parties to inspect for migration." }
                try {
                    for (rp in relyingParties) {
                        if (rp.encrypted_metadata == null) {
                            var rpId: SensitiveCredentialText? = null
                            var rpName: SensitiveCredentialText? = null
                            try {
                                rpId = textDecoder(rp.id)
                                rpName = textDecoder(rp.name)
                                val encryptedMetadata =
                                    protectionService.encryptRelyingPartyMetadata(
                                        requireNotNull(rpId),
                                        requireNotNull(rpName),
                                    )
                                database.relyingPartyQueries.update(
                                    last_used_at = rp.last_used_at,
                                    credential_count = rp.credential_count,
                                    icon_url = rp.icon_url,
                                    is_blocked = rp.is_blocked,
                                    name = rp.name,
                                    encrypted_metadata = encryptedMetadata,
                                    id = rp.id,
                                )
                            } finally {
                                rpId?.erase()
                                rpName?.erase()
                            }
                        }
                    }
                } finally {
                    relyingParties.forEach { it.eraseSensitiveMetadata() }
                }

                // 3. Migrate user consent records
                val consents = database.userConsentRecordQueries.select_all(MAX_CONSENT_RECORDS).executeAsList()
                log.d { "Found ${consents.size} user consent records to inspect for migration." }
                try {
                    for (consent in consents) {
                        if (consent.rp_id_index == null) {
                            val rpId = textDecoder(consent.rp_id)
                            var rpIdIndex: ByteArray? = null
                            try {
                                rpIdIndex = protectionService.getRpIdIndex(rpId)
                                database.userConsentRecordQueries.update_rp_id_index(
                                    rp_id_index = requireNotNull(rpIdIndex),
                                    id = consent.id,
                                )
                            } finally {
                                rpIdIndex?.fill(0)
                                rpId.erase()
                            }
                        }
                    }
                } finally {
                    consents.forEach { it.eraseSensitiveMetadata() }
                }
            }

            _status.set(MigrationStatus.SUCCESS)
            _lastError.set(null)
            log.i { "Searchable metadata migration completed successfully." }
            return Result.success(Unit)
        } catch (_: Exception) {
            _status.set(MigrationStatus.FAILED)
            val redactedFailure = IllegalStateException(MIGRATION_FAILURE_MESSAGE)
            _lastError.set(redactedFailure)
            log.e { MIGRATION_FAILURE_MESSAGE }
            return Result.failure(redactedFailure)
        }
    }

    /**
     * Resets the migration state to PENDING to force a re-run for testing purposes.
     */
    fun resetStateForTesting() {
        _status.set(MigrationStatus.PENDING)
        _lastError.set(null)
    }
}
