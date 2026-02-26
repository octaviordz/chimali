package com.chimali.authenticator.domain.model

import java.util.UUID

data class AuthenticationSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val credentialId: String,
    val sessionType: SessionType,
    val sessionState: SessionState = SessionState.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 300000, // 5 minutes
    val requestPayload: Map<String, Any> = emptyMap(),
    val responsePayload: Map<String, Any> = emptyMap()
)

enum class SessionType {
    REGISTRATION,
    AUTHENTICATION
}

enum class SessionState {
    PENDING,
    ACTIVE,
    COMPLETED,
    FAILED,
    EXPIRED
}
