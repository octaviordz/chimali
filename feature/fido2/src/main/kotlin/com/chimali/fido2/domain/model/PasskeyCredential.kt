package com.chimali.fido2.domain.model

data class PasskeyCredential(
    val id: String,
    val rpId: String,
    val rpName: String,
    val userId: String,
    val userName: String,
    val userDisplayName: String,
    val publicKey: String,
    val privateKeyAlias: String,
    val signCount: Long = 0,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val aaguid: String,
    val credentialId: String
)
