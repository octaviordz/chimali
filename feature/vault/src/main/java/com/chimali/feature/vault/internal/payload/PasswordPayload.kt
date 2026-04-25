package com.chimali.feature.vault.internal.payload


data class PasswordPayload(
    val title: String,
    val username: CharArray,
    val password: CharArray,
    val uri: String,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null
) {
    fun clearMemory() {
        username.fill('0')
        password.fill('0')
        notes?.fill('0')
        customFields?.forEach { it.clearMemory() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordPayload

        if (title != other.title) return false
        if (!username.contentEquals(other.username)) return false
        if (!password.contentEquals(other.password)) return false
        if (uri != other.uri) return false
        
        if (notes != null) {
            if (other.notes == null) return false
            if (!notes.contentEquals(other.notes)) return false
        } else if (other.notes != null) return false

        if (customFields != other.customFields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + (notes?.contentHashCode() ?: 0)
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
