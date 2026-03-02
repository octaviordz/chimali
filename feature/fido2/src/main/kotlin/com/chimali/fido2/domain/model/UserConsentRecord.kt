package com.chimali.fido2.domain.model

data class UserConsentRecord(
    val id: Long = 0,
    val operationType: String,
    val rpId: String,
    val credentialId: String? = null,
    val timestamp: Long,
    val biometricUsed: Boolean = false,
    val pinUsed: Boolean = false
)
