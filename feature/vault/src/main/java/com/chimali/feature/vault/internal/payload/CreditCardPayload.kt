package com.chimali.feature.vault.internal.payload

data class CreditCardPayload(
    val title: String,
    val cardholderName: CharArray,
    val cardNumber: CharArray,
    val expirationDate: String, // MM/YY
    val cvv: CharArray,
    val notes: CharArray? = null,
    val customFields: List<CustomField>? = null,
) {
    fun clearMemory() {
        cardholderName.fill('0')
        cardNumber.fill('0')
        cvv.fill('0')
        notes?.fill('0')
        customFields?.forEach { it.clearMemory() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CreditCardPayload

        if (title != other.title) return false
        if (!cardholderName.contentEquals(other.cardholderName)) return false
        if (!cardNumber.contentEquals(other.cardNumber)) return false
        if (expirationDate != other.expirationDate) return false
        if (!cvv.contentEquals(other.cvv)) return false

        if (notes != null) {
            if (other.notes == null) return false
            if (!notes.contentEquals(other.notes)) return false
        } else if (other.notes != null) {
            return false
        }

        if (customFields != other.customFields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + cardholderName.contentHashCode()
        result = 31 * result + cardNumber.contentHashCode()
        result = 31 * result + expirationDate.hashCode()
        result = 31 * result + cvv.contentHashCode()
        result = 31 * result + (notes?.contentHashCode() ?: 0)
        result = 31 * result + (customFields?.hashCode() ?: 0)
        return result
    }
}
