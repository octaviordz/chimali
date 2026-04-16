package com.chimali.fido2.domain.model

/**
 * T078 — Domain model representing options for a FIDO2 GetAssertion (authentication) ceremony.
 *
 * Corresponds to the CTAP2 `authenticatorGetAssertion` request parameters (§6.2).
 */
data class GetAssertionOptions(
    /** Relying Party Identifier — SHA-256 of this is used as rpIdHash in authData. */
    val rpId: String,
    /** 16-32 byte random challenge from the RP. Must be unique per ceremony. */
    val clientDataHash: ByteArray,
    /**
     * Optional allow-list: only credentials with matching IDs may be used.
     * If empty/null, any credential for this RP is acceptable (discoverable credential flow).
     */
    val allowCredentials: List<PublicKeyCredentialDescriptor>?,
    /** CTAP2 §6.2 param 0x04 — user verification requirement. */
    val userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
    /** CTAP2 extensions map (param 0x06), pass-through opaque blob. */
    val extensions: Map<String, Any>? = null,
    /** Timeout in milliseconds — null means use device default (60 s). */
    val timeout: Long? = null,
) {
    init {
        validate()
    }

    private fun validate() {
        require(rpId.isNotBlank()) { "RP ID cannot be blank" }
        require(clientDataHash.size == CLIENT_DATA_HASH_SIZE) {
            "clientDataHash must be 32 bytes (SHA-256), got ${clientDataHash.size}"
        }
        allowCredentials?.let { list ->
            require(list.size <= MAX_ALLOW_CREDENTIALS) { "allowCredentials cannot exceed 32 items" }
            // Individual descriptors are validated upon construction (init)
        }
        timeout?.let { require(it in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) { "Timeout must be 1–300000 ms" } }
    }

    fun getSafeTimeout(): Long = timeout ?: DEFAULT_TIMEOUT_MS

    /** True when no allowCredentials list is provided — any resident credential is valid. */
    fun isDiscoverableFlow(): Boolean = allowCredentials.isNullOrEmpty()

    companion object {
        private const val CLIENT_DATA_HASH_SIZE = 32
        private const val MAX_ALLOW_CREDENTIALS = 32
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val MIN_TIMEOUT_MS = 1L
        private const val MAX_TIMEOUT_MS = 300_000L

        /**
         * Quick factory for tests or internal use when clientDataHash is already computed.
         */
        fun create(
            rpId: String,
            clientDataHash: ByteArray,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
            userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
            extensions: Map<String, Any>? = null,
            timeout: Long? = null,
        ) = GetAssertionOptions(
            rpId = rpId,
            clientDataHash = clientDataHash,
            allowCredentials = allowCredentials,
            userVerification = userVerification,
            extensions = extensions,
            timeout = timeout,
        )
    }

    // ByteArray equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetAssertionOptions) return false
        return rpId == other.rpId &&
            clientDataHash.contentEquals(other.clientDataHash) &&
            allowCredentials == other.allowCredentials &&
            userVerification == other.userVerification
    }

    override fun hashCode(): Int {
        var result = rpId.hashCode()
        result = 31 * result + clientDataHash.contentHashCode()
        result = 31 * result + (allowCredentials?.hashCode() ?: 0)
        result = 31 * result + userVerification.hashCode()
        return result
    }
}
