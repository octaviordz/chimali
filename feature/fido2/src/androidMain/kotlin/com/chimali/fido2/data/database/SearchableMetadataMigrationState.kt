package com.chimali.fido2.data.database

import co.touchlab.kermit.Logger
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import java.util.concurrent.atomic.AtomicReference

enum class MigrationStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
}

object SearchableMetadataMigrationState {
    private const val MAX_CONSENT_RECORDS = 1_000_000L

    private val log = Logger.withTag("SearchableMetadataMigration")
    private val _status = AtomicReference(MigrationStatus.PENDING)
    private val _lastError = AtomicReference<Throwable?>(null)

    val status: MigrationStatus get() = _status.get()
    val lastError: Throwable? get() = _lastError.get()

    /**
     * Executes the FIDO2 searchable metadata migration.
     * This migrates legacy credentials, relying parties, and user consent records
     * by deriving deterministic lookup tokens and encrypting metadata envelopes.
     *
     * The migration is idempotent, transactional, and handles failures gracefully
     * by supporting retries.
     */
    @Suppress("TooGenericExceptionCaught")
    fun migrate(
        database: Fido2Database,
        protectionService: CredentialMetadataProtectionService,
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
                for (cred in credentials) {
                    if (cred.rp_id_index == null || cred.user_id_index == null || cred.encrypted_metadata == null) {
                        log.i { "Migrating credential ID: ${cred.id}" }
                        val rpIdIndex = protectionService.getRpIdIndex(cred.rp_id)
                        val userIdIndex = protectionService.getUserIdIndex(cred.user_id)
                        val metadataJson = """{"rpId":"${cred.rp_id}","userId":"${cred.user_id}"}"""
                        val encryptedMetadata = protectionService.encryptMetadata(metadataJson, cred.rp_id)

                        database.passkeyCredentialQueries.update_migration_fields(
                            rp_id_index = rpIdIndex,
                            user_id_index = userIdIndex,
                            encrypted_metadata = encryptedMetadata,
                            id = cred.id,
                        )
                    }
                }

                // 2. Migrate relying parties
                val relyingParties = database.relyingPartyQueries.select_all().executeAsList()
                log.d { "Found ${relyingParties.size} relying parties to inspect for migration." }
                for (rp in relyingParties) {
                    if (rp.encrypted_metadata == null) {
                        log.i { "Migrating relying party ID: ${rp.id}" }
                        val metadataJson = """{"id":"${rp.id}","name":"${rp.name}"}"""
                        val encryptedMetadata = protectionService.encryptRelyingPartyMetadata(metadataJson)

                        database.relyingPartyQueries.update(
                            last_used_at = rp.last_used_at,
                            credential_count = rp.credential_count,
                            icon_url = rp.icon_url,
                            is_blocked = rp.is_blocked,
                            name = rp.name,
                            encrypted_metadata = encryptedMetadata,
                            id = rp.id,
                        )
                    }
                }

                // 3. Migrate user consent records
                val consents = database.userConsentRecordQueries.select_all(MAX_CONSENT_RECORDS).executeAsList()
                log.d { "Found ${consents.size} user consent records to inspect for migration." }
                for (consent in consents) {
                    if (consent.rp_id_index == null) {
                        log.i { "Migrating user consent record ID: ${consent.id}" }
                        val rpIdIndex = protectionService.getRpIdIndex(consent.rp_id)
                        database.userConsentRecordQueries.update_rp_id_index(
                            rp_id_index = rpIdIndex,
                            id = consent.id,
                        )
                    }
                }
            }

            _status.set(MigrationStatus.SUCCESS)
            _lastError.set(null)
            log.i { "Searchable metadata migration completed successfully." }
            return Result.success(Unit)
        } catch (e: Exception) {
            _status.set(MigrationStatus.FAILED)
            _lastError.set(e)
            log.e(e) { "Searchable metadata migration failed." }
            return Result.failure(e)
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
