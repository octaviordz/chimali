package com.chimali.authenticator.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.chimali.authenticator.data.local.entity.PasskeyEntity

@Dao
interface PasskeyDao {
    
    @Query("SELECT * FROM passkeys ORDER BY last_used DESC")
    fun getAllPasskeys(): Flow<List<PasskeyEntity>>
    
    @Query("SELECT * FROM passkeys WHERE credential_id = :credentialId")
    suspend fun getPasskeyById(credentialId: String): PasskeyEntity?
    
    @Query("SELECT * FROM passkeys WHERE relying_party_id = :relyingPartyId")
    suspend fun getPasskeysByRelyingParty(relyingPartyId: String): List<PasskeyEntity>
    
    @Query("SELECT * FROM passkeys WHERE user_id = :userId")
    suspend fun getPasskeysByUser(userId: String): List<PasskeyEntity>
    
    @Query("SELECT * FROM passkeys WHERE relying_party_id = :relyingPartyId AND user_id = :userId")
    suspend fun getPasskeysForUserAndRelyingParty(relyingPartyId: String, userId: String): List<PasskeyEntity>
    
    @Query("SELECT * FROM passkeys WHERE is_user_verified = 1")
    fun getVerifiedPasskeys(): Flow<List<PasskeyEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasskey(passkey: PasskeyEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasskeys(passkeys: List<PasskeyEntity>)
    
    @Update
    suspend fun updatePasskey(passkey: PasskeyEntity)
    
    @Delete
    suspend fun deletePasskey(passkey: PasskeyEntity)
    
    @Query("DELETE FROM passkeys WHERE credential_id = :credentialId")
    suspend fun deletePasskeyById(credentialId: String)
    
    @Query("DELETE FROM passkeys WHERE relying_party_id = :relyingPartyId")
    suspend fun deletePasskeysByRelyingParty(relyingPartyId: String)
    
    @Query("UPDATE passkeys SET last_used = :timestamp WHERE credential_id = :credentialId")
    suspend fun updateLastUsed(credentialId: String, timestamp: Long)
    
    @Query("UPDATE passkeys SET is_user_verified = :isVerified WHERE credential_id = :credentialId")
    suspend fun updateVerificationStatus(credentialId: String, isVerified: Boolean)
    
    @Query("SELECT COUNT(*) FROM passkeys")
    suspend fun getPasskeyCount(): Int
}
