package com.chimali.fido2.domain.service

import kotlinx.datetime.Instant

/**
 * Data class representing user verification availability.
 */
data class UserVerificationAvailability(
    val isBiometricAvailable: Boolean,
    val isPinAvailable: Boolean,
    val isDeviceLockAvailable: Boolean,
    val supportedBiometricTypes: List<BiometricType>,
    val maxPinLength: Int,
    val minPinLength: Int,
    val biometricStrength: BiometricStrength,
) {
    /**
     * Checks if any verification method is available.
     */
    fun hasAnyVerificationMethod(): Boolean = isBiometricAvailable || isPinAvailable || isDeviceLockAvailable

    /**
     * Returns the best available verification method.
     */
    fun getBestAvailableMethod(): VerificationMethod =
        when {
            isBiometricAvailable -> VerificationMethod.BIOMETRIC
            isPinAvailable -> VerificationMethod.PIN
            isDeviceLockAvailable -> VerificationMethod.DEVICE_LOCK
            else -> VerificationMethod.NONE
        }
}

/**
 * Data class representing biometric verification result.
 */
data class BiometricVerificationResult(
    val isSuccess: Boolean,
    val biometricType: BiometricType,
    val confidence: Float,
    val timestamp: Instant,
    val errorMessage: String?,
) {
    companion object {
        const val BIOMETRIC_CONFIDENCE_THRESHOLD = 0.7f
    }

    /**
     * Checks if verification was successful.
     */
    fun isSuccessful(): Boolean = isSuccess

    /**
     * Checks if confidence level is sufficient.
     */
    fun hasHighConfidence(): Boolean = confidence >= BIOMETRIC_CONFIDENCE_THRESHOLD
}

/**
 * Data class representing PIN verification result.
 */
data class PinVerificationResult(
    val isSuccess: Boolean,
    val attemptsRemaining: Int,
    val isLocked: Boolean,
    val timestamp: Instant,
    val errorMessage: String?,
) {
    /**
     * Checks if verification was successful.
     */
    fun isSuccessful(): Boolean = isSuccess

    /**
     * Checks if PIN attempts are exhausted.
     */
    fun isAttemptsExhausted(): Boolean = attemptsRemaining <= 0

    /**
     * Checks if PIN is temporarily locked.
     */
    fun isTemporarilyLocked(): Boolean = isLocked
}

/**
 * Data class representing combined verification result.
 */
data class CombinedVerificationResult(
    val isSuccess: Boolean,
    val biometricResult: BiometricVerificationResult?,
    val pinResult: PinVerificationResult?,
    val verificationMethod: VerificationMethod,
    val timestamp: Instant,
) {
    /**
     * Checks if verification was successful.
     */
    fun isSuccessful(): Boolean = isSuccess

    /**
     * Returns the successful verification method.
     */
    fun getSuccessfulMethod(): VerificationMethod =
        when {
            biometricResult?.isSuccessful() == true -> VerificationMethod.BIOMETRIC
            pinResult?.isSuccessful() == true -> VerificationMethod.PIN
            else -> VerificationMethod.NONE
        }
}

/**
 * Data class representing device lock verification result.
 */
data class DeviceLockVerificationResult(
    val isSuccess: Boolean,
    val timestamp: Instant,
    val errorMessage: String?,
) {
    /**
     * Checks if verification was successful.
     */
    fun isSuccessful(): Boolean = isSuccess
}

/**
 * Data class representing biometric enrollment status.
 */
data class BiometricEnrollmentStatus(
    val isEnrolled: Boolean,
    val enrolledTypes: List<BiometricType>,
    val enrollmentStrength: BiometricStrength,
    val lastUpdated: Instant,
) {
    /**
     * Checks if any biometric is enrolled.
     */
    fun hasAnyEnrollment(): Boolean = isEnrolled && enrolledTypes.isNotEmpty()

    /**
     * Returns the strongest enrolled biometric type.
     */
    fun getStrongestType(): BiometricType? = enrolledTypes.maxByOrNull { it.level }
}

/**
 * Data class representing PIN configuration.
 */
data class PinConfiguration(
    val minLength: Int,
    val maxLength: Int,
    val isComplexityRequired: Boolean,
    val allowedSpecialChars: String?,
    val maxAttempts: Int,
    val lockoutDuration: Long,
) {
    /**
     * Validates a PIN against this configuration.
     */
    fun validatePin(pin: String): Boolean {
        if (pin.length < minLength || pin.length > maxLength) return false
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
        val hasSpecial =
            allowedSpecialChars?.let { chars ->
                pin.any { it in chars }
            } ?: false

        return hasLetter && hasDigit && (hasSpecial || true)
    }
}

/**
 * Enumeration of verification methods.
 */
enum class VerificationMethod {
    NONE,
    BIOMETRIC,
    PIN,
    DEVICE_LOCK,
    BIOMETRIC_AND_PIN,
}

/**
 * Enumeration of biometric types.
 */
enum class BiometricType(
    val level: Int,
) {
    NONE(0),
    FINGERPRINT(1),
    FACE(2),
    IRIS(3),
    VOICE(4),
    ;

    companion object {
        fun fromString(type: String): BiometricType =
            when (type.lowercase()) {
                "fingerprint" -> FINGERPRINT
                "face" -> FACE
                "iris" -> IRIS
                "voice" -> VOICE
                else -> NONE
            }
    }
}

/**
 * Enumeration of biometric strength levels.
 */
enum class BiometricStrength(
    val level: Int,
) {
    WEAK(1),
    MEDIUM(2),
    STRONG(3),
    VERY_STRONG(4),
}

/**
 * Enumeration of user verification requirements.
 */
enum class UserVerificationRequirement {
    NOT_REQUIRED,
    PREFERRED,
    REQUIRED,
    DISCOURAGED,
}

/**
 * Enumeration of verification context.
 */
enum class VerificationContext {
    CREDENTIAL_CREATION,
    AUTHENTICATION,
    CREDENTIAL_DELETION,
    CREDENTIAL_UPDATE,
}

/**
 * Enumeration of verification states.
 */
enum class VerificationState {
    IDLE,
    BIOMETRIC_PENDING,
    PIN_PENDING,
    DEVICE_LOCK_PENDING,
    COMBINED_PENDING,
    ERROR,
    CANCELLED,
}
