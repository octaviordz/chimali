package com.chimali.fido2.domain.model

/**
 * Domain model representing a PublicKeyCredentialUserEntity.
 * This contains user information for FIDO2 credential operations.
 */
data class PublicKeyCredentialUserEntity(
    val id: ByteArray,
    val name: String,
    val displayName: String,
    val icon: String?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the PublicKeyCredentialUserEntity according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(id.isNotEmpty()) { "User ID cannot be empty" }
        require(id.size <= 64) { "User ID cannot exceed 64 bytes" }
        require(name.isNotBlank()) { "User name cannot be blank" }
        require(displayName.isNotBlank()) { "User display name cannot be blank" }
        
        // Validate formats
        require(name.length <= 64) { "User name cannot exceed 64 characters" }
        require(displayName.length <= 64) { "User display name cannot exceed 64 characters" }
        
        // Validate icon if present
        icon?.let { iconUrl ->
            require(iconUrl.isNotBlank()) { "Icon cannot be blank if provided" }
            require(iconUrl.length <= 128) { "Icon cannot exceed 128 characters" }
            require(iconUrl.startsWith("https://") || iconUrl.startsWith("http://")) { 
                "Icon must use HTTP or HTTPS protocol" 
            }
        }
    }
    
    /**
     * Returns the user ID as a base64 URL-safe string.
     */
    fun getIdBase64Url(): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(id)
    }
    
    /**
     * Returns a safe display name.
     */
    fun getSafeDisplayName(): String {
        return if (displayName.isNotBlank()) displayName else name
    }
    
    /**
     * Checks if this user has an icon.
     */
    fun hasIcon(): Boolean {
        return icon?.isNotBlank() ?: false
    }
    
    /**
     * Returns the user ID as a hex string.
     */
    fun getIdHex(): String {
        return id.joinToString("") { "%02x".format(it) }
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_USER_ID_LENGTH = 64
        const val MAX_NAME_LENGTH = 64
        const val MAX_DISPLAY_NAME_LENGTH = 64
        const val MAX_ICON_LENGTH = 128
        
        /**
         * Creates a new PublicKeyCredentialUserEntity with validation.
         */
        fun create(
            id: ByteArray,
            name: String,
            displayName: String,
            icon: String? = null
        ): PublicKeyCredentialUserEntity {
            return PublicKeyCredentialUserEntity(
                id = id,
                name = name,
                displayName = displayName,
                icon = icon
            )
        }
        
        /**
         * Creates a new PublicKeyCredentialUserEntity from base64 user ID.
         */
        fun fromBase64Id(
            idBase64: String,
            name: String,
            displayName: String,
            icon: String? = null
        ): PublicKeyCredentialUserEntity {
            val id = try {
                java.util.Base64.getUrlDecoder().decode(idBase64)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 user ID", e)
            }
            
            return create(id, name, displayName, icon)
        }
    }
}
