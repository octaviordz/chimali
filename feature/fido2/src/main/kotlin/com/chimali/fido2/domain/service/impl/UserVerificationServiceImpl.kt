package com.chimali.fido2.domain.service.impl

import androidx.biometric.BiometricManager
import com.chimali.fido2.domain.model.UserVerificationResult
import com.chimali.fido2.domain.model.VerificationMethod
import com.chimali.fido2.domain.service.UserVerificationService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserVerificationServiceImpl @Inject constructor(
    private val biometricManager: BiometricManager
) : UserVerificationService {

    override suspend fun verifyUser(prompt: String): Result<UserVerificationResult> {
        // TODO: Implement user verification logic
        return Result.success(
            UserVerificationResult(
                success = true,
                verificationMethod = VerificationMethod.DEVICE_LOCK
            )
        )
    }

    override suspend fun isBiometricAvailable(): Boolean {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun isDeviceSecure(): Boolean {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
