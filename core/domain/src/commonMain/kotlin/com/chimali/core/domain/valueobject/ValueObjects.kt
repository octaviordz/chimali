package com.chimali.core.domain.valueobject

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CredentialId(val value: String)

@Serializable
@JvmInline
value class PasskeyId(val value: String)

@Serializable
@JvmInline
value class EncryptedString(val value: String)

@Serializable
enum class CredentialCategory {
    SOCIAL, WORK, FINANCIAL, PERSONAL, DEVELOPMENT, SHOPPING, OTHER
}

@Serializable
data class CredentialTag(val name: String)
