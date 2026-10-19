package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req

/** The validated CTAP2 credential-protection policy values stored with passkey metadata. */
object CredentialProtectionPolicy {
    const val DEFAULT: Int = 1
    const val OPTIONAL_WITH_CREDENTIAL_ID_LIST: Int = 2
    const val USER_VERIFICATION_REQUIRED: Int = 3

    private const val EXTENSION_NAME = "credProtect"

    /** Returns whether a policy is in the supported CTAP2 domain. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isSupported(value: Int): Boolean = value in DEFAULT..USER_VERIFICATION_REQUIRED

    /** Validates an in-memory policy before it can influence credential authorization. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun requireSupported(value: Int): Int {
        require(isSupported(value)) { "Unsupported credential-protection policy" }
        return value
    }

    /** Validates a persisted SQL integer before narrowing or constructing an authorized model. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun fromPersisted(value: Long): Int {
        require(value in DEFAULT.toLong()..USER_VERIFICATION_REQUIRED.toLong()) {
            "Unsupported credential-protection policy"
        }
        return value.toInt()
    }

    /** Returns null only when the extension is absent; a present invalid value fails closed. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun fromExtensions(extensions: Map<*, *>?): Int? {
        if (extensions == null || !extensions.containsKey(EXTENSION_NAME)) return null
        val value = extensions[EXTENSION_NAME]
        val policy =
            when (value) {
                is Int -> value
                is Long -> {
                    require(value in DEFAULT.toLong()..USER_VERIFICATION_REQUIRED.toLong()) {
                        "Unsupported credential-protection policy"
                    }
                    value.toInt()
                }
                else -> throw IllegalArgumentException("Unsupported credential-protection policy")
            }
        return requireSupported(policy)
    }
}
