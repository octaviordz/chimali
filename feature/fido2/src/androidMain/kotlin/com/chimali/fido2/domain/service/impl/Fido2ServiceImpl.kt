package com.chimali.fido2.domain.service.impl

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.Fido2Repository
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

/**
 * T070 — Wires [Fido2Service] to [RegisterCredentialUseCase] so the presentation layer
 * has a single entry-point for all FIDO2 operations.
 */
@Single
class Fido2ServiceImpl(
    private val fido2Repository: Fido2Repository,
    private val registerCredentialUseCase: RegisterCredentialUseCase,
) : Fido2Service {
    /**
     * T070 — Full FIDO2 registration via CTAP2 MakeCredential.
     *
     * Delegates to [RegisterCredentialUseCase] which handles:
     * - Options validation
     * - User verification (biometric / PIN)
     * - Consent recording (T075)
     * - Key-pair generation & KeyStore storage
     * - AttestationObject construction
     * - Credential persistence
     */
    override suspend fun makeCredential(options: MakeCredentialOptions): Outcome<MakeCredentialResult, DomainError> =
        registerCredentialUseCase(options)

    override suspend fun registerNewCredential(
        rpId: RpId,
        userName: String,
        userDisplayName: String,
    ): Outcome<CredentialId, DomainError> = fido2Repository.registerCredential(rpId, userName, userDisplayName)

    override suspend fun authenticateWithCredential(rpId: RpId): Outcome<CredentialId, DomainError> =
        fido2Repository.authenticateCredential(rpId)

    override fun getAllCredentials(): Flow<PasskeyCredential> = fido2Repository.getAllCredentials()

    override suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError> =
        fido2Repository.deleteCredential(credentialId)

    override suspend fun isSupported(): Boolean = true
}
