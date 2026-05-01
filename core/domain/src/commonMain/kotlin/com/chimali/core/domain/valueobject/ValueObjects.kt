package com.chimali.core.domain.valueobject

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class EncryptedString(
    val value: String,
)

@Serializable
enum class CredentialCategory {
    SOCIAL,
    WORK,
    FINANCIAL,
    PERSONAL,
    DEVELOPMENT,
    SHOPPING,
    OTHER,
}

@Serializable
data class CredentialTag(
    val name: String,
)
