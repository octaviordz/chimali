package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialCategory
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.EncryptedString
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val id: CredentialId,
    val title: String,
    val username: String,
    val password: EncryptedString,
    val url: String?,
    val notes: String?,
    val category: CredentialCategory,
    val tags: List<String>,
    val createdAt: Instant,
    val lastModified: Instant,
    val lastUsed: Instant?,
    val isFavorite: Boolean,
) : DomainModel
