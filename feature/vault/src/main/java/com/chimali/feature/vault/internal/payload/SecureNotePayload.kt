package com.chimali.feature.vault.internal.payload

/** FR-VAULT-026: owns its arrays; copies transfer independent ownership. */
data class SecureNotePayload(
    val title: CharArray,
    val content: CharArray,
    val customFields: List<CustomField>? = null,
) : SensitivePayload {
    // Legacy UI boundary; callers must not treat String adapters as erasure guarantees.
    constructor(
        title: String,
        content: CharArray,
        customFields: List<CustomField>? = null,
    ) : this(title.toCharArray(), content, customFields)

    override fun clearMemory() {
        title.fill('\u0000')
        content.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }

    fun copyForEditing(): SecureNotePayload =
        copy(
            title = title.copyOf(),
            content = content.copyOf(),
            customFields = customFields?.map { it.copyForEditing() },
        )

    override fun toString(): String = "SecureNotePayload([redacted])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureNotePayload) return false
        if (!title.contentEquals(other.title)) return false
        if (!content.contentEquals(other.content)) return false
        if (customFields != other.customFields) return false
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + title.contentHashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
