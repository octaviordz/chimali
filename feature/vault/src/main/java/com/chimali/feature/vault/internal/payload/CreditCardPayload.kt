package com.chimali.feature.vault.internal.payload

/** FR-VAULT-026: owns its arrays; copies transfer independent ownership. */
data class CreditCardPayload(
    val title: CharArray,
    val cardholderName: CharArray,
    val cardNumber: CharArray,
    val expirationDate: CharArray,
    val cvv: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) : SensitivePayload {
    // Legacy UI boundary; callers must not treat String adapters as erasure guarantees.
    constructor(
        title: String,
        cardholderName: CharArray,
        cardNumber: CharArray,
        expirationDate: String,
        cvv: CharArray,
        notes: CharArray? = null,
        customFields: List<CustomField>? = null,
    ) : this(title.toCharArray(), cardholderName, cardNumber, expirationDate.toCharArray(), cvv, notes, customFields)

    override fun clearMemory() {
        title.fill('\u0000')
        cardholderName.fill('\u0000')
        cardNumber.fill('\u0000')
        expirationDate.fill('\u0000')
        cvv.fill('\u0000')
        notes?.fill('\u0000')
        customFields?.forEach { it.clearMemory() }
    }

    fun copyForEditing(): CreditCardPayload =
        copy(
            title = title.copyOf(),
            cardholderName = cardholderName.copyOf(),
            cardNumber = cardNumber.copyOf(),
            expirationDate = expirationDate.copyOf(),
            cvv = cvv.copyOf(),
            notes = notes?.copyOf(),
            customFields = customFields?.map { it.copyForEditing() },
        )

    override fun toString(): String = "CreditCardPayload([redacted])"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreditCardPayload) return false
        if (!title.contentEquals(other.title)) return false
        if (!cardholderName.contentEquals(other.cardholderName)) return false
        if (!cardNumber.contentEquals(other.cardNumber)) return false
        if (!expirationDate.contentEquals(other.expirationDate)) return false
        if (!cvv.contentEquals(other.cvv)) return false
        if (!notes.contentEquals(other.notes)) return false
        if (customFields != other.customFields) return false
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + title.contentHashCode()
        result = 31 * result + cardholderName.contentHashCode()
        result = 31 * result + cardNumber.contentHashCode()
        result = 31 * result + expirationDate.contentHashCode()
        result = 31 * result + cvv.contentHashCode()
        result = 31 * result + notes.contentHashCode()
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
