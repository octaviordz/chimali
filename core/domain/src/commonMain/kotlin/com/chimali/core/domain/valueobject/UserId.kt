package com.chimali.core.domain.valueobject

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Represents the User ID, uniquely identifying a user account associated with a Passkey.
 */
@OptIn(ExperimentalEncodingApi::class)
@Serializable
@JvmInline
value class UserId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
    }

    /**
     * Converts the Base64URL encoded string back to a ByteArray.
     */
    fun toByteArray(): ByteArray = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(value)

    companion object {
        /**
         * Creates a UserId from a ByteArray by encoding it as Base64URL.
         */
        fun fromByteArray(bytes: ByteArray): UserId =
            UserId(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes))
    }
}
