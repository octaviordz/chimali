package com.chimali.fido2.domain.service.impl

import androidx.biometric.BiometricManager
import com.chimali.fido2.domain.model.UserConsentRecord
import com.chimali.fido2.domain.service.BiometricEnrollmentStatus
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.BiometricVerificationResult
import com.chimali.fido2.domain.service.CombinedVerificationResult
import com.chimali.fido2.domain.service.DeviceLockVerificationResult
import com.chimali.fido2.domain.service.PinConfiguration
import com.chimali.fido2.domain.service.PinVerificationResult
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.domain.service.VerificationState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserVerificationServiceImpl @Inject constructor(
    private val biometricManager: BiometricManager
) : UserVerificationService {

    override suspend fun getUserVerificationAvailability(): UserVerificationAvailability {
        val biometricAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
        val pinAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        return UserVerificationAvailability(
            biometricAvailable = biometricAvailable,
            pinAvailable = pinAvailable,
            deviceLockAvailable = pinAvailable,
            supportedBiometricTypes = if (biometricAvailable) listOf(BiometricType.FINGERPRINT) else emptyList(),
            maxPinLength = 16,
            minPinLength = 4,
            biometricStrength = BiometricStrength.STRONG
        )
    }


    override suspend fun isBiometricAvailable(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun isPinAvailable(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun getBiometricEnrollmentStatus(): BiometricEnrollmentStatus {
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
        return BiometricEnrollmentStatus(
            isEnrolled = canAuth,
            enrolledTypes = if (canAuth) listOf(BiometricType.FINGERPRINT) else emptyList(),
            enrollmentStrength = BiometricStrength.STRONG,
            lastUpdated = Instant.now()
        )
    }

    override suspend fun getPinConfiguration(): PinConfiguration {
        return PinConfiguration(
            minLength = 4,
            maxLength = 16,
            requireComplexity = false,
            allowedSpecialChars = null,
            maxAttempts = 3,
            lockoutDuration = 30_000L
        )
    }

    override suspend fun recordUserConsent(consent: UserConsentRecord): Result<Unit> {
        // TODO: Persist consent record
        return Result.success(Unit)
    }

    override suspend fun getRecentConsentRecords(
        rpId: String?,
        limit: Int
    ): Flow<UserConsentRecord> {
        // TODO: Return persisted records
        return flowOf()
    }

    override suspend fun isUserVerificationRequired(
        rpId: String,
        operationType: String,
        context: VerificationContext?
    ): UserVerificationRequirement {
        return UserVerificationRequirement.PREFERRED
    }


}
