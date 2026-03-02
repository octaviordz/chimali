package com.chimali.fido2.domain.model

/**
 * Domain model representing a PublicKeyCredentialDescriptor.
 * This describes existing credentials that can be excluded from creation.
 */
data class PublicKeyCredentialDescriptor(
    val type: PublicKeyCredentialType,
    val id: ByteArray,
    val transports: List<AuthenticatorTransport>?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the PublicKeyCredentialDescriptor according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(id.isNotEmpty()) { "Credential ID cannot be empty" }
        require(id.size <= 1023) { "Credential ID cannot exceed 1023 bytes" }
        require(type != PublicKeyCredentialType.UNKNOWN) { "Credential type must be specified" }
        
        // Validate transports if present
        transports?.let { transportList ->
            require(transportList.isNotEmpty()) { "Transports list cannot be empty if provided" }
            require(transportList.size <= 5) { "Transports list cannot exceed 5 items" }
            transportList.forEach { transport ->
                require(transport != AuthenticatorTransport.UNKNOWN) { 
                    "Transport cannot be UNKNOWN" 
                }
            }
        }
    }
    
    /**
     * Returns the credential ID as a base64 URL-safe string.
     */
    fun getIdBase64Url(): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(id)
    }
    
    /**
     * Returns the credential ID as a hex string.
     */
    fun getIdHex(): String {
        return id.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Checks if this descriptor supports specific transport.
     */
    fun supportsTransport(transport: AuthenticatorTransport): Boolean {
        return transports?.contains(transport) ?: false
    }
    
    /**
     * Returns a list of supported transports.
     */
    fun getSupportedTransports(): List<AuthenticatorTransport> {
        return transports ?: emptyList()
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        const val MAX_TRANSPORTS_SIZE = 5
        
        /**
         * Creates a new PublicKeyCredentialDescriptor with validation.
         */
        fun create(
            type: PublicKeyCredentialType = PublicKeyCredentialType.PUBLIC_KEY,
            id: ByteArray,
            transports: List<AuthenticatorTransport>? = null
        ): PublicKeyCredentialDescriptor {
            return PublicKeyCredentialDescriptor(
                type = type,
                id = id,
                transports = transports
            )
        }
        
        /**
         * Creates a descriptor from base64 credential ID.
         */
        fun fromBase64Id(
            type: PublicKeyCredentialType = PublicKeyCredentialType.PUBLIC_KEY,
            idBase64: String,
            transports: List<AuthenticatorTransport>? = null
        ): PublicKeyCredentialDescriptor {
            val id = try {
                java.util.Base64.getUrlDecoder().decode(idBase64)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 credential ID", e)
            }
            
            return create(type, id, transports)
        }
    }
}

/**
 * Enumeration of authenticator transport types.
 */
enum class AuthenticatorTransport {
    USB,
    NFC,
    BLE,
    INTERNAL,
    HYBRID,
    UNKNOWN
}
