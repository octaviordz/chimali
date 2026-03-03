package com.chimali.fido2.domain.model

import java.time.Instant

/**
 * Domain model representing a user consent record for FIDO2 operations.
 * This entity tracks user consent for authentication and registration operations.
 */
data class UserConsentRecord(
    val id: String,
    val operationType: ConsentOperationType,
    val rpId: String,
    val credentialId: String?,
    val timestamp: Instant,
    val biometricUsed: Boolean,
    val pinUsed: Boolean,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceId: String?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the UserConsentRecord according to security requirements.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(id.isNotBlank()) { "Consent record ID cannot be blank" }
        require(rpId.isNotBlank()) { "RP ID cannot be blank" }
        require(timestamp.isBefore(Instant.now().plusSeconds(60))) { 
            "Timestamp cannot be more than 60 seconds in the future" 
        }
        
        // Validate RP ID format
        require(rpId.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+[a-zA-Z0-9./:-]*$"))) { 
            "RP ID must be a valid domain or HTTPS origin" 
        }
        
        // Validate credential ID if present
        credentialId?.let { credId ->
            require(credId.isNotBlank()) { "Credential ID cannot be blank if provided" }
            require(credId.length <= 1023) { "Credential ID cannot exceed 1023 bytes" }
        }
        
        // Validate optional fields
        ipAddress?.let { ip ->
            require(ip.isNotBlank()) { "IP address cannot be blank if provided" }
            require(ip.length <= 45) { "IP address cannot exceed 45 characters" }
            require(isValidIpAddress(ip)) { "IP address must be valid IPv4 or IPv6 format" }
        }
        
        userAgent?.let { ua ->
            require(ua.isNotBlank()) { "User agent cannot be blank if provided" }
            require(ua.length <= 512) { "User agent cannot exceed 512 characters" }
        }
        
        deviceId?.let { device ->
            require(device.isNotBlank()) { "Device ID cannot be blank if provided" }
            require(device.length <= 64) { "Device ID cannot exceed 64 characters" }
        }
        
        // Validate consent method
        require(biometricUsed || pinUsed) { 
            "At least one consent method (biometric or PIN) must be used" 
        }
    }
    
    /**
     * Checks if this consent was given recently.
     */
    fun isRecent(minutes: Long = 5): Boolean {
        val cutoff = timestamp.plusSeconds(minutes * 60)
        return Instant.now().isBefore(cutoff)
    }
    
    /**
     * Checks if this consent was for a specific credential.
     */
    fun isForCredential(credentialId: String): Boolean {
        return this.credentialId?.equals(credentialId, ignoreCase = true) ?: false
    }
    
    /**
     * Checks if this consent was for a specific relying party.
     */
    fun isForRelyingParty(rpId: String): Boolean {
        return this.rpId.equals(rpId, ignoreCase = true)
    }
    
    /**
     * Returns the consent method used.
     */
    fun getConsentMethod(): ConsentMethod {
        return when {
            biometricUsed && pinUsed -> ConsentMethod.BIOMETRIC_AND_PIN
            biometricUsed -> ConsentMethod.BIOMETRIC
            pinUsed -> ConsentMethod.PIN
            else -> ConsentMethod.NONE
        }
    }
    
    /**
     * Returns a safe representation of the credential ID.
     */
    fun getSafeCredentialId(): String {
        return credentialId ?: "N/A"
    }
    
    /**
     * Checks if this consent record is for registration.
     */
    fun isRegistrationConsent(): Boolean {
        return operationType == ConsentOperationType.REGISTRATION
    }
    
    /**
     * Checks if this consent record is for authentication.
     */
    fun isAuthenticationConsent(): Boolean {
        return operationType == ConsentOperationType.AUTHENTICATION
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_IP_ADDRESS_LENGTH = 45
        const val MAX_USER_AGENT_LENGTH = 512
        const val MAX_DEVICE_ID_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        
        /**
         * Creates a new UserConsentRecord with validation.
         */
        fun create(
            operationType: ConsentOperationType,
            rpId: String,
            credentialId: String? = null,
            biometricUsed: Boolean,
            pinUsed: Boolean,
            ipAddress: String? = null,
            userAgent: String? = null,
            deviceId: String? = null
        ): UserConsentRecord {
            return UserConsentRecord(
                id = java.util.UUID.randomUUID().toString(),
                operationType = operationType,
                rpId = rpId,
                credentialId = credentialId,
                timestamp = Instant.now(),
                biometricUsed = biometricUsed,
                pinUsed = pinUsed,
                ipAddress = ipAddress,
                userAgent = userAgent,
                deviceId = deviceId
            )
        }
        
        /**
         * Validates IP address format (IPv4 or IPv6).
         */
        private fun isValidIpAddress(ip: String): Boolean {
            return try {
                // IPv4 validation
                val ipv4Regex = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                if (ipv4Regex.matches(ip)) return true
                
                // IPv6 validation (simplified)
                val ipv6Regex = Regex("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
                ipv6Regex.matches(ip)
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Enumeration of consent operation types.
 */
enum class ConsentOperationType {
    REGISTRATION,
    AUTHENTICATION,
    CREDENTIAL_DELETION,
    CREDENTIAL_UPDATE
}

/**
 * Enumeration of consent methods.
 */
enum class ConsentMethod {
    NONE,
    BIOMETRIC,
    PIN,
    BIOMETRIC_AND_PIN
}
