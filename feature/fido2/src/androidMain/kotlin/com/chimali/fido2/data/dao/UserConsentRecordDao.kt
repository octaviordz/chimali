package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.User_consent_record as UserConsentRecordEntity
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.toOwnedUtf8
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Data Access Object for UserConsentRecord entities using SQLDelight.
 * Provides database operations for user consent tracking.
 */
@Single
class UserConsentRecordDao(
    private val database: Fido2Database,
    private val metadataProtectionService: CredentialMetadataProtectionService,
) {
    /**
     * Inserts a consent record. The consent fields and RP value are borrowed; the generated RP lookup token is
     * operation-owned, passed to SQLDelight only for this call, and erased on success or database failure.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun insertConsent(consent: UserConsentRecord) {
        val rpBytes = consent.rpIdText.toOwnedUtf8()
        var rpIdIndex: ByteArray? = null
        try {
            rpIdIndex = metadataProtectionService.getRpIdIndex(consent.rpIdText)
            database.userConsentRecordQueries.insert(
                id = consent.id,
                created_at = consent.timestamp.toEpochMilliseconds(),
                timestamp = consent.timestamp.toEpochMilliseconds(),
                updated_at = consent.timestamp.toEpochMilliseconds(),
                biometric_used = if (consent.isBiometricUsed) 1L else 0L,
                credential_id = consent.credentialId?.encoded,
                device_id = consent.deviceId,
                ip_address = consent.ipAddress,
                operation_type = consent.operationType.name,
                pin_used = if (consent.isPinUsed) 1L else 0L,
                rp_id = rpBytes,
                user_agent = consent.userAgent,
                rp_id_index = rpIdIndex,
            )
        } finally {
            rpIdIndex?.fill(0)
            rpBytes.fill(0)
        }
    }

    /**
     * Returns recent consent rows, optionally filtered by RP ID. On filtered collection, the lookup token is
     * created for the collection lifetime, borrowed by SQLDelight, and erased on completion, failure, or
     * cancellation; the unfiltered branch creates no token.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getRecentConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            flow {
                val rpIdIndex = metadataProtectionService.getRpIdIndex(rpId.value)
                try {
                    emitAll(
                        database.userConsentRecordQueries
                            .select_recent_by_rp_id(
                                rp_id_index = rpIdIndex,
                                limit = limit.toLong(),
                            ).asFlow()
                            .map { query -> query.executeAsList() },
                    )
                } finally {
                    rpIdIndex.fill(0)
                }
            }
        } else {
            database.userConsentRecordQueries
                .select_recent(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }
}
