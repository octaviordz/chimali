package com.chimali.fido2.domain.repository

import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import kotlinx.coroutines.flow.Flow

interface UserConsentRepository {
    suspend fun recordConsent(consentRecord: UserConsentRecord): Result<Unit>

    fun getConsentRecordsByRpId(rpId: RpId): Flow<List<UserConsentRecord>>

    fun getAllConsentRecords(): Flow<List<UserConsentRecord>>

    suspend fun deleteConsentRecordsByRpId(rpId: RpId): Result<Unit>
}
