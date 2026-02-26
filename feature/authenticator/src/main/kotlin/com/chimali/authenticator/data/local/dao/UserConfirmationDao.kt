package com.chimali.authenticator.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.chimali.authenticator.data.local.entity.UserConfirmationEntity

@Dao
interface UserConfirmationDao {
    
    @Query("SELECT * FROM user_confirmations ORDER BY requested_at DESC")
    fun getAllConfirmations(): Flow<List<UserConfirmationEntity>>
    
    @Query("SELECT * FROM user_confirmations WHERE confirmation_id = :confirmationId")
    suspend fun getConfirmationById(confirmationId: String): UserConfirmationEntity?
    
    @Query("SELECT * FROM user_confirmations WHERE session_id = :sessionId")
    fun getConfirmationsBySession(sessionId: String): Flow<List<UserConfirmationEntity>>
    
    @Query("SELECT * FROM user_confirmations WHERE confirmation_type = :confirmationType")
    fun getConfirmationsByType(confirmationType: String): Flow<List<UserConfirmationEntity>>
    
    @Query("SELECT * FROM user_confirmations WHERE authentication_method = :method")
    fun getConfirmationsByMethod(method: String): Flow<List<UserConfirmationEntity>>
    
    @Query("SELECT * FROM user_confirmations WHERE is_approved = 1")
    fun getApprovedConfirmations(): Flow<List<UserConfirmationEntity>>
    
    @Query("SELECT * FROM user_confirmations WHERE is_approved = 0")
    fun getDeniedConfirmations(): Flow<List<UserConfirmationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfirmation(confirmation: UserConfirmationEntity)
    
    @Update
    suspend fun updateConfirmation(confirmation: UserConfirmationEntity)
    
    @Delete
    suspend fun deleteConfirmation(confirmation: UserConfirmationEntity)
    
    @Query("DELETE FROM user_confirmations WHERE confirmation_id = :confirmationId")
    suspend fun deleteConfirmationById(confirmationId: String)
    
    @Query("DELETE FROM user_confirmations WHERE session_id = :sessionId")
    suspend fun deleteConfirmationsBySession(sessionId: String)
    
    @Query("UPDATE user_confirmations SET is_approved = :approved, responded_at = :timestamp, denial_reason = :reason WHERE confirmation_id = :confirmationId")
    suspend fun updateConfirmationResult(confirmationId: String, approved: Boolean, timestamp: Long, reason: String?)
    
    @Query("SELECT COUNT(*) FROM user_confirmations WHERE is_approved = 1")
    suspend fun getApprovedConfirmationCount(): Int
    
    @Query("SELECT COUNT(*) FROM user_confirmations WHERE is_approved = 0")
    suspend fun getDeniedConfirmationCount(): Int
}
