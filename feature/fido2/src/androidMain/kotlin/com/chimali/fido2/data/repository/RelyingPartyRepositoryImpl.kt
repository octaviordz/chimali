package com.chimali.fido2.data.repository

import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
class RelyingPartyRepositoryImpl : RelyingPartyRepository {
    override suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit> {
        // DEFERRED(040): RP persistence — pending schema design
        return Result.success(Unit)
    }

    override suspend fun getRelyingPartyById(rpId: RpId): RelyingParty? {
        // DEFERRED(040): RP persistence — pending schema design
        return null
    }

    override fun getAllRelyingParties(): Flow<List<RelyingParty>> {
        // DEFERRED(040): RP persistence — pending schema design
        return flowOf(emptyList())
    }

    override suspend fun deleteRelyingParty(rpId: RpId): Result<Unit> {
        // DEFERRED(040): RP persistence — pending schema design
        return Result.success(Unit)
    }

    override suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ): Result<Unit> {
        // DEFERRED(040): RP persistence — pending schema design
        return Result.success(Unit)
    }
}
