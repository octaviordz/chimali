package com.chimali.fido2.domain.service

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow

interface Fido2Service {
    /** Full FIDO2 registration via CTAP2 MakeCredential options. Returns a [MakeCredentialResult]. */
    suspend fun makeCredential(options: MakeCredentialOptions): Outcome<MakeCredentialResult, DomainError>

    suspend fun registerNewCredential(
        rpId: RpId,
        userName: String,
        userDisplayName: String,
    ): Outcome<CredentialId, DomainError>

    fun getAllCredentials(): Flow<PasskeyCredential>

    suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError>

    suspend fun isSupported(): Boolean
}
