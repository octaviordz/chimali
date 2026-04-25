package com.chimali.fido2.domain.service.impl

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
    override suspend fun makeCredential(options: MakeCredentialOptions): Result<MakeCredentialResult> {
        return registerCredentialUseCase(options)
    }

    override suspend fun registerNewCredential(
        rpId: String,
        userName: String,
        userDisplayName: String,
    ): Result<String> {
        return fido2Repository.registerCredential(rpId, userName, userDisplayName)
    }

    override suspend fun authenticateWithCredential(rpId: String): Result<String> {
        return fido2Repository.authenticateCredential(rpId)
    }

    override suspend fun getAllCredentials(): Flow<List<PasskeyCredential>> {
        return fido2Repository.getAllCredentials()
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> {
        return fido2Repository.deleteCredential(credentialId)
    }

    override suspend fun isSupported(): Boolean = true
}
