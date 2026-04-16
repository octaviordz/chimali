package com.chimali.fido2.domain.repository

import com.chimali.fido2.domain.model.RelyingParty
import kotlinx.coroutines.flow.Flow

interface RelyingPartyRepository {
    suspend fun saveRelyingParty(relyingParty: RelyingParty): Result<Unit>

    suspend fun getRelyingPartyById(rpId: String): RelyingParty?

    suspend fun getAllRelyingParties(): Flow<List<RelyingParty>>

    suspend fun deleteRelyingParty(rpId: String): Result<Unit>

    suspend fun updateCredentialCount(
        rpId: String,
        count: Int,
    ): Result<Unit>
}
