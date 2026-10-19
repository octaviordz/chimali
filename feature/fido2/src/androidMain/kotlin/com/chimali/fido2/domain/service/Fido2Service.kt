package com.chimali.fido2.domain.service

import com.chimali.core.common.annotation.Req
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

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun makeCredential(
        options: MakeCredentialOptions,
        userVerificationPerformed: Boolean = false,
    ): Outcome<MakeCredentialResult, DomainError>

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun registerNewCredential(
        rpId: RpId,
        userName: String,
        userDisplayName: String,
    ): Outcome<CredentialId, DomainError>

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAllCredentials(): Flow<PasskeyCredential>

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError>

    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun isSupported(): Boolean
}
