package com.chimali.authenticator.domain.repository

import com.chimali.authenticator.domain.model.Passkey
import com.chimali.authenticator.domain.model.AuthenticationSession
import kotlinx.coroutines.flow.Flow

interface Fido2Repository {
    // Passkey operations
    fun getAllPasskeys(): Flow<List<Passkey>>
    suspend fun getPasskeyById(credentialId: String): Passkey?
    suspend fun getPasskeysByRelyingParty(relyingPartyId: String): List<Passkey>
    suspend fun getPasskeysByUser(userId: String): List<Passkey>
    suspend fun createPasskey(passkey: Passkey)
    suspend fun updatePasskey(passkey: Passkey)
    suspend fun deletePasskey(credentialId: String)
    suspend fun updateLastUsed(credentialId: String)
    suspend fun updateVerificationStatus(credentialId: String, isVerified: Boolean)
    
    // Authentication session operations
    fun getAllSessions(): Flow<List<AuthenticationSession>>
    suspend fun getSessionById(sessionId: String): AuthenticationSession?
    fun getSessionsByDevice(deviceId: String): Flow<List<AuthenticationSession>>
    fun getSessionsByCredential(credentialId: String): Flow<List<AuthenticationSession>>
    suspend fun createSession(session: AuthenticationSession)
    suspend fun updateSession(session: AuthenticationSession)
    suspend fun deleteSession(sessionId: String)
    suspend fun updateSessionState(sessionId: String, state: AuthenticationSession.SessionState)
    suspend fun cleanupExpiredSessions()
    
    // FIDO2 protocol operations
    suspend fun generateCredentialAttestation(relyingPartyId: String, userId: String, userName: String): String
    suspend fun generateAssertion(relyingPartyId: String, challenge: ByteArray): String
    suspend fun verifySignature(credentialId: String, data: ByteArray, signature: ByteArray): Boolean
}
