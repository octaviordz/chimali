package com.chimali.fido2.domain.model

/**
 * Domain model representing AuthenticatorSelectionCriteria.
 * This specifies criteria for selecting authenticators.
 */
data class AuthenticatorSelectionCriteria(
    val authenticatorAttachment: AuthenticatorAttachment?,
    val requireResidentKey: ResidentKeyRequirement?,
    val userVerification: UserVerificationRequirement?,
    val timeoutSeconds: Long?,
    val allowCredentials: List<PublicKeyCredentialDescriptor>?,
) {
    init {
        validate()
    }

    /**
     * Checks if resident keys are required.
     */
    fun requiresResidentKeys(): Boolean = requireResidentKey == ResidentKeyRequirement.REQUIRED

    /**
     * Checks if user verification is required.
     */
    fun requiresUserVerification(): Boolean = userVerification == UserVerificationRequirement.REQUIRED

    /**
     * Checks if cross-platform authenticators are allowed.
     */
    fun allowsCrossPlatform(): Boolean = authenticatorAttachment == AuthenticatorAttachment.CROSS_PLATFORM

    /**
     * Returns a description of the selection criteria.
     */
    fun getDescription(): String {
        val parts = mutableListOf<String>()

        authenticatorAttachment?.let { attachment ->
            parts.add("Attachment: ${attachment.name.lowercase()}")
        }

        requireResidentKey?.let { resident ->
            parts.add("Resident Key: ${resident.name.lowercase()}")
        }

        userVerification?.let { verification ->
            parts.add("User Verification: ${verification.name.lowercase()}")
        }

        timeoutSeconds?.let { timeout ->
            parts.add("Timeout: ${timeout}s")
        }

        allowCredentials?.let { credentials ->
            parts.add("Allow Credentials: ${credentials.size} items")
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else "No specific criteria"
    }

    companion object {
        private const val MAX_TIMEOUT_SECONDS = 300
        private const val DEFAULT_TIMEOUT_SECONDS = 300L
        private const val MAX_ALLOW_CREDENTIALS = 32

        /**
         * Creates a new AuthenticatorSelectionCriteria with validation.
         */
        fun create(
            authenticatorAttachment: AuthenticatorAttachment? = null,
            requireResidentKey: ResidentKeyRequirement? = null,
            userVerification: UserVerificationRequirement? = null,
            timeoutSeconds: Long? = null,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
        ): AuthenticatorSelectionCriteria =
            AuthenticatorSelectionCriteria(
                authenticatorAttachment = authenticatorAttachment,
                requireResidentKey = requireResidentKey,
                userVerification = userVerification,
                timeoutSeconds = timeoutSeconds,
                allowCredentials = allowCredentials,
            )
    }

    /**
     * Validates the AuthenticatorSelectionCriteria according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate timeout if present
        timeoutSeconds?.let { timeout ->
            require(timeout > 0) { "Timeout must be positive" }
            require(timeout <= MAX_TIMEOUT_SECONDS) { "Timeout cannot exceed 5 minutes (${MAX_TIMEOUT_SECONDS}s)" }
        }

        // Validate credential list if present
        allowCredentials?.let { allowList ->
            require(allowList.size <= MAX_ALLOW_CREDENTIALS) {
                "Allow credentials list cannot exceed $MAX_ALLOW_CREDENTIALS items"
            }
            allowList.forEach { descriptor ->
                descriptor.validate()
            }
        }
    }

    /**
     * Returns the safe timeout value.
     */
    fun getSafeTimeoutSeconds(): Long {
        return timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS // Default 5 minutes
    }
}

/**
 * Enumeration of authenticator attachment types.
 */
enum class AuthenticatorAttachment {
    PLATFORM,
    CROSS_PLATFORM,
    USB,
    NFC,
    BLE,
    INTERNAL,
}

/**
 * Enumeration of resident key requirements.
 */
enum class ResidentKeyRequirement {
    DISCOURAGED,
    PREFERRED,
    REQUIRED,
}

/**
 * Enumeration of user verification requirements.
 */
enum class UserVerificationRequirement {
    DISCOURAGED,
    PREFERRED,
    REQUIRED,
}
