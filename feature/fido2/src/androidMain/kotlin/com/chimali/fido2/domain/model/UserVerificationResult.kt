package com.chimali.fido2.domain.model

data class UserVerificationResult(
    val success: Boolean,
    val verificationMethod: VerificationMethod,
    val timestamp: kotlinx.datetime.Instant =
        kotlinx.datetime.Instant.fromEpochMilliseconds(
            java.lang.System.currentTimeMillis(),
        ),
)

enum class VerificationMethod {
    BIOMETRIC,
    PIN,
    DEVICE_LOCK,
}
