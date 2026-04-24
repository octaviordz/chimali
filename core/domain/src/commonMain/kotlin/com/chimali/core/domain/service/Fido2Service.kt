package com.chimali.core.domain.service

import com.chimali.core.domain.model.Passkey

data class CreatePasskeyRequest(
    val relyingParty: String,
    val username: String,
)

interface Fido2Service {
    suspend fun makeCredential(request: CreatePasskeyRequest): Result<Passkey>
}
