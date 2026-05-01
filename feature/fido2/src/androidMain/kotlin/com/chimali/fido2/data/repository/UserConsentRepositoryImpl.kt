package com.chimali.fido2.data.repository

import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.UserConsentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
@Suppress("ForbiddenComment")
class UserConsentRepositoryImpl : UserConsentRepository {
    override suspend fun recordConsent(consentRecord: UserConsentRecord): Result<Unit> {
        // TODO: Implement database save logic
        return Result.success(Unit)
    }

    override suspend fun getConsentRecordsByRpId(rpId: RpId): Flow<List<UserConsentRecord>> {
        // TODO: Implement database query logic
        return flowOf(emptyList())
    }

    override suspend fun getAllConsentRecords(): Flow<List<UserConsentRecord>> {
        // TODO: Implement database query logic
        return flowOf(emptyList())
    }

    override suspend fun deleteConsentRecordsByRpId(rpId: RpId): Result<Unit> {
        // TODO: Implement database delete logic
        return Result.success(Unit)
    }
}
