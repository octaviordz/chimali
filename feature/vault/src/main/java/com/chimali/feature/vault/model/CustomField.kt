package com.chimali.feature.vault.model

data class CustomField(
    val name: String,
    val value: CharArray,
    val isConcealed: Boolean,
) {
    fun clearMemory() {
        value.fill('\u0000')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CustomField

        if (name != other.name) return false
        if (!value.contentEquals(other.value)) return false
        if (isConcealed != other.isConcealed) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + isConcealed.hashCode()
        return result
    }
}
