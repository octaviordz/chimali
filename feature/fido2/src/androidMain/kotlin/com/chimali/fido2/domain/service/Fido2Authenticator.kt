package com.chimali.fido2.domain.service

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AuthenticatorTransport
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Service interface for FIDO2 authenticator operations.
 * Provides methods for credential registration and authentication.
 */
interface Fido2Authenticator {
    /**
     * Registers a new credential with the authenticator.
     *
     * @param options The registration options containing all necessary parameters
     * @return Outcome containing AttestationObject on success, error on failure
     */
    suspend fun makeCredential(options: MakeCredentialOptions): Outcome<AttestationObject, DomainError>

    /**
     * Authenticates using an existing credential.
     *
     * @param assertionOptions The assertion options for authentication
     * @return Outcome containing AssertionObject on success, error on failure
     */
    suspend fun getAssertion(assertionOptions: GetAssertionOptions): Outcome<AssertionObject, DomainError>

    /**
     * Retrieves all credentials stored on the authenticator.
     *
     * @return Flow of stored credentials
     */
    fun getCredentials(): Flow<PasskeyCredential>

    /**
     * Retrieves credentials for a specific relying party.
     *
     * @param rpId The ID of the relying party
     * @return Flow of credentials for the RP
     */
    fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential>

    /**
     * Deletes a credential from the authenticator.
     *
     * @param credentialId The ID of the credential to delete
     * @param rpId The ID of the relying party requesting deletion
     * @return Outcome indicating success or failure
     */
    suspend fun deleteCredential(
        credentialId: CredentialId,
        rpId: RpId? = null,
    ): Outcome<Unit, DomainError>

    /**
     * Updates user verification preferences.
     *
     * @param preferences The new verification preferences
     * @return Outcome indicating success or failure
     */
    suspend fun updateVerificationPreferences(preferences: VerificationPreferences): Outcome<Unit, DomainError>

    /**
     * Retrieves current verification preferences.
     *
     * @return Current verification preferences
     */
    suspend fun getVerificationPreferences(): VerificationPreferences

    /**
     * Checks if the authenticator supports specific algorithms.
     *
     * @param algorithm The cryptographic algorithm to check
     * @return True if supported, false otherwise
     */
    suspend fun supportsAlgorithm(algorithm: String): Boolean

    /**
     * Checks if the authenticator supports resident keys.
     *
     * @return True if resident keys are supported
     */
    suspend fun supportsResidentKeys(): Boolean

    /**
     * Checks if the authenticator supports user verification.
     *
     * @return True if user verification is supported
     */
    suspend fun supportsUserVerification(): Boolean

    /**
     * Retrieves the authenticator's capabilities and information.
     *
     * @return AuthenticatorInfo containing capabilities and metadata
     */
    suspend fun getAuthenticatorInfo(): AuthenticatorInfo

    /**
     * Resets the authenticator to factory settings.
     *
     * @param resetType The type of reset to perform
     * @return Outcome indicating success or failure
     */
    suspend fun resetAuthenticator(resetType: AuthenticatorResetType): Outcome<Unit, DomainError>

    /**
     * Retrieves the current authenticator state.
     *
     * @return AuthenticatorState indicating current status
     */
    suspend fun getAuthenticatorState(): AuthenticatorState

    /**
     * Performs a health check on the authenticator.
     *
     * @return Outcome containing HealthCheckResult
     */
    suspend fun performHealthCheck(): Outcome<HealthCheckResult, DomainError>

    /**
     * Configures the authenticator with specific settings.
     *
     * @param configuration The configuration to apply
     * @return Outcome indicating success or failure
     */
    suspend fun configureAuthenticator(configuration: AuthenticatorConfiguration): Outcome<Unit, DomainError>

    /**
     * Retrieves the current authenticator configuration.
     *
     * @return Current authenticator configuration
     */
    suspend fun getAuthenticatorConfiguration(): AuthenticatorConfiguration

    /**
     * Checks if the authenticator is ready for operations.
     *
     * @return True if ready, false otherwise
     */
    suspend fun isReady(): Boolean

    /**
     * Initiates a pairing process with another device.
     *
     * @param pairingRequest The pairing request information
     * @return Outcome containing PairingResult
     */
    suspend fun initiatePairing(pairingRequest: PairingRequest): Outcome<PairingResult, DomainError>

    /**
     * Retrieves supported transport methods.
     *
     * @return List of supported transport methods
     */
    suspend fun getSupportedTransports(): List<AuthenticatorTransport>

    /**
     * Validates that the authenticator can handle a specific request.
     *
     * @param request The request to validate
     * @return Outcome indicating if the request can be handled
     */
    suspend fun validateRequest(request: Fido2Request): Outcome<RequestValidationResult, DomainError>
}

/**
 * Data class representing verification preferences.
 */
data class VerificationPreferences(
    val isUserVerificationRequired: Boolean,
    val preferredVerificationMethod: VerificationMethod,
    val isBiometricFallbackAllowed: Boolean,
    val isPinFallbackAllowed: Boolean,
    val biometricTimeoutMs: Long,
    val pinTimeoutMs: Long,
    val maxVerificationAttempts: Int,
) {
    /**
     * Checks if biometric verification is enabled.
     */
    fun isBiometricEnabled(): Boolean =
        preferredVerificationMethod == VerificationMethod.BIOMETRIC ||
            preferredVerificationMethod == VerificationMethod.BIOMETRIC_AND_PIN

    /**
     * Checks if PIN verification is enabled.
     */
    fun isPinEnabled(): Boolean =
        preferredVerificationMethod == VerificationMethod.PIN ||
            preferredVerificationMethod == VerificationMethod.BIOMETRIC_AND_PIN

    /**
     * Returns the timeout for the preferred method.
     */
    fun getPreferredTimeout(): Long =
        when (preferredVerificationMethod) {
            VerificationMethod.BIOMETRIC -> biometricTimeoutMs
            VerificationMethod.PIN -> pinTimeoutMs
            VerificationMethod.BIOMETRIC_AND_PIN -> maxOf(biometricTimeoutMs, pinTimeoutMs)
            else -> DEFAULT_VERIFICATION_TIMEOUT_MS // Default 30 seconds
        }

    companion object {
        private const val DEFAULT_VERIFICATION_TIMEOUT_MS = 30000L

        /**
         * Creates default verification preferences.
         */
        fun createDefault(): VerificationPreferences =
            VerificationPreferences(
                isUserVerificationRequired = true,
                preferredVerificationMethod = VerificationMethod.BIOMETRIC,
                isBiometricFallbackAllowed = true,
                isPinFallbackAllowed = true,
                // 30 seconds
                biometricTimeoutMs = DEFAULT_VERIFICATION_TIMEOUT_MS,
                // 60 seconds
                pinTimeoutMs = 60000L,
                maxVerificationAttempts = 3,
            )
    }
}

/**
 * Data class representing authenticator information.
 */
data class AuthenticatorInfo(
    val aaguid: ByteArray,
    val version: String,
    val supportedAlgorithms: List<String>,
    val supportedTransports: List<AuthenticatorTransport>,
    val isResidentKeySupported: Boolean,
    val isUserVerificationSupported: Boolean,
    val maxCredentialCount: Int,
    val maxCredentialIdLength: Int,
    val firmwareVersion: String,
    val serialNumber: String,
    val isInitialized: Boolean,
    val isLocked: Boolean,
) {
    /**
     * Checks if the authenticator is ready.
     */
    fun isReady(): Boolean = isInitialized && !isLocked

    /**
     * Returns a description of the authenticator.
     */
    fun getDescription(): String = "Authenticator v$version (AAGUID: ${aaguid.joinToString("") { "%02x".format(it) }})"

    /**
     * Checks if a specific algorithm is supported.
     */
    fun supportsAlgorithm(algorithm: String): Boolean = supportedAlgorithms.contains(algorithm)

    /**
     * Checks if a specific transport is supported.
     */
    fun supportsTransport(transport: AuthenticatorTransport): Boolean = supportedTransports.contains(transport)
}

/**
 * Data class representing authenticator configuration.
 */
data class AuthenticatorConfiguration(
    val isUserVerificationRequired: Boolean,
    val allowedAlgorithms: List<String>,
    val allowedTransports: List<AuthenticatorTransport>,
    val isResidentKeysEnabled: Boolean,
    val maxCredentialCount: Int,
    val biometricSettings: BiometricSettings?,
    val pinSettings: PinSettings?,
    val securityLevel: SecurityLevel,
) {
    /**
     * Checks if biometric verification is configured.
     */
    fun hasBiometricSettings(): Boolean = biometricSettings != null

    /**
     * Checks if PIN verification is configured.
     */
    fun hasPinSettings(): Boolean = pinSettings != null

    /**
     * Returns the security level description.
     */
    fun getSecurityLevelDescription(): String =
        when (securityLevel) {
            SecurityLevel.HIGH -> "High security"
            SecurityLevel.MEDIUM -> "Medium security"
            SecurityLevel.LOW -> "Low security"
            SecurityLevel.MINIMAL -> "Minimal security"
        }
}

/**
 * Data class representing biometric settings.
 */
data class BiometricSettings(
    val enabledTypes: List<BiometricType>,
    val requiredStrength: BiometricStrength,
    val timeoutMs: Long,
    val maxAttempts: Int,
    val isFallbackAllowed: Boolean,
) {
    /**
     * Checks if a specific biometric type is enabled.
     */
    fun isTypeEnabled(type: BiometricType): Boolean = enabledTypes.contains(type)

    /**
     * Returns the strongest enabled biometric type.
     */
    fun getStrongestType(): BiometricType? = enabledTypes.maxByOrNull { it.level }
}

/**
 * Data class representing PIN settings.
 */
data class PinSettings(
    val minLength: Int,
    val maxLength: Int,
    val isComplexityRequired: Boolean,
    val allowedSpecialChars: String?,
    val maxAttempts: Int,
    val lockoutDurationMs: Long,
    val isBiometricFallbackAllowed: Boolean,
) {
    /**
     * Validates a PIN against these settings.
     */
    fun validatePin(pin: String): Boolean {
        if (pin.length !in minLength..maxLength) return false
        if (isComplexityRequired && !meetsComplexityRequirements(pin)) return false
        return true
    }

    /**
     * Checks if PIN meets complexity requirements.
     */
    private fun meetsComplexityRequirements(pin: String): Boolean {
        if (!isComplexityRequired) return true

        val hasLetter = pin.any { it.isLetter() }
        val hasDigit = pin.any { it.isDigit() }

        return hasLetter && hasDigit
    }
}

/**
 * Enumeration of authenticator reset types.
 */
enum class AuthenticatorResetType {
    SOFT_RESET,
    HARD_RESET,
    FACTORY_RESET,
    USER_DATA_RESET,
}

/**
 * Enumeration of authenticator states.
 */
enum class AuthenticatorState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    BUSY,
    ERROR,
    LOCKED,
    UPDATING_FIRMWARE,
}

/**
 * Data class representing health check result.
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val checks: Map<String, HealthCheckStatus>,
    val timestamp: Instant,
    val errorMessage: String?,
) {
    /**
     * Checks if all health checks passed.
     */
    fun allChecksPassed(): Boolean = checks.values.all { it == HealthCheckStatus.PASSED }

    /**
     * Returns a summary of health check results.
     */
    fun getSummary(): String {
        val passed = checks.values.count { it == HealthCheckStatus.PASSED }
        val total = checks.size
        return "Health check: $passed/$total passed"
    }
}

/**
 * Data class representing pairing request.
 */
data class PairingRequest(
    val deviceId: String,
    val deviceName: String,
    val transportType: AuthenticatorTransport,
    val protocolVersion: String,
    val timeoutMs: Long,
) {
    /**
     * Returns a description of the pairing request.
     */
    fun getDescription(): String = "Pair with $deviceName via $transportType (v$protocolVersion)"
}

/**
 * Data class representing pairing result.
 */
data class PairingResult(
    val isSuccess: Boolean,
    val pairingId: String?,
    val errorMessage: String?,
    val timestamp: Instant,
) {
    /**
     * Checks if pairing was successful.
     */
    fun isSuccessful(): Boolean = isSuccess
}

/**
 * Data class representing FIDO2 request.
 */
data class Fido2Request(
    val type: String,
    val rpId: String,
    val challenge: ByteArray?,
    val algorithm: String?,
    val timeoutMs: Long?,
) {
    /**
     * Returns a description of the request.
     */
    fun getDescription(): String = "FIDO2 $type request for $rpId"
}

/**
 * Data class representing request validation result.
 */
data class RequestValidationResult(
    val isValid: Boolean,
    val supportedFeatures: List<String>,
    val unsupportedFeatures: List<String>,
    val warnings: List<String>,
    val errorMessage: String?,
) {
    /**
     * Checks if validation was successful.
     */
    fun isValidRequest(): Boolean = isValid

    /**
     * Returns a summary of validation results.
     */
    fun getSummary(): String =
        if (isValid) {
            "Request is valid"
        } else {
            "Request has ${unsupportedFeatures.size} unsupported features"
        }
}

/**
 * Enumeration of security levels.
 */
enum class SecurityLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Enumeration of health check status.
 */
enum class HealthCheckStatus {
    PASSED,
    FAILED,
    WARNING,
    NOT_APPLICABLE,
}
