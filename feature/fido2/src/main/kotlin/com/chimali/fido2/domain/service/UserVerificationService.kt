package com.chimali.fido2.domain.service

import com.chimali.fido2.domain.model.UserVerificationResult

interface UserVerificationService {
    suspend fun verifyUser(prompt: String): Result<UserVerificationResult>
    suspend fun isBiometricAvailable(): Boolean
    suspend fun isDeviceSecure(): Boolean
}
