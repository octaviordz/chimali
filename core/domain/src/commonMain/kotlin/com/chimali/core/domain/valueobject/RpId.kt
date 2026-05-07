package com.chimali.core.domain.valueobject

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Represents the Relying Party ID (RP ID), uniquely identifying the domain
 * that requested the Passkey operation.
 */
@Serializable
@JvmInline
value class RpId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RpId cannot be blank" }
    }
}
