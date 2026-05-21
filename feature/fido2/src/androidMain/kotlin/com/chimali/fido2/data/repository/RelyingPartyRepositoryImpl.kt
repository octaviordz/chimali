package com.chimali.fido2.data.repository

import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class RelyingPartyRepositoryImpl(
    private val relyingPartyDao: RelyingPartyDao,
) : RelyingPartyRepository {
    override suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit> =
        runCatching {
            relyingPartyDao.insertOrUpdateRelyingParty(relyingParty)
        }

    override suspend fun getRelyingPartyById(rpId: RpId): RelyingParty? =
        relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()

    override fun getAllRelyingParties(): Flow<List<RelyingParty>> =
        relyingPartyDao.getAllRelyingParties().map { list ->
            list.map { it.toDomainModel() }
        }

    override suspend fun deleteRelyingParty(rpId: RpId): Result<Unit> =
        runCatching {
            relyingPartyDao.deleteRelyingParty(rpId)
        }

    override suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ): Result<Unit> =
        runCatching {
            relyingPartyDao.updateCredentialCount(rpId, count)
        }
}
