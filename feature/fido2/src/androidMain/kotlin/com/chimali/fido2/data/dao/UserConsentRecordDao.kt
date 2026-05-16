package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.User_consent_record as UserConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

/**
 * Data Access Object for UserConsentRecord entities using SQLDelight.
 * Provides database operations for user consent tracking.
 */
@Single
class UserConsentRecordDao(
    private val database: Fido2Database,
) {
    /**
     * Inserts a new consent record into the database.
     */
    fun insertConsent(consent: UserConsentRecord) {
        database.userConsentRecordQueries.insert(
            id = consent.id,
            timestamp = consent.timestamp.toEpochMilliseconds(),
            biometric_used = if (consent.isBiometricUsed) 1L else 0L,
            credential_id = consent.credentialId?.encoded,
            device_id = consent.deviceId,
            ip_address = consent.ipAddress,
            operation_type = consent.operationType.name,
            pin_used = if (consent.isPinUsed) 1L else 0L,
            rp_id = consent.rpId.value,
            user_agent = consent.userAgent,
        )
    }

    /**
     * Retrieves recent consent records, optionally filtered by RP ID.
     */
    fun getRecentConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<List<UserConsentRecordEntity>> =
        if (rpId != null) {
            database.userConsentRecordQueries
                .select_recent_by_rp_id(
                    rp_id = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .select_recent(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }
}
