package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Data Access Object for PasskeyCredential entities using SQLDelight.
 * Provides database operations for credential metadata storage.
 */
@Single
class PasskeyCredentialDao(
    private val database: Fido2Database,
    private val timeProvider: TimeProvider,
    private val metadataProtectionService: CredentialMetadataProtectionService,
) {
    /**
     * Inserts a new credential into the database.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun insertCredential(credential: PasskeyCredential) {
        var rpIdBytes: ByteArray? = null
        var rpNameBytes: ByteArray? = null
        var userIdBytes: ByteArray? = null
        var userNameBytes: ByteArray? = null
        var userDisplayNameBytes: ByteArray? = null
        var rpIdIndex: ByteArray? = null
        var userIdIndex: ByteArray? = null
        try {
            rpIdBytes = credential.rpId.toUtf8Bytes()
            rpNameBytes = credential.rpId.toUtf8Bytes()
            userIdBytes = credential.userId.toUtf8Bytes()
            userNameBytes = credential.userName.toUtf8Bytes()
            userDisplayNameBytes = credential.userDisplayName.toUtf8Bytes()
            rpIdIndex = metadataProtectionService.getRpIdIndex(credential.rpId)
            userIdIndex = metadataProtectionService.getUserIdIndex(credential.userId)
            val encryptedMetadata =
                metadataProtectionService.encryptCredentialMetadata(
                    credential.rpId,
                    credential.userId,
                )
            database.passkeyCredentialQueries.insert(
                id = credential.id.encoded,
                created_at = credential.createdAt.toEpochMilliseconds(),
                last_used_at = credential.lastUsedAt.toEpochMilliseconds(),
                updated_at =
                    maxOf(
                        timeProvider.now(),
                        credential.createdAt,
                        credential.lastUsedAt,
                    ).toEpochMilliseconds(),
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(credential.aaguid),
                cose_algorithm = credential.coseAlgorithm.toLong(),
                credential_id = credential.id.encoded,
                cred_protect_policy = credential.credProtectPolicy.toLong(),
                derivation_profile = credential.derivationProfile,
                label = credential.label,
                public_key =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(credential.publicKey.encoded),
                rp_id = requireNotNull(rpIdBytes),
                // fallback
                rp_name = requireNotNull(rpNameBytes),
                sign_count = credential.signCount,
                user_display_name = requireNotNull(userDisplayNameBytes),
                user_id = requireNotNull(userIdBytes),
                user_name = requireNotNull(userNameBytes),
                rp_id_index = requireNotNull(rpIdIndex),
                user_id_index = requireNotNull(userIdIndex),
                encrypted_metadata = encryptedMetadata,
            )
        } finally {
            rpIdBytes?.fill(0)
            rpNameBytes?.fill(0)
            userIdBytes?.fill(0)
            userNameBytes?.fill(0)
            userDisplayNameBytes?.fill(0)
            rpIdIndex?.fill(0)
            userIdIndex?.fill(0)
        }
    }

    /**
     * Replaces all projections for this RP/user and inserts the new credential atomically.
     * The event append is deliberately owned by the repository and commits before this phase.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun replaceCredentialsForRpAndUser(credential: PasskeyCredential) {
        val context = currentCoroutineContext()
        context.ensureActive()
        var rpIdIndex: ByteArray? = null
        var userIdIndex: ByteArray? = null
        try {
            rpIdIndex = metadataProtectionService.getRpIdIndex(credential.rpId)
            userIdIndex = metadataProtectionService.getUserIdIndex(credential.userId)
            database.transaction {
                context.ensureActive()
                val existing =
                    database.passkeyCredentialQueries
                        .select_by_rp_id_and_user_id(
                            rp_id_index = requireNotNull(rpIdIndex),
                            user_id_index = requireNotNull(userIdIndex),
                        ).executeAsList()

                deleteAndEraseCredentialRows(
                    existing = existing,
                    ensureActive = context::ensureActive,
                    deleteById = { id -> database.passkeyCredentialQueries.delete_by_id(id = id) },
                )

                context.ensureActive()
                insertCredential(credential)
                context.ensureActive()
            }
        } finally {
            rpIdIndex?.fill(0)
            userIdIndex?.fill(0)
        }
    }

    /** Deletes rows loaded only for replacement and erases their owned mutable metadata on every exit path. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun deleteAndEraseCredentialRows(
        existing: List<PasskeyCredentialEntity>,
        ensureActive: () -> Unit,
        deleteById: (String) -> Unit,
    ) {
        try {
            existing.forEach { entity ->
                ensureActive()
                deleteById(entity.id)
            }
        } finally {
            existing.forEach(PasskeyCredentialEntity::eraseSensitiveMetadata)
        }
    }

    /**
     * Retrieves a credential by its ID.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialById(credentialId: CredentialId): PasskeyCredentialEntity? =
        database.passkeyCredentialQueries
            .select_by_id(id = credentialId.encoded)
            .executeAsOneOrNull()

    /**
     * Retrieves all credentials for a specific relying party.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByRpId(rpId: RpId): Flow<List<PasskeyCredentialEntity>> = getCredentialsByRpText(rpId.value)

    /** Borrows RP text for the collection lifetime and erases the owned lookup token on all exits. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByRpText(rpId: CharSequence): Flow<List<PasskeyCredentialEntity>> =
        flow {
            val rpIdIndex = metadataProtectionService.getRpIdIndex(rpId)
            try {
                emitAll(
                    database.passkeyCredentialQueries
                        .select_by_rp_id(rp_id_index = rpIdIndex)
                        .asFlow()
                        .map { query -> query.executeAsList() },
                )
            } finally {
                rpIdIndex.fill(0)
            }
        }

    /**
     * Retrieves a paginated list of credentials for a specific relying party.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> {
        val rpIdIndex = metadataProtectionService.getRpIdIndex(rpId.value)
        try {
            return database.passkeyCredentialQueries
                .get_paged_credentials_by_rp_id(
                    rp_id_index = rpIdIndex,
                    limit = limit,
                    offset = offset,
                ).executeAsList()
        } finally {
            rpIdIndex.fill(0)
        }
    }

    /**
     * Retrieves a paginated list of all credentials.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries.get_paged_credentials(limit = limit, offset = offset).executeAsList()

    /**
     * Retrieves all credentials for a specific RP and user (sync).
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByRpIdAndUserId(
        rpId: RpId,
        userId: UserId,
    ): List<PasskeyCredentialEntity> {
        var rpIdIndex: ByteArray? = null
        var userIdIndex: ByteArray? = null
        try {
            rpIdIndex = metadataProtectionService.getRpIdIndex(rpId.value)
            userIdIndex = metadataProtectionService.getUserIdIndex(userId.value)
            return database.passkeyCredentialQueries
                .select_by_rp_id_and_user_id(rp_id_index = rpIdIndex, user_id_index = userIdIndex)
                .executeAsList()
        } finally {
            rpIdIndex?.fill(0)
            userIdIndex?.fill(0)
        }
    }

    /**
     * Retrieves all credentials for a specific user.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getCredentialsByUserId(userId: UserId): Flow<List<PasskeyCredentialEntity>> =
        flow {
            val userIdIndex = metadataProtectionService.getUserIdIndex(userId.value)
            try {
                emitAll(
                    database.passkeyCredentialQueries
                        .select_by_user_id(user_id_index = userIdIndex)
                        .asFlow()
                        .map { query -> query.executeAsList() },
                )
            } finally {
                userIdIndex.fill(0)
            }
        }

    /**
     * Retrieves all credentials from the database.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAllCredentials(): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .select_all()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun updateCredential(credential: PasskeyCredential) {
        var rpNameBytes: ByteArray? = null
        var userDisplayNameBytes: ByteArray? = null
        var userNameBytes: ByteArray? = null
        try {
            rpNameBytes = credential.rpId.toUtf8Bytes()
            userDisplayNameBytes = credential.userDisplayName.toUtf8Bytes()
            userNameBytes = credential.userName.toUtf8Bytes()
            database.passkeyCredentialQueries.update(
                last_used_at = credential.lastUsedAt.toEpochMilliseconds(),
                label = credential.label,
                // fallback
                rp_name = requireNotNull(rpNameBytes),
                sign_count = credential.signCount,
                user_display_name = requireNotNull(userDisplayNameBytes),
                user_name = requireNotNull(userNameBytes),
                id = credential.id.encoded,
            )
        } finally {
            rpNameBytes?.fill(0)
            userDisplayNameBytes?.fill(0)
            userNameBytes?.fill(0)
        }
    }

    /**
     * Updates the custom label for a credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ) {
        database.passkeyCredentialQueries.update_label(
            label = label,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the sign count for a credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun updateSignCount(
        credentialId: CredentialId,
        signCount: Long,
    ) {
        database.passkeyCredentialQueries.update_sign_count(
            sign_count = signCount,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the last used timestamp for a credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun updateLastUsedAt(credentialId: CredentialId) {
        database.passkeyCredentialQueries.update_last_used_at(
            last_used_at = timeProvider.now().toEpochMilliseconds(),
            id = credentialId.encoded,
        )
    }

    /**
     * Retrieves the sign count for a specific credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSignCount(credentialId: CredentialId): Long =
        database.passkeyCredentialQueries
            .get_sign_count(id = credentialId.encoded)
            .executeAsOneOrNull() ?: 0L

    /**
     * Deletes a credential by its ID.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun deleteCredential(credentialId: CredentialId) {
        database.passkeyCredentialQueries.delete_by_id(id = credentialId.encoded)
    }
}
