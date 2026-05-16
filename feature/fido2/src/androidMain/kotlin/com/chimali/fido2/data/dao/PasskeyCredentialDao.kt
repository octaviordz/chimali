package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow
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
) {
    /**
     * Inserts a new credential into the database.
     */
    fun insertCredential(credential: PasskeyCredential) {
        database.passkeyCredentialQueries.insert(
            id = credential.id.encoded,
            created_at = credential.createdAt.toEpochMilliseconds(),
            last_used_at = credential.lastUsedAt.toEpochMilliseconds(),
            aaguid =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(credential.aaguid),
            cose_algorithm = credential.coseAlgorithm.toLong(),
            credential_id = credential.id.encoded,
            cred_protect_policy = credential.credProtectPolicy.toLong(),
            label = credential.label,
            private_key_alias = credential.privateKeyAlias,
            public_key =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(credential.publicKey.encoded),
            rp_id = credential.rpId.value,
            // fallback
            rp_name = credential.rpId.value,
            sign_count = credential.signCount,
            user_display_name = credential.userDisplayName,
            user_id = credential.userId.value,
            user_name = credential.userName,
        )
    }

    /**
     * Retrieves a credential by its ID.
     */
    fun getCredentialById(credentialId: CredentialId): PasskeyCredentialEntity? =
        database.passkeyCredentialQueries
            .select_by_id(id = credentialId.encoded)
            .executeAsOneOrNull()

    /**
     * Retrieves all credentials for a specific relying party.
     */
    fun getCredentialsByRpId(rpId: RpId): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .select_by_rp_id(rp_id = rpId.value)
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves a paginated list of credentials for a specific relying party.
     */
    fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries
            .get_paged_credentials_by_rp_id(
                rp_id = rpId.value,
                limit = limit,
                offset = offset,
            ).executeAsList()

    /**
     * Retrieves a paginated list of all credentials.
     */
    fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries.get_paged_credentials(limit = limit, offset = offset).executeAsList()

    /**
     * Retrieves all credentials for a specific RP and user (sync).
     */
    fun getCredentialsByRpIdAndUserId(
        rpId: RpId,
        userId: UserId,
    ): List<PasskeyCredentialEntity> =
        database.passkeyCredentialQueries
            .select_by_rp_id_and_user_id(rp_id = rpId.value, user_id = userId.value)
            .executeAsList()

    /**
     * Retrieves all credentials for a specific user.
     */
    fun getCredentialsByUserId(userId: UserId): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .select_by_user_id(user_id = userId.value)
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Retrieves all credentials from the database.
     */
    fun getAllCredentials(): Flow<List<PasskeyCredentialEntity>> =
        database.passkeyCredentialQueries
            .select_all()
            .asFlow()
            .map { query -> query.executeAsList() }

    /**
     * Updates an existing credential.
     */
    fun updateCredential(credential: PasskeyCredential) {
        database.passkeyCredentialQueries.update(
            last_used_at = credential.lastUsedAt.toEpochMilliseconds(),
            label = credential.label,
            // fallback
            rp_name = credential.rpId.value,
            sign_count = credential.signCount,
            user_display_name = credential.userDisplayName,
            user_name = credential.userName,
            id = credential.id.encoded,
        )
    }

    /**
     * Updates the custom label for a credential.
     */
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
     * Updates the public key for a credential.
     */
    fun updatePublicKey(
        credentialId: CredentialId,
        publicKey: String,
    ) {
        database.passkeyCredentialQueries.update_public_key(
            public_key = publicKey,
            id = credentialId.encoded,
        )
    }

    /**
     * Updates the sign count for a credential.
     */
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
    fun updateLastUsedAt(credentialId: CredentialId) {
        database.passkeyCredentialQueries.update_last_used_at(
            last_used_at = timeProvider.now().toEpochMilliseconds(),
            id = credentialId.encoded,
        )
    }

    /**
     * Retrieves the sign count for a specific credential.
     */
    fun getSignCount(credentialId: CredentialId): Long =
        database.passkeyCredentialQueries
            .get_sign_count(id = credentialId.encoded)
            .executeAsOneOrNull() ?: 0L

    /**
     * Deletes a credential by its ID.
     */
    fun deleteCredential(credentialId: CredentialId) {
        database.passkeyCredentialQueries.delete_by_id(id = credentialId.encoded)
    }
}
