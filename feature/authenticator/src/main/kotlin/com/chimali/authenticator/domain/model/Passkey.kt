package com.chimali.authenticator.domain.model

import java.util.UUID

data class Passkey(
    val credentialId: String = UUID.randomUUID().toString(),
    val relyingPartyId: String,
    val userId: String,
    val userName: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val keyMetadata: Map<String, Any> = emptyMap(),
    val isUserVerified: Boolean = false
)
