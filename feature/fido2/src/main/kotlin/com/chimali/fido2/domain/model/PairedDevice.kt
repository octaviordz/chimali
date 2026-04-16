package com.chimali.fido2.domain.model

/**
 * Represents a Bluetooth host that has previously connected and successfully completed
 * a FIDO2 operation (MakeCredential or GetAssertion) with this authenticator.
 * This satisfies the "Paired Devices" tracking requirement.
 */
data class PairedDevice(
    val macAddress: String,
    val name: String?,
    val deviceClass: Int?,
    val alias: String? = null,
    val lastUsedAt: Long,
    val createdAt: Long
)
