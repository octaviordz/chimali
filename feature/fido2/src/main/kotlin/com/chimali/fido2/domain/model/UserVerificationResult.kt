package com.chimali.fido2.domain.model

data class UserVerificationResult(
    val success: Boolean,
    val verificationMethod: VerificationMethod,
    val timestamp: Long = System.currentTimeMillis()
)

enum class VerificationMethod {
    BIOMETRIC,
    PIN,
    DEVICE_LOCK
}
