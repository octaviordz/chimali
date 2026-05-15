package com.chimali.fido2.data.repository

import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.UserConsentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
class UserConsentRepositoryImpl : UserConsentRepository {
    override suspend fun recordConsent(consentRecord: UserConsentRecord): Result<Unit> {
        // DEFERRED(040): Consent persistence — pending schema design
        return Result.success(Unit)
    }

    override fun getConsentRecordsByRpId(rpId: RpId): Flow<List<UserConsentRecord>> {
        // DEFERRED(040): Consent persistence — pending schema design
        return flowOf(emptyList())
    }

    override fun getAllConsentRecords(): Flow<List<UserConsentRecord>> {
        // DEFERRED(040): Consent persistence — pending schema design
        return flowOf(emptyList())
    }

    override suspend fun deleteConsentRecordsByRpId(rpId: RpId): Result<Unit> {
        // DEFERRED(040): Consent persistence — pending schema design
        return Result.success(Unit)
    }
}
