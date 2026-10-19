package com.chimali.fido2.data.repository

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.security.api.MetadataLookupKeyUnavailableException
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/** Strictly enumerates credential owners for destructive repository batches. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class DestructiveCredentialBatchReader(
    private val dao: PasskeyCredentialDao,
    private val publicKeyDecoder: PublicKeyDecoder,
    private val timeProvider: TimeProvider,
    private val metadataProtectionService: CredentialMetadataProtectionService,
    private val onCredentialHydrated: (PasskeyCredential) -> Unit,
) {
    private companion object {
        private const val ENUMERATION_FAILED = "Credential storage operation failed"
        private const val LOOKUP_KEY_UNAVAILABLE = "Credential storage is temporarily unavailable"
        private const val INVALID_RECORDS = "Credential storage contains invalid records"
    }

    /** Returns every credential selected by the scope; callers own and erase successful results. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun read(
        rpId: RpId? = null,
        expiredMaxAgeDays: Long? = null,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val rows =
            try {
                val source = rpId?.let { dao.getCredentialsByRpText(it.value) } ?: dao.getAllCredentials()
                source.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: android.database.SQLException) {
                Logger.e { "CredentialRepository: Failed to enumerate destructive credential batch" }
                return Outcome.Error(DomainError.DatabaseError(ENUMERATION_FAILED, failure))
            } catch (failure: java.sql.SQLException) {
                Logger.e { "CredentialRepository: Failed to enumerate destructive credential batch" }
                return Outcome.Error(DomainError.DatabaseError(ENUMERATION_FAILED, failure))
            } catch (_: MetadataLookupKeyUnavailableException) {
                Logger.e { "CredentialRepository: Destructive credential lookup key unavailable" }
                return Outcome.Error(DomainError.StorageError(LOOKUP_KEY_UNAVAILABLE))
            }

        val credentials = mutableListOf<PasskeyCredential>()
        var transferred = false
        try {
            val cutoff = expiredMaxAgeDays?.let { timeProvider.now() - it.days }
            for (row in rows) {
                if (cutoff != null && Instant.fromEpochMilliseconds(row.created_at) >= cutoff) continue
                when (val mapped = row.toDomainModel(publicKeyDecoder, metadataProtectionService)) {
                    is Outcome.Success -> {
                        credentials += mapped.data
                        onCredentialHydrated(mapped.data)
                    }
                    is Outcome.Error -> {
                        Logger.e { "CredentialRepository: Invalid record in destructive credential batch" }
                        return Outcome.Error(DomainError.ValidationError(INVALID_RECORDS, mapped.error.cause))
                    }
                }
            }
            transferred = true
            return Outcome.Success(credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: android.database.SQLException) {
            Logger.e { "CredentialRepository: Failed to hydrate destructive credential batch" }
            return Outcome.Error(DomainError.DatabaseError(ENUMERATION_FAILED, failure))
        } catch (failure: java.sql.SQLException) {
            Logger.e { "CredentialRepository: Failed to hydrate destructive credential batch" }
            return Outcome.Error(DomainError.DatabaseError(ENUMERATION_FAILED, failure))
        } finally {
            rows.forEach(PasskeyCredentialEntity::eraseSensitiveMetadata)
            if (!transferred) credentials.forEach(PasskeyCredential::eraseSensitiveText)
        }
    }
}
