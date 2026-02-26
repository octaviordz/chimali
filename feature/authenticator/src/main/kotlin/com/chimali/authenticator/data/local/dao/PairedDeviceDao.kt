package com.chimali.authenticator.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.chimali.authenticator.data.local.entity.PairedDeviceEntity

@Dao
interface PairedDeviceDao {
    
    @Query("SELECT * FROM paired_devices ORDER BY last_connected DESC")
    fun getAllDevices(): Flow<List<PairedDeviceEntity>>
    
    @Query("SELECT * FROM paired_devices WHERE device_id = :deviceId")
    suspend fun getDeviceById(deviceId: String): PairedDeviceEntity?
    
    @Query("SELECT * FROM paired_devices WHERE device_name LIKE :name || '%'")
    suspend fun getDevicesByName(name: String): List<PairedDeviceEntity>
    
    @Query("SELECT * FROM paired_devices WHERE platform = :platform")
    suspend fun getDevicesByPlatform(platform: String): List<PairedDeviceEntity>
    
    @Query("SELECT * FROM paired_devices WHERE is_trusted = 1")
    fun getTrustedDevices(): Flow<List<PairedDeviceEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PairedDeviceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<PairedDeviceEntity>)
    
    @Update
    suspend fun updateDevice(device: PairedDeviceEntity)
    
    @Delete
    suspend fun deleteDevice(device: PairedDeviceEntity)
    
    @Query("DELETE FROM paired_devices WHERE device_id = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)
    
    @Query("DELETE FROM paired_devices")
    suspend fun deleteAllDevices()
    
    @Query("UPDATE paired_devices SET last_connected = :timestamp WHERE device_id = :deviceId")
    suspend fun updateLastConnected(deviceId: String, timestamp: Long)
    
    @Query("UPDATE paired_devices SET is_trusted = :isTrusted WHERE device_id = :deviceId")
    suspend fun updateTrustStatus(deviceId: String, isTrusted: Boolean)
    
    @Query("SELECT COUNT(*) FROM paired_devices")
    suspend fun getDeviceCount(): Int
}
