package com.chimali.fido2.domain.model

import java.util.Base64

/**
 * Domain model representing options for making a FIDO2 credential.
 * This contains all the parameters needed for the navigator.credentials.create() operation.
 */
data class MakeCredentialOptions(
    val rp: PublicKeyCredentialRpEntity,
    val user: PublicKeyCredentialUserEntity,
    val challenge: ByteArray,
    val pubKeyCredParams: PublicKeyCredentialParameters,
    val timeout: Long?,
    val allowCredentials: List<PublicKeyCredentialDescriptor>?,
    val excludeCredentials: List<PublicKeyCredentialDescriptor>?,
    val authenticatorSelection: AuthenticatorSelectionCriteria?,
    val attestation: AttestationConveyancePreference,
    val extensions: Map<String, Any>?,
    val selectedAlgId: Int,
) {
    init {
        validate()
    }

    /**
     * Validates the MakeCredentialOptions according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(challenge.isNotEmpty()) { "Challenge cannot be empty" }
        require(challenge.size <= MAX_CHALLENGE_SIZE) { "Challenge cannot exceed $MAX_CHALLENGE_SIZE bytes" }

        // Validate timeout
        timeout?.let { timeout ->
            require(timeout > 0) { "Timeout must be positive" }
            require(timeout <= MAX_TIMEOUT_MS) { "Timeout cannot exceed 5 minutes (300000ms)" }
        }

        // Validate credential lists
        allowCredentials?.let { allowList ->
            require(allowList.size <= MAX_CREDENTIAL_LIST_SIZE) { "Allow credentials list cannot exceed $MAX_CREDENTIAL_LIST_SIZE items" }
            allowList.forEach { descriptor ->
                descriptor.validate()
            }
        }

        excludeCredentials?.let { excludeList ->
            require(
                excludeList.size <= MAX_CREDENTIAL_LIST_SIZE,
            ) { "Exclude credentials list cannot exceed $MAX_CREDENTIAL_LIST_SIZE items" }
            excludeList.forEach { descriptor ->
                descriptor.validate()
            }
        }

        // Validate authenticator selection
        authenticatorSelection?.validate()

        // Validate RP and user entities
        rp.validate()
        user.validate()
        pubKeyCredParams.validate()

        // Validate extensions
        extensions?.let { ext ->
            require(ext.size <= MAX_EXTENSIONS_SIZE) { "Extensions map cannot exceed $MAX_EXTENSIONS_SIZE entries" }
            ext.keys.forEach { key ->
                require(key.isNotBlank()) { "Extension key cannot be blank" }
                require(key.length <= MAX_EXTENSION_KEY_LENGTH) { "Extension key cannot exceed $MAX_EXTENSION_KEY_LENGTH characters" }
            }
        }
    }

    /**
     * Returns the challenge as a base64 URL-safe string.
     */
    fun getChallengeBase64Url(): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
    }

    /**
     * Checks if this request allows resident keys.
     */
    fun allowsResidentKeys(): Boolean {
        return authenticatorSelection?.requireResidentKey == ResidentKeyRequirement.REQUIRED ||
            authenticatorSelection?.requireResidentKey == ResidentKeyRequirement.PREFERRED
    }

    /**
     * Checks if this request requires user verification.
     */
    fun requiresUserVerification(): Boolean {
        return authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED ||
            authenticatorSelection?.userVerification == UserVerificationRequirement.PREFERRED
    }

    /**
     * Checks if this request allows specific credential types.
     */
    fun allowsCredentialType(type: PublicKeyCredentialType): Boolean {
        return pubKeyCredParams.type == type
    }

    /**
     * Returns a safe timeout value.
     */
    fun getSafeTimeout(): Long {
        return timeout ?: DEFAULT_TIMEOUT_MS // Default 60 seconds
    }

    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_CHALLENGE_SIZE = 64
        const val MAX_CREDENTIAL_LIST_SIZE = 32
        const val MAX_EXTENSIONS_SIZE = 32
        const val MAX_EXTENSION_KEY_LENGTH = 32
        const val DEFAULT_TIMEOUT_MS = 60000L // 60 seconds
        private const val MAX_TIMEOUT_MS = 300000L

        /**
         * Creates a new MakeCredentialOptions with validation.
         */
        fun create(
            rp: PublicKeyCredentialRpEntity,
            user: PublicKeyCredentialUserEntity,
            challenge: ByteArray,
            pubKeyCredParams: PublicKeyCredentialParameters = PublicKeyCredentialParameters.create(),
            timeout: Long? = null,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
            excludeCredentials: List<PublicKeyCredentialDescriptor>? = null,
            authenticatorSelection: AuthenticatorSelectionCriteria? = null,
            attestation: AttestationConveyancePreference = AttestationConveyancePreference.NONE,
            extensions: Map<String, Any>? = null,
            selectedAlgId: Int,
        ): MakeCredentialOptions {
            return MakeCredentialOptions(
                rp = rp,
                user = user,
                challenge = challenge,
                pubKeyCredParams = pubKeyCredParams,
                timeout = timeout,
                allowCredentials = allowCredentials,
                excludeCredentials = excludeCredentials,
                authenticatorSelection = authenticatorSelection,
                attestation = attestation,
                extensions = extensions,
                selectedAlgId = selectedAlgId,
            )
        }

        /**
         * Creates MakeCredentialOptions from base64 challenge.
         */
        fun fromBase64Challenge(
            rp: PublicKeyCredentialRpEntity,
            user: PublicKeyCredentialUserEntity,
            challengeBase64: String,
            pubKeyCredParams: PublicKeyCredentialParameters = PublicKeyCredentialParameters.create(),
            timeout: Long? = null,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
            excludeCredentials: List<PublicKeyCredentialDescriptor>? = null,
            authenticatorSelection: AuthenticatorSelectionCriteria? = null,
            attestation: AttestationConveyancePreference = AttestationConveyancePreference.NONE,
            extensions: Map<String, Any>? = null,
            selectedAlgId: Int,
        ): MakeCredentialOptions {
            val challenge =
                try {
                    Base64.getUrlDecoder().decode(challengeBase64)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid base64 challenge", e)
                }

            return create(
                rp = rp,
                user = user,
                challenge = challenge,
                pubKeyCredParams = pubKeyCredParams,
                timeout = timeout,
                allowCredentials = allowCredentials,
                excludeCredentials = excludeCredentials,
                authenticatorSelection = authenticatorSelection,
                attestation = attestation,
                extensions = extensions,
                selectedAlgId = selectedAlgId,
            )
        }
    }
}
