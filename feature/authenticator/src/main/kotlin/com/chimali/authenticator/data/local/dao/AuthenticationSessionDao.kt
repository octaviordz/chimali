package com.chimali.authenticator.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.chimali.authenticator.data.local.entity.AuthenticationSessionEntity

@Dao
interface AuthenticationSessionDao {
    
    @Query("SELECT * FROM authentication_sessions ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<AuthenticationSessionEntity>>
    
    @Query("SELECT * FROM authentication_sessions WHERE session_id = :sessionId")
    suspend fun getSessionById(sessionId: String): AuthenticationSessionEntity?
    
    @Query("SELECT * FROM authentication_sessions WHERE device_id = :deviceId")
    fun getSessionsByDevice(deviceId: String): Flow<List<AuthenticationSessionEntity>>
    
    @Query("SELECT * FROM authentication_sessions WHERE credential_id = :credentialId")
    fun getSessionsByCredential(credentialId: String): Flow<List<AuthenticationSessionEntity>>
    
    @Query("SELECT * FROM authentication_sessions WHERE session_type = :sessionType")
    fun getSessionsByType(sessionType: String): Flow<List<AuthenticationSessionEntity>>
    
    @Query("SELECT * FROM authentication_sessions WHERE session_state = :sessionState")
    fun getSessionsByState(sessionState: String): Flow<List<AuthenticationSessionEntity>>
    
    @Query("SELECT * FROM authentication_sessions WHERE expires_at < :currentTime")
    suspend fun getExpiredSessions(currentTime: Long): List<AuthenticationSessionEntity>
    
    @Query("SELECT * FROM authentication_sessions WHERE session_state = 'PENDING' AND expires_at > :currentTime")
    suspend fun getActivePendingSessions(currentTime: Long): List<AuthenticationSessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AuthenticationSessionEntity)
    
    @Update
    suspend fun updateSession(session: AuthenticationSessionEntity)
    
    @Delete
    suspend fun deleteSession(session: AuthenticationSessionEntity)
    
    @Query("DELETE FROM authentication_sessions WHERE session_id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
    
    @Query("DELETE FROM authentication_sessions WHERE expires_at < :currentTime")
    suspend fun deleteExpiredSessions(currentTime: Long)
    
    @Query("UPDATE authentication_sessions SET session_state = :state WHERE session_id = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: String)
    
    @Query("UPDATE authentication_sessions SET response_payload = :payload WHERE session_id = :sessionId")
    suspend fun updateResponsePayload(sessionId: String, payload: String)
    
    @Query("SELECT COUNT(*) FROM authentication_sessions WHERE session_state = 'ACTIVE'")
    suspend fun getActiveSessionCount(): Int
}
