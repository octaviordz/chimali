package com.chimali.authenticator.data.repository

import android.content.Context
import com.chimali.authenticator.domain.model.Passkey
import com.chimali.authenticator.domain.model.AuthenticationSession
import com.chimali.authenticator.domain.model.SessionType
import com.chimali.authenticator.domain.model.SessionState
import com.chimali.authenticator.domain.repository.Fido2Repository
import com.chimali.security.keystore.KeyStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Fido2RepositoryImpl @Inject constructor(
    private val context: Context,
    private val keyStoreManager: KeyStoreManager
) : Fido2Repository {
    
    private val _passkeys = MutableStateFlow<List<Passkey>>(emptyList())
    private val _sessions = MutableStateFlow<List<AuthenticationSession>>(emptyList())
    
    override fun getAllPasskeys(): Flow<List<Passkey>> = _passkeys.asStateFlow()
    
    override suspend fun getPasskeyById(credentialId: String): Passkey? {
        return _passkeys.value.find { it.credentialId == credentialId }
    }
    
    override suspend fun getPasskeysByRelyingParty(relyingPartyId: String): List<Passkey> {
        return _passkeys.value.filter { it.relyingPartyId == relyingPartyId }
    }
    
    override suspend fun getPasskeysByUser(userId: String): List<Passkey> {
        return _passkeys.value.filter { it.userId == userId }
    }
    
    override suspend fun createPasskey(passkey: Passkey) {
        val currentPasskeys = _passkeys.value.toMutableList()
        currentPasskeys.add(passkey)
        _passkeys.value = currentPasskeys
    }
    
    override suspend fun updatePasskey(passkey: Passkey) {
        val currentPasskeys = _passkeys.value.toMutableList()
        val index = currentPasskeys.indexOfFirst { it.credentialId == passkey.credentialId }
        if (index >= 0) {
            currentPasskeys[index] = passkey
            _passkeys.value = currentPasskeys
        }
    }
    
    override suspend fun deletePasskey(credentialId: String) {
        val currentPasskeys = _passkeys.value.toMutableList()
        currentPasskeys.removeAll { it.credentialId == credentialId }
        _passkeys.value = currentPasskeys
    }
    
    override suspend fun updateLastUsed(credentialId: String) {
        val currentPasskeys = _passkeys.value.toMutableList()
        val index = currentPasskeys.indexOfFirst { it.credentialId == credentialId }
        if (index >= 0) {
            currentPasskeys[index] = currentPasskeys[index].copy(lastUsed = System.currentTimeMillis())
            _passkeys.value = currentPasskeys
        }
    }
    
    override suspend fun updateVerificationStatus(credentialId: String, isVerified: Boolean) {
        val currentPasskeys = _passkeys.value.toMutableList()
        val index = currentPasskeys.indexOfFirst { it.credentialId == credentialId }
        if (index >= 0) {
            currentPasskeys[index] = currentPasskeys[index].copy(isUserVerified = isVerified)
            _passkeys.value = currentPasskeys
        }
    }
    
    override fun getAllSessions(): Flow<List<AuthenticationSession>> = _sessions.asStateFlow()
    
    override suspend fun getSessionById(sessionId: String): AuthenticationSession? {
        return _sessions.value.find { it.sessionId == sessionId }
    }
    
    override fun getSessionsByDevice(deviceId: String): Flow<List<AuthenticationSession>> {
        return _sessions.asStateFlow().map { sessions ->
            sessions.filter { it.deviceId == deviceId }
        }
    }
    
    override fun getSessionsByCredential(credentialId: String): Flow<List<AuthenticationSession>> {
        return _sessions.asStateFlow().map { sessions ->
            sessions.filter { it.credentialId == credentialId }
        }
    }
    
    override suspend fun createSession(session: AuthenticationSession) {
        val currentSessions = _sessions.value.toMutableList()
        currentSessions.add(session)
        _sessions.value = currentSessions
    }
    
    override suspend fun updateSession(session: AuthenticationSession) {
        val currentSessions = _sessions.value.toMutableList()
        val index = currentSessions.indexOfFirst { it.sessionId == session.sessionId }
        if (index >= 0) {
            currentSessions[index] = session
            _sessions.value = currentSessions
        }
    }
    
    override suspend fun deleteSession(sessionId: String) {
        val currentSessions = _sessions.value.toMutableList()
        currentSessions.removeAll { it.sessionId == sessionId }
        _sessions.value = currentSessions
    }
    
    override suspend fun updateSessionState(sessionId: String, state: SessionState) {
        val currentSessions = _sessions.value.toMutableList()
        val index = currentSessions.indexOfFirst { it.sessionId == sessionId }
        if (index >= 0) {
            currentSessions[index] = currentSessions[index].copy(sessionState = state)
            _sessions.value = currentSessions
        }
    }
    
    override suspend fun cleanupExpiredSessions() {
        val currentTime = System.currentTimeMillis()
        val currentSessions = _sessions.value.toMutableList()
        currentSessions.removeAll { it.expiresAt < currentTime }
        _sessions.value = currentSessions
    }
    
    override suspend fun generateCredentialAttestation(relyingPartyId: String, userId: String, userName: String): String {
        // Generate a new credential attestation
        val credentialId = UUID.randomUUID().toString()
        
        // Create key pair for this credential
        val keyAlias = "credential_$credentialId"
        val (publicKey, privateKey) = keyStoreManager.generateKeyPair(keyAlias)
        
        // Create passkey
        val passkey = Passkey(
            credentialId = credentialId,
            relyingPartyId = relyingPartyId,
            userId = userId,
            userName = userName,
            displayName = userName
        )
        
        createPasskey(passkey)
        
        // Return attestation data (simplified)
        return credentialId
    }
    
    override suspend fun generateAssertion(relyingPartyId: String, challenge: ByteArray): String {
        // Generate assertion for authentication
        val passkeys = getPasskeysByRelyingParty(relyingPartyId)
        if (passkeys.isEmpty()) {
            throw Exception("No passkeys found for relying party: $relyingPartyId")
        }
        
        val passkey = passkeys.first()
        val keyAlias = "credential_${passkey.credentialId}"
        
        // Sign the challenge
        val signature = keyStoreManager.signData(keyAlias, challenge)
        
        // Update last used
        updateLastUsed(passkey.credentialId)
        
        // Return assertion data (simplified)
        return java.util.Base64.getEncoder().encodeToString(signature)
    }
    
    override suspend fun verifySignature(credentialId: String, data: ByteArray, signature: ByteArray): Boolean {
        val keyAlias = "credential_$credentialId"
        return keyStoreManager.verifySignature(keyAlias, data, signature)
    }
}
