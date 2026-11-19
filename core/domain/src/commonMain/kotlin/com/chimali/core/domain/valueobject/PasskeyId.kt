package com.chimali.core.domain.valueobject

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Represents a Passkey ID, typically a Base64-URL encoded string.
 */
@Serializable
@JvmInline
value class PasskeyId(
    val value: String,
) {
    override fun toString(): String = "PasskeyId([redacted])"

    init {
        require(value.isNotBlank()) { "PasskeyId cannot be blank" }
    }
}
