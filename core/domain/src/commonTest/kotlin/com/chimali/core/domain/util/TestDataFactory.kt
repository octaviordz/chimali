package com.chimali.core.domain.util

import com.chimali.core.domain.model.Credential
import com.chimali.core.domain.valueobject.CredentialCategory
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.EncryptedString
import kotlinx.datetime.Clock

object TestDataFactory {
    fun createCredential(
        id: String = "test-id",
        title: String = "Test Credential",
        category: CredentialCategory = CredentialCategory.PERSONAL
    ) = Credential(
        id = CredentialId(id),
        title = title,
        username = "testuser",
        password = EncryptedString("encrypted-password"),
        url = "https://example.com",
        notes = "Test notes",
        category = category,
        tags = listOf("test"),
        createdAt = Clock.System.now(),
        lastModified = Clock.System.now(),
        lastUsed = null,
        isFavorite = false
    )
}
