package com.chimali.feature.vault.internal.payload

/** FR-VAULT-026: owns its arrays; copies transfer independent ownership. */
data class PasswordPayload(
    val title: CharArray,
    val username: CharArray,
    val password: CharArray,
    val uri: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) : SensitivePayload {
    // Legacy UI boundary; callers must not treat String adapters as erasure guarantees.
    constructor(
        title: String,
        username: CharArray,
        password: CharArray,
        uri: String,
        notes: CharArray? = null,
        customFields: List<CustomField>? = null,
    ) : this(title.toCharArray(), username, password, uri.toCharArray(), notes, customFields)

    override fun clearMemory() {
        title.fill('\u0000')
        username.fill('\u0000')
        password.fill('\u0000')
        uri.fill('\u0000')
        notes?.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }

    fun copyForEditing(): PasswordPayload =
        copy(
            title = title.copyOf(),
            username = username.copyOf(),
            password = password.copyOf(),
            uri = uri.copyOf(),
            notes = notes?.copyOf(),
            customFields = customFields?.map { it.copyForEditing() },
        )

    override fun toString(): String = "PasswordPayload([redacted])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordPayload) return false
        if (!title.contentEquals(other.title)) return false
        if (!username.contentEquals(other.username)) return false
        if (!password.contentEquals(other.password)) return false
        if (!uri.contentEquals(other.uri)) return false
        if (!notes.contentEquals(other.notes)) return false
        if (customFields != other.customFields) return false
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + title.contentHashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + uri.contentHashCode()
        result = 31 * result + notes.contentHashCode()
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
