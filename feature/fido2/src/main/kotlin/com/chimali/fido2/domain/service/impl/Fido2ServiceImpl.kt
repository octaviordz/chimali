package com.chimali.fido2.domain.service.impl

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.Fido2Repository
import com.chimali.fido2.domain.service.Fido2Service
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Fido2ServiceImpl @Inject constructor(
    private val fido2Repository: Fido2Repository
) : Fido2Service {

    override suspend fun registerNewCredential(rpId: String, userName: String, userDisplayName: String): Result<String> {
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

    override suspend fun isSupported(): Boolean {
        // TODO: Check if FIDO2 is supported on this device
        return true
    }
}
