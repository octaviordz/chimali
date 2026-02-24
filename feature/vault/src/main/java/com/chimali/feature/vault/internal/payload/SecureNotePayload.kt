package com.chimali.feature.vault.internal.payload

data class SecureNotePayload(
    val title: String,
    val content: CharArray,
    val customFields: List<CustomField>? = null
) {
    fun clearMemory() {
        content.fill('0')
        customFields?.forEach { it.clearMemory() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecureNotePayload

        if (title != other.title) return false
        if (!content.contentEquals(other.content)) return false
        if (customFields != other.customFields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
