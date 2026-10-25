package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain model representing a user consent record for FIDO2 operations.
 * This entity tracks user consent for authentication and registration operations.
 */
@Serializable
class UserConsentRecord private constructor(
    val id: String,
    val operationType: ConsentOperationType,
    @SerialName("rpId") private val rpIdOwner: SensitiveText,
    val credentialId: CredentialId?,
    val timestamp: Instant,
    val isBiometricUsed: Boolean,
    val isPinUsed: Boolean,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceId: String?,
) {
    /** Copies a compatibility RP value into independent mutable model ownership. */
    constructor(
        id: String,
        operationType: ConsentOperationType,
        rpId: RpId,
        credentialId: CredentialId?,
        timestamp: Instant,
        isBiometricUsed: Boolean,
        isPinUsed: Boolean,
        ipAddress: String?,
        userAgent: String?,
        deviceId: String?,
    ) : this(
        id,
        operationType,
        SensitiveText.copyOf(rpId.value),
        credentialId,
        timestamp,
        isBiometricUsed,
        isPinUsed,
        ipAddress,
        userAgent,
        deviceId,
    )

    /** Compatibility/display RP value; persistence and policy code borrow rpIdText. */
    val rpId: RpId get() = RpId(rpIdOwner.toBoundaryString())

    /** Borrowed RP text, valid until this consent owner ends. */
    val rpIdText: SensitiveText get() = rpIdOwner

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            rpIdOwner.erase()
            throw failure
        }
    }

    /** Erases the RP association owned by this record. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() = rpIdOwner.erase()

    /** Deep-copies the owned RP while retaining the existing public optional-field contract. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: String = this.id,
        operationType: ConsentOperationType = this.operationType,
        rpId: RpId? = null,
        credentialId: CredentialId? = this.credentialId,
        timestamp: Instant = this.timestamp,
        isBiometricUsed: Boolean = this.isBiometricUsed,
        isPinUsed: Boolean = this.isPinUsed,
        ipAddress: String? = this.ipAddress,
        userAgent: String? = this.userAgent,
        deviceId: String? = this.deviceId,
    ): UserConsentRecord =
        copyWithOwnerFactory(
            id,
            operationType,
            rpId,
            credentialId,
            timestamp,
            isBiometricUsed,
            isPinUsed,
            ipAddress,
            userAgent,
            deviceId,
        )

    /** Creates a copy through an injectable owner factory so rejection cleanup is observable. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyWithOwnerFactory(
        id: String = this.id,
        operationType: ConsentOperationType = this.operationType,
        rpId: RpId? = null,
        credentialId: CredentialId? = this.credentialId,
        timestamp: Instant = this.timestamp,
        isBiometricUsed: Boolean = this.isBiometricUsed,
        isPinUsed: Boolean = this.isPinUsed,
        ipAddress: String? = this.ipAddress,
        userAgent: String? = this.userAgent,
        deviceId: String? = this.deviceId,
        ownerFactory: (CharSequence) -> SensitiveText = SensitiveText::copyOf,
    ): UserConsentRecord {
        var createdOwner: SensitiveText? = null
        try {
            createdOwner = ownerFactory(rpId?.value ?: rpIdOwner)
            return UserConsentRecord(
                id,
                operationType,
                requireNotNull(createdOwner),
                credentialId,
                timestamp,
                isBiometricUsed,
                isPinUsed,
                ipAddress,
                userAgent,
                deviceId,
            ).also { createdOwner = null }
        } finally {
            createdOwner?.erase()
        }
    }

    /** Compares record fields without materializing the RP association. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is UserConsentRecord &&
            id == other.id &&
            operationType == other.operationType &&
            rpIdOwner == other.rpIdOwner &&
            credentialId == other.credentialId &&
            timestamp == other.timestamp &&
            isBiometricUsed == other.isBiometricUsed &&
            isPinUsed == other.isPinUsed &&
            ipAddress == other.ipAddress &&
            userAgent == other.userAgent &&
            deviceId == other.deviceId

    /** Hashes fields used by equals without RP text copies. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int =
        listOf(
            id,
            operationType,
            rpIdOwner,
            credentialId,
            timestamp,
            isBiometricUsed,
            isPinUsed,
            ipAddress,
            userAgent,
            deviceId,
        ).hashCode()

    /** Redacts the RP and optional identifying fields from diagnostics. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "UserConsentRecord([sensitive metadata redacted])"

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
        require(isValidRpId(rpIdOwner)) {
            "RP ID must be a valid domain or HTTPS origin"
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isRecent(minutes: Int = 5): Boolean {
        val now = Clock.System.now()
        return (now - timestamp) <= minutes.minutes
    }

    /**
     * Checks if this consent was for a specific credential ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isForCredential(credentialId: CredentialId): Boolean = this.credentialId == credentialId

    /**
     * Checks if this consent was for a specific relying party.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isForRelyingParty(rpId: RpId): Boolean = rpIdOwner.contentEquals(rpId.value, ignoreCase = true)

    /**
     * Returns the consent method used.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getConsentMethod(): ConsentMethod =
        when {
            isBiometricUsed && isPinUsed -> ConsentMethod.BIOMETRIC_AND_PIN
            isBiometricUsed -> ConsentMethod.BIOMETRIC
            isPinUsed -> ConsentMethod.PIN
            else -> ConsentMethod.NONE
        }

    /**
     * Returns a safe representation of the credential ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeCredentialId(): String = credentialId?.encoded ?: "N/A"

    /**
     * Checks if this consent record is for registration.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isRegistrationConsent(): Boolean = operationType == ConsentOperationType.REGISTRATION

    /**
     * Checks if this consent record is for authentication.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isAuthenticationConsent(): Boolean = operationType == ConsentOperationType.AUTHENTICATION

    companion object {
        const val MAX_IP_ADDRESS_LENGTH = 45
        const val MAX_USER_AGENT_LENGTH = 512
        const val MAX_DEVICE_ID_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        private const val MAX_IPV6_GROUP_LENGTH = 4
        private val FUTURE_GRACE = 1.minutes

        /** Copies a borrowed RP into a new consent owner; the caller erases the returned record. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromText(
            id: String,
            operationType: ConsentOperationType,
            rpId: CharSequence,
            credentialId: CredentialId? = null,
            timestamp: Instant = Clock.System.now(),
            isBiometricUsed: Boolean = false,
            isPinUsed: Boolean = false,
        ): UserConsentRecord =
            UserConsentRecord(
                id,
                operationType,
                SensitiveText.copyOf(rpId),
                credentialId,
                timestamp,
                isBiometricUsed,
                isPinUsed,
                null,
                null,
                null,
            )

        /**
         * Creates a new UserConsentRecord with validation.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun create(
            id: String,
            operationType: ConsentOperationType,
            rpId: RpId,
            credentialId: CredentialId? = null,
            timestamp: Instant = Clock.System.now(),
            isBiometricUsed: Boolean = false,
            isPinUsed: Boolean = false,
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
                isBiometricUsed = isBiometricUsed,
                isPinUsed = isPinUsed,
                ipAddress = ipAddress,
                userAgent = userAgent,
                deviceId = deviceId,
            )

        /**
         * Validates RP ID format according to FIDO2 specifications.
         */
        private fun isValidRpId(rpId: CharSequence): Boolean =
            Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[a-zA-Z0-9./_-]*)?$").matches(rpId)

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
