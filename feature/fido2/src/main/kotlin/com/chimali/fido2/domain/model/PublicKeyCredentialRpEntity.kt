package com.chimali.fido2.domain.model

/**
 * Domain model representing a PublicKeyCredentialRpEntity.
 * This contains information about the relying party requesting credential creation.
 */
data class PublicKeyCredentialRpEntity(
    val id: String,
    val name: String,
    val icon: String?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the PublicKeyCredentialRpEntity according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(id.isNotBlank()) { "RP ID cannot be blank" }
        require(name.isNotBlank()) { "RP name cannot be blank" }
        
        // Validate formats
        require(RelyingParty.isValidRpId(id)) {
            "RP ID must be a valid domain or HTTPS origin: $id" 
        }
        require(name.length <= 64) { "RP name cannot exceed 64 characters" }
        
        // Validate icon if present
        icon?.let { iconUrl ->
            require(iconUrl.isNotBlank()) { "Icon cannot be blank if provided" }
            require(iconUrl.length <= 128) { "Icon cannot exceed 128 characters" }
        }
    }
    
    /**
     * Returns the domain from the RP ID.
     */
    fun getDomain(): String {
        return try {
            java.net.URI.create(id).host ?: id
        } catch (e: Exception) {
            id
        }
    }
    
    /**
     * Returns a safe name for display.
     */
    fun getSafeName(): String {
        return if (name.isNotBlank()) name else getDomain()
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_NAME_LENGTH = 64
        const val MAX_ICON_LENGTH = 128
        
        /**
         * Creates a new PublicKeyCredentialRpEntity with validation.
         */
        fun create(
            id: String,
            name: String,
            icon: String? = null
        ): PublicKeyCredentialRpEntity {
            return PublicKeyCredentialRpEntity(
                id = id,
                name = name,
                icon = icon
            )
        }
    }
}
