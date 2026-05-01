package com.chimali.fido2.data.repository

import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
@Suppress("ForbiddenComment")
class RelyingPartyRepositoryImpl : RelyingPartyRepository {
    override suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit> {
        // TODO: Implement database save logic
        return Result.success(Unit)
    }

    override suspend fun getRelyingPartyById(rpId: RpId): RelyingParty? {
        // TODO: Implement database get logic
        return null
    }

    override suspend fun getAllRelyingParties(): Flow<List<RelyingParty>> {
        // TODO: Implement database query logic
        return flowOf(emptyList())
    }

    override suspend fun deleteRelyingParty(rpId: RpId): Result<Unit> {
        // TODO: Implement database delete logic
        return Result.success(Unit)
    }

    override suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ): Result<Unit> {
        // TODO: Implement database update logic
        return Result.success(Unit)
    }
}
