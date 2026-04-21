package com.chimali.core.domain.repository

import com.chimali.core.domain.model.Passkey
import com.chimali.core.domain.valueobject.PasskeyId

interface PasskeyRepository {
    suspend fun getAllPasskeys(): Result<List<Passkey>>
    suspend fun getPasskeyById(id: PasskeyId): Result<Passkey>
    suspend fun getPasskeysByRelyingParty(rp: String): Result<List<Passkey>>
    suspend fun createPasskey(passkey: Passkey): Result<PasskeyId>
    suspend fun updatePasskey(passkey: Passkey): Result<Unit>
    suspend fun deletePasskey(id: PasskeyId): Result<Unit>
    suspend fun updateSignatureCounter(id: PasskeyId, counter: Long): Result<Unit>
}
