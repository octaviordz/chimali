package com.chimali.fido2.domain.repository

import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import kotlinx.coroutines.flow.Flow

interface RelyingPartyRepository {
    suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit>

    suspend fun getRelyingPartyById(rpId: RpId): RelyingParty?

    fun getAllRelyingParties(): Flow<List<RelyingParty>>

    suspend fun deleteRelyingParty(rpId: RpId): Result<Unit>

    suspend fun updateCredentialCount(
        rpId: RpId,
        count: Int,
    ): Result<Unit>
}
