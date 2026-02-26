package com.chimali.authenticator.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.chimali.authenticator.data.local.entity.BluetoothHidConnectionEntity

@Dao
interface BluetoothHidConnectionDao {
    
    @Query("SELECT * FROM bluetooth_hid_connections ORDER BY established_at DESC")
    fun getAllConnections(): Flow<List<BluetoothHidConnectionEntity>>
    
    @Query("SELECT * FROM bluetooth_hid_connections WHERE connection_id = :connectionId")
    suspend fun getConnectionById(connectionId: String): BluetoothHidConnectionEntity?
    
    @Query("SELECT * FROM bluetooth_hid_connections WHERE device_id = :deviceId")
    fun getConnectionsByDevice(deviceId: String): Flow<List<BluetoothHidConnectionEntity>>
    
    @Query("SELECT * FROM bluetooth_hid_connections WHERE connection_state = :state")
    fun getConnectionsByState(state: String): Flow<List<BluetoothHidConnectionEntity>>
    
    @Query("SELECT * FROM bluetooth_hid_connections WHERE device_id = :deviceId AND connection_state = :state")
    suspend fun getActiveConnectionForDevice(deviceId: String, state: String): BluetoothHidConnectionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: BluetoothHidConnectionEntity)
    
    @Update
    suspend fun updateConnection(connection: BluetoothHidConnectionEntity)
    
    @Delete
    suspend fun deleteConnection(connection: BluetoothHidConnectionEntity)
    
    @Query("DELETE FROM bluetooth_hid_connections WHERE connection_id = :connectionId")
    suspend fun deleteConnectionById(connectionId: String)
    
    @Query("DELETE FROM bluetooth_hid_connections WHERE device_id = :deviceId")
    suspend fun deleteConnectionsByDevice(deviceId: String)
    
    @Query("UPDATE bluetooth_hid_connections SET connection_state = :state WHERE connection_id = :connectionId")
    suspend fun updateConnectionState(connectionId: String, state: String)
    
    @Query("SELECT COUNT(*) FROM bluetooth_hid_connections WHERE connection_state = 'CONNECTED'")
    suspend fun getActiveConnectionCount(): Int
}
