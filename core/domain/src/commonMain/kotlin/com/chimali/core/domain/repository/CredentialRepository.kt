package com.chimali.core.domain.repository

import com.chimali.core.domain.model.Credential
import com.chimali.core.domain.valueobject.CredentialCategory
import com.chimali.core.domain.valueobject.CredentialId

interface CredentialRepository {
    suspend fun getAllCredentials(): Result<List<Credential>>

    suspend fun getCredentialById(id: CredentialId): Result<Credential>

    suspend fun searchCredentials(query: String): Result<List<Credential>>

    suspend fun saveCredential(credential: Credential): Result<CredentialId>

    suspend fun updateCredential(credential: Credential): Result<Unit>

    suspend fun deleteCredential(id: CredentialId): Result<Unit>

    suspend fun getCredentialsByCategory(category: CredentialCategory): Result<List<Credential>>

    suspend fun getFavoriteCredentials(): Result<List<Credential>>

    suspend fun updateLastUsed(id: CredentialId): Result<Unit>
}
