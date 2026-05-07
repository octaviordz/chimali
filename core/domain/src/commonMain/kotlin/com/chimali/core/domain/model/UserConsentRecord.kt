package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain model representing a user consent record for FIDO2 operations.
 * This entity tracks user consent for authentication and registration operations.
 */
@Serializable
data class UserConsentRecord(
    val id: String,
    val operationType: ConsentOperationType,
    val rpId: RpId,
    val credentialId: CredentialId?,
    val timestamp: Instant,
    val biometricUsed: Boolean,
    val pinUsed: Boolean,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceId: String?,
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

        val now = Clock.System.now()
        require(timestamp <= now + FUTURE_GRACE) {
            "Consent timestamp cannot be in the future"
        }

        // Validate RP ID format
        require(isValidRpId(rpId.value)) {
            "RP ID must be a valid domain or HTTPS origin: ${rpId.value}"
        }

        credentialId?.let { id ->
            require(id.toByteArray().size <= MAX_CREDENTIAL_ID_LENGTH) {
                "Credential ID exceeds maximum length of $MAX_CREDENTIAL_ID_LENGTH bytes"
            }
        }

        // Validate optional fields
        ipAddress?.let { ip ->
            require(ip.isNotBlank()) { "IP address cannot be blank if provided" }
            require(ip.length <= MAX_IP_ADDRESS_LENGTH) {
                "IP address exceeds maximum length of $MAX_IP_ADDRESS_LENGTH"
            }
            require(isValidIpAddress(ip)) { "Invalid IP address format: $ip" }
        }

        userAgent?.let { ua ->
            require(ua.isNotBlank()) { "User agent cannot be blank if provided" }
            require(ua.length <= MAX_USER_AGENT_LENGTH) {
                "User agent exceeds maximum length of $MAX_USER_AGENT_LENGTH"
            }
        }

        deviceId?.let { device ->
            require(device.isNotBlank()) { "Device ID cannot be blank if provided" }
            require(device.length <= MAX_DEVICE_ID_LENGTH) {
                "Device ID exceeds maximum length of $MAX_DEVICE_ID_LENGTH"
            }
        }
    }

    /**
     * Checks if this consent is still considered recent (e.g., within 5 minutes).
     */
    fun isRecent(minutes: Int = 5): Boolean {
        val now = Clock.System.now()
        return (now - timestamp) <= minutes.minutes
    }

    /**
     * Checks if this consent was for a specific credential ID.
     */
    fun isForCredential(credentialId: CredentialId): Boolean = this.credentialId == credentialId

    /**
     * Checks if this consent was for a specific relying party.
     */
    fun isForRelyingParty(rpId: RpId): Boolean = this.rpId.value.equals(rpId.value, ignoreCase = true)

    /**
     * Returns the consent method used.
     */
    fun getConsentMethod(): ConsentMethod =
        when {
            biometricUsed && pinUsed -> ConsentMethod.BIOMETRIC_AND_PIN
            biometricUsed -> ConsentMethod.BIOMETRIC
            pinUsed -> ConsentMethod.PIN
            else -> ConsentMethod.NONE
        }

    /**
     * Returns a safe representation of the credential ID.
     */
    fun getSafeCredentialId(): String = credentialId?.encoded ?: "N/A"

    /**
     * Checks if this consent record is for registration.
     */
    fun isRegistrationConsent(): Boolean = operationType == ConsentOperationType.REGISTRATION

    /**
     * Checks if this consent record is for authentication.
     */
    fun isAuthenticationConsent(): Boolean = operationType == ConsentOperationType.AUTHENTICATION

    companion object {
        const val MAX_IP_ADDRESS_LENGTH = 45
        const val MAX_USER_AGENT_LENGTH = 512
        const val MAX_DEVICE_ID_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        private const val MAX_IPV6_GROUP_LENGTH = 4
        private val FUTURE_GRACE = 1.minutes

        /**
         * Creates a new UserConsentRecord with validation.
         */
        fun create(
            id: String,
            operationType: ConsentOperationType,
            rpId: RpId,
            credentialId: CredentialId? = null,
            timestamp: Instant = Clock.System.now(),
            biometricUsed: Boolean = false,
            pinUsed: Boolean = false,
            ipAddress: String? = null,
            userAgent: String? = null,
            deviceId: String? = null,
        ): UserConsentRecord =
            UserConsentRecord(
                id = id,
                operationType = operationType,
                rpId = rpId,
                credentialId = credentialId,
                timestamp = timestamp,
                biometricUsed = biometricUsed,
                pinUsed = pinUsed,
                ipAddress = ipAddress,
                userAgent = userAgent,
                deviceId = deviceId,
            )

        /**
         * Validates RP ID format according to FIDO2 specifications.
         */
        private fun isValidRpId(rpId: String): Boolean =
            rpId.matches(Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[a-zA-Z0-9./_-]*)?$"))

        /**
         * Validates IP address format (IPv4 or IPv6).
         */
        private fun isValidIpAddress(ip: String): Boolean {
            return try {
                // IPv4 validation
                val ipv4Regex =
                    Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                if (ipv4Regex.matches(ip)) return true

                // IPv6 validation (supports compression)
                if (ip.contains(':')) {
                    if (ip.contains(":::")) return false
                    val colons = ip.count { it == ':' }
                    if (colons !in 2..7) return false

                    val validChars = ip.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
                    if (!validChars) return false

                    val groups = ip.split(":")
                    return groups.all { it.length <= MAX_IPV6_GROUP_LENGTH }
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Enumeration of consent operation types.
 */
@Serializable
enum class ConsentOperationType {
    REGISTRATION,
    AUTHENTICATION,
    CREDENTIAL_DELETION,
    CREDENTIAL_UPDATE,
}

/**
 * Enumeration of consent methods.
 */
@Serializable
enum class ConsentMethod {
    NONE,
    BIOMETRIC,
    PIN,
    BIOMETRIC_AND_PIN,
}
