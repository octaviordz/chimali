package com.chimali.core.domain.repository

import com.chimali.core.domain.exception.DomainException
import com.chimali.core.domain.model.Credential
import com.chimali.core.domain.valueobject.CredentialCategory
import com.chimali.core.domain.valueobject.CredentialId

class FakeCredentialRepository : CredentialRepository {
    private val credentials = mutableMapOf<CredentialId, Credential>()

    override suspend fun getAllCredentials(): Result<List<Credential>> {
        return Result.success(credentials.values.toList())
    }

    override suspend fun getCredentialById(id: CredentialId): Result<Credential> {
        val credential = credentials[id]
        return if (credential != null) {
            Result.success(credential)
        } else {
            Result.failure(DomainException.CredentialNotFound)
        }
    }

    override suspend fun searchCredentials(query: String): Result<List<Credential>> {
        val filtered = credentials.values.filter {
            it.title.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true)
        }
        return Result.success(filtered)
    }

    override suspend fun saveCredential(credential: Credential): Result<CredentialId> {
        credentials[credential.id] = credential
        return Result.success(credential.id)
    }

    override suspend fun updateCredential(credential: Credential): Result<Unit> {
        if (!credentials.containsKey(credential.id)) {
            return Result.failure(DomainException.CredentialNotFound)
        }
        credentials[credential.id] = credential
        return Result.success(Unit)
    }

    override suspend fun deleteCredential(id: CredentialId): Result<Unit> {
        return if (credentials.remove(id) != null) {
            Result.success(Unit)
        } else {
            Result.failure(DomainException.CredentialNotFound)
        }
    }

    override suspend fun getCredentialsByCategory(category: CredentialCategory): Result<List<Credential>> {
        val filtered = credentials.values.filter { it.category == category }
        return Result.success(filtered)
    }

    override suspend fun getFavoriteCredentials(): Result<List<Credential>> {
        val filtered = credentials.values.filter { it.isFavorite }
        return Result.success(filtered)
    }

    override suspend fun updateLastUsed(id: CredentialId): Result<Unit> {
        val credential = credentials[id] ?: return Result.failure(DomainException.CredentialNotFound)
        // In a real implementation this would update the lastUsed timestamp
        return Result.success(Unit)
    }
}
