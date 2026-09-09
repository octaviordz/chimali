package com.chimali.feature.vault.internal.payload

/** FR-VAULT-026: owns its arrays; copies transfer independent ownership. */
data class CustomField(
    val name: CharArray,
    val value: CharArray,
    val isConcealed: Boolean,
) : SensitivePayload {
    // Legacy UI boundary; callers must not treat String adapters as erasure guarantees.
    constructor(
        name: String,
        value: CharArray,
        isConcealed: Boolean,
    ) : this(name.toCharArray(), value, isConcealed)

    override fun clearMemory() {
        name.fill('\u0000')
        value.fill('\u0000')
    }

    fun copyForEditing(): CustomField =
        copy(
            name = name.copyOf(),
            value = value.copyOf(),
        )

    override fun toString(): String = "CustomField([redacted])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomField) return false
        if (!name.contentEquals(other.name)) return false
        if (!value.contentEquals(other.value)) return false
        if (isConcealed != other.isConcealed) return false
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + name.contentHashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + isConcealed.hashCode()
        return result
    }
}
