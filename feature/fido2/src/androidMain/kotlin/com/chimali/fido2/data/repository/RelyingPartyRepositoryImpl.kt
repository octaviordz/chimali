package com.chimali.fido2.data.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.domain.repository.RelyingPartyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single

@Single
class RelyingPartyRepositoryImpl(
    private val relyingPartyDao: RelyingPartyDao,
) : RelyingPartyRepository {
    /** Retains newly hydrated owners for terminal-cleanup regression tests. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal var onRelyingPartyCreatedForTest: ((RelyingParty) -> Unit)? = null

    /** Persists the supplied relying-party metadata through the protected SQLDelight store. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit> =
        runCatching {
            relyingPartyDao.insertOrUpdateRelyingParty(relyingParty)
        }

    /** Reconstructs the matching relying-party model and transfers its mutable fields to the caller. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getRelyingPartyById(rpId: RpId): RelyingParty? =
        relyingPartyDao.getRelyingPartyById(rpId)?.toDomainModel()

    /**
     * Transfers complete owned batches only after downstream accepts the emission. Partial mapping failure,
     * emission failure, and cancellation erase every model still owned here and every source row.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun getAllRelyingParties(): Flow<List<RelyingParty>> =
        flow {
            relyingPartyDao.getAllRelyingParties().collect { rows ->
                val models = mutableListOf<RelyingParty>()
                var transferred = false
                try {
                    rows.forEach {
                        val model = it.toDomainModel()
                        models += model
                        onRelyingPartyCreatedForTest?.invoke(model)
                    }
                    emit(models)
                    transferred = true
                } finally {
                    rows.forEach { it.eraseSensitiveMetadata() }
                    if (!transferred) models.forEach(RelyingParty::eraseSensitiveText)
                }
            }
        }

    /** Deletes the relying-party record identified by [rpId]. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun deleteRelyingParty(rpId: RpId): Result<Unit> =
        runCatching {
            relyingPartyDao.deleteRelyingParty(rpId)
        }

    /** Updates the persisted credential count for the relying-party record. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ): Result<Unit> =
        runCatching {
            relyingPartyDao.updateCredentialCount(rpId, count)
        }
}
