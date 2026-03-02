package com.chimali.fido2.domain.model

/**
 * Domain model representing AttestationConveyancePreference.
 * This specifies preferences for attestation conveyance.
 */
enum class AttestationConveyancePreference {
    NONE,
    INDIRECT,
    DIRECT,
    ENTERPRISE,
    
    /**
     * Returns a description of the preference.
     */
    fun getDescription(): String {
        return when (this) {
            NONE -> "No attestation preference"
            INDIRECT -> "Indirect attestation"
            DIRECT -> "Direct attestation"
            ENTERPRISE -> "Enterprise attestation"
        }
    }
    
    /**
     * Checks if this preference allows attestation.
     */
    fun allowsAttestation(): Boolean {
        return this != NONE
    }
    
    /**
     * Checks if this requires direct attestation.
     */
    fun requiresDirectAttestation(): Boolean {
        return this == DIRECT
    }
    
    /**
     * Checks if this requires enterprise attestation.
     */
    fun requiresEnterpriseAttestation(): Boolean {
        return this == ENTERPRISE
    }
    
    companion object {
        /**
         * Creates a preference from string value.
         */
        fun fromString(value: String): AttestationConveyancePreference {
            return when (value.lowercase()) {
                "none" -> NONE
                "indirect" -> INDIRECT
                "direct" -> DIRECT
                "enterprise" -> ENTERPRISE
                else -> NONE // Default to none
            }
        }
        
        /**
         * Returns the default preference.
         */
        fun getDefault(): AttestationConveyancePreference {
            return NONE
        }
    }
}
