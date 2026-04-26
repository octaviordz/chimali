package com.chimali.fido2.domain.model

/**
 * Domain model representing a PublicKeyCredentialUserEntity.
 * This contains user information for FIDO2 credential operations.
 */
@Suppress("TooGenericExceptionCaught")
data class PublicKeyCredentialUserEntity(
    val id: ByteArray,
    val name: String,
    val displayName: String,
    val icon: String?,
) {
    init {
        validate()
    }

    /**
     * Validates the PublicKeyCredentialUserEntity according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(id.isNotEmpty()) { "User ID cannot be empty" }
        require(id.size <= MAX_USER_ID_LENGTH) { "User ID cannot exceed $MAX_USER_ID_LENGTH bytes" }
        require(name.isNotBlank()) { "User name cannot be blank" }
        require(displayName.isNotBlank()) { "User display name cannot be blank" }

        // Validate formats
        require(name.length <= MAX_NAME_LENGTH) { "User name cannot exceed $MAX_NAME_LENGTH characters" }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "User display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
        }

        // Validate icon if present
        icon?.let { iconUrl ->
            require(iconUrl.isNotBlank()) { "Icon cannot be blank if provided" }
            require(iconUrl.length <= MAX_ICON_LENGTH) { "Icon cannot exceed $MAX_ICON_LENGTH characters" }
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
        return displayName.ifBlank { name }
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PublicKeyCredentialUserEntity

        if (!id.contentEquals(other.id)) return false
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (icon != other.icon) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (icon?.hashCode() ?: 0)
        return result
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
            icon: String? = null,
        ): PublicKeyCredentialUserEntity {
            return PublicKeyCredentialUserEntity(
                id = id,
                name = name,
                displayName = displayName,
                icon = icon,
            )
        }

        /**
         * Creates a new PublicKeyCredentialUserEntity from base64 user ID.
         */
        fun fromBase64Id(
            idBase64: String,
            name: String,
            displayName: String,
            icon: String? = null,
        ): PublicKeyCredentialUserEntity {
            val id =
                try {
                    java.util.Base64.getUrlDecoder().decode(idBase64)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid base64 user ID", e)
                }

            return create(id, name, displayName, icon)
        }
    }
}
