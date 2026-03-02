package com.chimali.fido2.domain.repository

import com.chimali.fido2.domain.model.UserConsentRecord
import kotlinx.coroutines.flow.Flow

interface UserConsentRepository {
    suspend fun recordConsent(consentRecord: UserConsentRecord): Result<Unit>
    suspend fun getConsentRecordsByRpId(rpId: String): Flow<List<UserConsentRecord>>
    suspend fun getAllConsentRecords(): Flow<List<UserConsentRecord>>
    suspend fun deleteConsentRecordsByRpId(rpId: String): Result<Unit>
}
