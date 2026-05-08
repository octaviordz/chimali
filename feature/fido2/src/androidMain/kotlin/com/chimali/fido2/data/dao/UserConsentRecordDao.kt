package com.chimali.fido2.data.dao

import app.cash.sqldelight.coroutines.asFlow
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.UserConsentRecord as UserConsentRecordEntity
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
            isBiometricUsed = if (consent.isBiometricUsed) 1L else 0L,
            credentialId = consent.credentialId?.encoded,
            deviceId = consent.deviceId,
            ipAddress = consent.ipAddress,
            operationType = consent.operationType.name,
            isPinUsed = if (consent.isPinUsed) 1L else 0L,
            rpId = consent.rpId.value,
            userAgent = consent.userAgent,
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
                .selectRecentByRpId(
                    rpId = rpId.value,
                    limit = limit.toLong(),
                ).asFlow()
                .map { query -> query.executeAsList() }
        } else {
            database.userConsentRecordQueries
                .selectRecent(limit = limit.toLong())
                .asFlow()
                .map { query -> query.executeAsList() }
        }
}
